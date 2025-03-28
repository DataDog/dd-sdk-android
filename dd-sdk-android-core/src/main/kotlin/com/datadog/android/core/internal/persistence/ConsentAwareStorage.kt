/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.persistence

import androidx.annotation.AnyThread
import androidx.annotation.WorkerThread
import com.datadog.android.api.InternalLogger
import com.datadog.android.api.context.DatadogContext
import com.datadog.android.api.storage.EventBatchWriter
import com.datadog.android.core.internal.data.upload.DataOkHttpUploader.Companion.HTTP_ACCEPTED
import com.datadog.android.core.internal.metrics.MetricsDispatcher
import com.datadog.android.core.internal.metrics.RemovalReason
import com.datadog.android.core.internal.persistence.file.FileMover
import com.datadog.android.core.internal.persistence.file.FileOrchestrator
import com.datadog.android.core.internal.persistence.file.FilePersistenceConfig
import com.datadog.android.core.internal.persistence.file.FileReaderWriter
import com.datadog.android.core.internal.persistence.file.batch.BatchFileReaderWriter
import com.datadog.android.core.internal.persistence.file.existsSafe
import com.datadog.android.core.internal.persistence.file.lengthSafe
import com.datadog.android.core.internal.privacy.ConsentProvider
import com.datadog.android.core.internal.utils.submitSafe
import com.datadog.android.core.metrics.MethodCallSamplingRate
import com.datadog.android.core.metrics.TelemetryMetricType
import com.datadog.android.internal.profiler.BenchmarkSdkPerformance
import com.datadog.android.internal.profiler.GlobalBenchmark
import com.datadog.android.privacy.TrackingConsent
import java.io.File
import java.util.Locale
import java.util.concurrent.ExecutorService

