/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.data.upload

import com.datadog.android.api.context.DatadogContext
import com.datadog.android.api.storage.RawBatchEvent
import com.datadog.android.core.InternalSdkCore
import com.datadog.android.core.UploadWorker
import com.datadog.android.core.configuration.UploadSchedulerStrategy
import com.datadog.android.core.internal.ContextProvider
import com.datadog.android.core.internal.SdkFeature
import com.datadog.android.core.internal.metrics.RemovalReason
import com.datadog.android.core.internal.net.info.NetworkInfoProvider
import com.datadog.android.core.internal.persistence.BatchData
import com.datadog.android.core.internal.persistence.BatchId
import com.datadog.android.core.internal.persistence.Storage
import com.datadog.android.core.internal.persistence.file.FileMover
import com.datadog.android.core.internal.persistence.file.FileOrchestrator
import com.datadog.android.core.internal.persistence.file.FileReader
import com.datadog.android.core.internal.persistence.file.batch.BatchFileReader
import com.datadog.android.core.internal.system.SystemInfoProvider
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.File
import java.io.IOException
import java.util.LinkedList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

@Tag("FormalMethods")
internal class FormalMethodsUploadDiagnosticsTest {

    @Test
    fun `known counterexample - retryable WorkManager batch has no retry task owner`() {
        val context = mock<DatadogContext>()
        val sdkCore = mock<InternalSdkCore> {
            on { getDatadogContext() } doReturn context
        }
        val storage = mock<Storage>()
        val uploader = mock<DataUploader>()
        val feature = mock<SdkFeature>()
        val batchId = BatchId("formal-methods-retry")
        val batch = listOf(RawBatchEvent("payload".toByteArray()))
        val retryableFailure = UploadStatus.NetworkError(IOException("connection reset"))

        whenever(feature.storage) doReturn storage
        whenever(feature.uploader) doReturn uploader
        whenever(storage.readNextBatch()) doReturn BatchData(batchId, batch, null)
        whenever(uploader.upload(context, batch, null, batchId)) doReturn retryableFailure

        val taskQueue = LinkedList<UploadWorker.UploadNextBatchTask>()
        UploadWorker.UploadNextBatchTask(taskQueue, sdkCore, feature).run()

        verify(storage).confirmBatchRead(
            batchId,
            RemovalReason.IntakeCode(retryableFailure.code),
            deleteBatch = false
        )
        assertThat(taskQueue)
            .describedAs("the retained retryable batch should still have a task owner in this work run")
            .isEmpty()
    }

    @Test
    fun `known counterexample - crash flusher deletes a retryable failed batch`() {
        val context = mock<DatadogContext>()
        val contextProvider = mock<ContextProvider> {
            on { getContext(emptySet()) } doReturn context
        }
        val file = mock<File>()
        val batch = listOf(RawBatchEvent("payload".toByteArray()))
        val orchestrator = mock<FileOrchestrator> {
            on { getFlushableFiles() } doReturn listOf(file)
            on { getMetadataFile(file) } doReturn null
        }
        val reader = mock<BatchFileReader> {
            on { readData(org.mockito.kotlin.eq(file), org.mockito.kotlin.any()) } doReturn batch
        }
        val mover = mock<FileMover>()
        val uploader = mock<DataUploader> {
            on { upload(context, batch, null) } doReturn UploadStatus.NetworkError(IOException("offline"))
        }
        val flusher = DataFlusher(
            contextProvider = contextProvider,
            fileOrchestrator = orchestrator,
            fileReader = reader,
            metadataFileReader = mock<FileReader<ByteArray>>(),
            fileMover = mover,
            featureName = "formal-methods",
            internalLogger = mock()
        )

        flusher.flush(uploader)

        verify(mover).delete(file)
    }

    @Test
    fun `known counterexample - stopping upload scheduling returns while upload is active`() {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val active = AtomicBoolean(false)
        val executor = ScheduledThreadPoolExecutor(1)
        val scheduler = DataUploadScheduler(
            featureName = "formal-methods",
            storage = mock(),
            dataUploader = mock(),
            contextProvider = mock(),
            networkInfoProvider = mock<NetworkInfoProvider>(),
            systemInfoProvider = mock<SystemInfoProvider>(),
            uploadSchedulerStrategy = mock<UploadSchedulerStrategy>(),
            maxBatchesPerJob = 1,
            scheduledThreadPoolExecutor = executor,
            internalLogger = mock(),
            runnable = java.util.concurrent.Callable {
                active.set(true)
                started.countDown()
                release.await(5, TimeUnit.SECONDS)
                active.set(false)
                60_000L
            }
        )

        try {
            scheduler.startScheduling()
            assertThat(started.await(5, TimeUnit.SECONDS)).isTrue()

            scheduler.stopScheduling()

            assertThat(active.get())
                .describedAs("stopScheduling returned while its current upload still owned work")
                .isTrue()
        } finally {
            release.countDown()
            executor.shutdownNow()
            executor.awaitTermination(5, TimeUnit.SECONDS)
        }
    }
}