internal class ConsentAwareStorage(
    private val executorService: ExecutorService,
    internal val grantedOrchestrator: FileOrchestrator,
    internal val pendingOrchestrator: FileOrchestrator,
    private val batchEventsReaderWriter: BatchFileReaderWriter,
    private val batchMetadataReaderWriter: FileReaderWriter,
    private val fileMover: FileMover,
    private val internalLogger: InternalLogger,
    internal val filePersistenceConfig: FilePersistenceConfig,
    private val metricsDispatcher: MetricsDispatcher,
    private val consentProvider: ConsentProvider,
    private val featureName: String,
    private val benchmarkSdkPerformance: BenchmarkSdkPerformance = GlobalBenchmark.getSdkPerformance()
) : Storage, BatchWriteEventListener {

    /**
     * Keeps track of files currently being read.
     */
    private val lockedBatches: MutableSet<Batch> = mutableSetOf()

    private val writeLock = Any()

    /** @inheritdoc */
    @WorkerThread
    override fun writeCurrentBatch(
        datadogContext: DatadogContext,
        forceNewBatch: Boolean,
        callback: (EventBatchWriter) -> Unit
    ) {
        val metric = internalLogger.startPerformanceMeasure(
            callerClass = ConsentAwareStorage::class.java.name,
            metric = TelemetryMetricType.MethodCalled,
            samplingRate = MethodCallSamplingRate.RARE.rate,
            operationName = "writeCurrentBatch[$featureName]"
        )
        executorService.submitSafe("Data write", internalLogger) {
            val orchestrator = resolveOrchestrator()
            if (orchestrator == null) {
                callback.invoke(NoOpEventBatchWriter())
                metric?.stopAndSend(false)
                return@submitSafe
            }
            synchronized(writeLock) {
                val batchFile = orchestrator.getWritableFile(forceNewBatch)
                val metadataFile = if (batchFile != null) {
                    orchestrator.getMetadataFile(batchFile)
                } else {
                    null
                }
                val writer = if (batchFile == null) {
                    NoOpEventBatchWriter()
                } else {
                    FileEventBatchWriter(
                        batchFile = batchFile,
                        metadataFile = metadataFile,
                        eventsWriter = batchEventsReaderWriter,
                        metadataReaderWriter = batchMetadataReaderWriter,
                        filePersistenceConfig = filePersistenceConfig,
                        batchWriteEventListener = this,
                        internalLogger = internalLogger
                    )
                }
                callback.invoke(writer)
                metric?.stopAndSend(writer !is NoOpEventBatchWriter)
            }
        }
    }

    /** @inheritdoc */
    @WorkerThread
    override fun readNextBatch(): BatchData? {
        val (batchFile, metaFile) = synchronized(lockedBatches) {
            val batchFile = grantedOrchestrator
                .getReadableFile(lockedBatches.map { it.file }.toSet()) ?: return null

            val metaFile = grantedOrchestrator.getMetadataFile(batchFile)
            lockedBatches.add(Batch(batchFile, metaFile))
            batchFile to metaFile
        }

        val batchId = BatchId.fromFile(batchFile)
        val batchMetadata = if (metaFile == null || !metaFile.existsSafe(internalLogger)) {
            null
        } else {
            batchMetadataReaderWriter.readData(metaFile)
        }
        val batchData = batchEventsReaderWriter.readData(batchFile)

        return BatchData(id = batchId, data = batchData, metadata = batchMetadata)
    }

    /** @inheritdoc */
    @WorkerThread
    override fun confirmBatchRead(
        batchId: BatchId,
        removalReason: RemovalReason,
        deleteBatch: Boolean
    ) {
        val batch = synchronized(lockedBatches) {
            lockedBatches.firstOrNull { batchId.matchesFile(it.file) }
        } ?: return

        if (deleteBatch) {
            deleteBatch(batch, removalReason)
        }
        synchronized(lockedBatches) {
            lockedBatches.remove(batch)
        }
    }

    /** @inheritdoc */
    @AnyThread
    override fun dropAll() {
        executorService.submitSafe("ConsentAwareStorage.dropAll", internalLogger) {
            synchronized(lockedBatches) {
                lockedBatches.forEach {
                    deleteBatch(it, RemovalReason.Flushed)
                }
                lockedBatches.clear()
            }
            arrayOf(pendingOrchestrator, grantedOrchestrator).forEach { orchestrator ->
                orchestrator.getAllFiles().forEach {
                    val metaFile = orchestrator.getMetadataFile(it)
                    deleteBatch(it, metaFile, RemovalReason.Flushed)
                }
            }
        }
    }

    override fun onWriteEvent(bytes: Long) {
        sendBenchmarkTelemetry(
            bytes,
            BENCHMARK_BYTES_WRITTEN
        )
    }

    private fun sendBenchmarkTelemetry(value: Long, metricName: String) {
        val tags = mapOf(
            TRACK_NAME to featureName
        )

        benchmarkSdkPerformance
            .getMeter(METER_NAME)
            .getCounter(metricName)
            .add(value, tags)
    }

    @WorkerThread
    private fun resolveOrchestrator(): FileOrchestrator? {
        val consent = consentProvider.getConsent()
        return when (consent) {
            TrackingConsent.GRANTED -> grantedOrchestrator
            TrackingConsent.PENDING -> pendingOrchestrator
            TrackingConsent.NOT_GRANTED -> null
        }
    }

    @WorkerThread
    private fun deleteBatch(batch: Batch, reason: RemovalReason) {
        deleteBatch(batch.file, batch.metaFile, reason)
    }

    @WorkerThread
    private fun deleteBatch(batchFile: File, metaFile: File?, reason: RemovalReason) {
        deleteBatchFile(batchFile, reason)
        if (metaFile?.existsSafe(internalLogger) == true) {
            deleteBatchMetadataFile(metaFile)
        }
    }

    @WorkerThread
    private fun deleteBatchFile(batchFile: File, reason: RemovalReason) {
        val fileSizeBeforeDeletion = batchFile.lengthSafe(internalLogger)

        val result = fileMover.delete(batchFile)
        if (result) {

            val numPendingFiles = grantedOrchestrator.decrementAndGetPendingFilesCount()
            metricsDispatcher.sendBatchDeletedMetric(batchFile, reason, numPendingFiles)

            if (reason == RemovalReason.IntakeCode(HTTP_ACCEPTED) && fileSizeBeforeDeletion > 0) {
                sendBenchmarkTelemetry(
                    fileSizeBeforeDeletion,
                    BENCHMARK_BYTES_DELETED
                )
            }
        } else {
            internalLogger.log(
                InternalLogger.Level.WARN,
                InternalLogger.Target.MAINTAINER,
                { WARNING_DELETE_FAILED.format(Locale.US, batchFile.path) }
            )
        }
    }

    @WorkerThread
    private fun deleteBatchMetadataFile(metadataFile: File) {
        val result = fileMover.delete(metadataFile)
        if (!result) {
            internalLogger.log(
                InternalLogger.Level.WARN,
                InternalLogger.Target.MAINTAINER,
                { WARNING_DELETE_FAILED.format(Locale.US, metadataFile.path) }
            )
        }
    }

    private data class Batch(val file: File, val metaFile: File?)

    companion object {
        internal const val WARNING_DELETE_FAILED = "Unable to delete file: %s"
        internal const val METER_NAME = "dd-sdk-android"
        internal const val TRACK_NAME = "track"
        internal const val BENCHMARK_BYTES_WRITTEN = "android.benchmark.bytes_written"
        internal const val BENCHMARK_BYTES_DELETED = "android.benchmark.bytes_deleted"
    }
}
