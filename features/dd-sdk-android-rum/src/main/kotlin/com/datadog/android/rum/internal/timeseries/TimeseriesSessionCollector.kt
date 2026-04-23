/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.timeseries

import android.system.Os
import android.system.OsConstants
import android.util.Log
import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.api.storage.DataWriter
import com.datadog.android.api.storage.EventType
import com.datadog.android.core.internal.utils.scheduleSafe
import com.datadog.android.rum.RumSessionType
import com.datadog.android.rum.internal.toTimeseriesCpuSessionType
import com.datadog.android.rum.internal.toTimeseriesMemorySessionType
import com.datadog.android.rum.internal.thread.NoOpScheduledExecutorService
import com.datadog.android.rum.internal.vitals.VitalReader
import com.datadog.android.rum.model.RumTimeseriesCpuEvent
import com.datadog.android.rum.model.RumTimeseriesMemoryEvent
import com.google.gson.JsonObject
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

@Suppress("TooManyFunctions")
internal class TimeseriesSessionCollector(
    private val memoryReader: VitalReader,
    private val writer: DataWriter<Any>,
    private val sdkCore: FeatureSdkCore,
    private val totalRamBytes: Long,
    private val batchSize: Int = DEFAULT_BATCH_SIZE,
    private val samplingIntervalMs: Long = DEFAULT_SAMPLING_INTERVAL_MS,
    internal val cpuUsageProvider: (() -> Double?)? = null,
    internal val executorFactory: () -> ScheduledExecutorService = { Executors.newSingleThreadScheduledExecutor() },
    private val enableDeltaCompression: Boolean = false
) : TimeseriesCollecting {

    private var sessionId: String = ""
    private var applicationId: String = ""
    private var sessionType: RumSessionType = RumSessionType.USER

    @Volatile
    private var executor: ScheduledExecutorService = NoOpScheduledExecutorService()

    private val memoryBuffer = mutableListOf<RumTimeseriesMemoryEvent.Data>()
    private val cpuBuffer = mutableListOf<RumTimeseriesCpuEvent.Data>()

    private var prevCpuTicks: Long = 0L
    private val cpuClockTicks: Long = Os.sysconf(OsConstants._SC_CLK_TCK)

    override fun start(sessionId: String, applicationId: String, sessionType: RumSessionType) {
        this.sessionId = sessionId
        this.applicationId = applicationId
        this.sessionType = sessionType
        synchronized(this) {
            memoryBuffer.clear()
            cpuBuffer.clear()
        }
        prevCpuTicks = readCpuTicks()

        val newExecutor = executorFactory()
        executor = newExecutor
        newExecutor.scheduleSafe(
            OPERATION_NAME,
            samplingIntervalMs,
            TimeUnit.MILLISECONDS,
            sdkCore.internalLogger,
            ::sample
        )
    }

    override fun stop() {
        executor.shutdownNow()
        executor = NoOpScheduledExecutorService()
        synchronized(this) {
            flushMemoryBatch()
            flushCpuBatch()
            memoryBuffer.clear()
            cpuBuffer.clear()
        }
    }

    private fun sample() {
        val timestampNs = System.currentTimeMillis() * 1_000_000L
        collectMemorySample(timestampNs)
        collectCpuSample(timestampNs)
        synchronized(this) {
            if (memoryBuffer.size >= batchSize) {
                flushMemoryBatch()
                memoryBuffer.clear()
            }
            if (cpuBuffer.size >= batchSize) {
                flushCpuBatch()
                cpuBuffer.clear()
            }
        }
        executor.scheduleSafe(
            OPERATION_NAME,
            samplingIntervalMs,
            TimeUnit.MILLISECONDS,
            sdkCore.internalLogger,
            ::sample
        )
    }

    private fun collectMemorySample(timestampNs: Long) {
        val memoryBytes = memoryReader.readVitalData() ?: return
        if (totalRamBytes <= 0L) return
        val memoryPercent = memoryBytes / totalRamBytes * PERCENT_FACTOR
        val dataPoint = RumTimeseriesMemoryEvent.DataPoint(memoryBytes, memoryPercent)
        val data = RumTimeseriesMemoryEvent.Data(timestampNs, dataPoint)
        synchronized(this) { memoryBuffer.add(data) }
    }

    private fun collectCpuSample(timestampNs: Long) {
        val cpuUsage = cpuUsageProvider?.invoke() ?: readCpuDeltaPercent()
        val dataPoint = RumTimeseriesCpuEvent.DataPoint(cpuUsage ?: return)
        val data = RumTimeseriesCpuEvent.Data(timestampNs, dataPoint)
        synchronized(this) { cpuBuffer.add(data) }
    }

    private fun flushMemoryBatch() {
        if (memoryBuffer.isEmpty()) return
        val startNs = memoryBuffer.first().timestamp
        val endNs = memoryBuffer.last().timestamp
        val data = memoryBuffer.toList()
        val currentSessionId = sessionId
        val currentApplicationId = applicationId
        val currentSessionType = sessionType

        sdkCore.getFeature(Feature.RUM_FEATURE_NAME)?.withWriteContext { datadogContext, writeScope ->
            val event = RumTimeseriesMemoryEvent(
                dd = RumTimeseriesMemoryEvent.Dd(),
                application = RumTimeseriesMemoryEvent.Application(id = currentApplicationId),
                session = RumTimeseriesMemoryEvent.Session(
                    id = currentSessionId,
                    type = currentSessionType.toTimeseriesMemorySessionType()
                ),
                source = RumTimeseriesMemoryEvent.Source.ANDROID,
                date = datadogContext.time.serverTimeNs / NANOS_PER_MS,
                service = datadogContext.service,
                version = datadogContext.version,
                timeseries = RumTimeseriesMemoryEvent.Timeseries(
                    id = UUID.randomUUID().toString(),
                    name = MEMORY_METRIC_NAME,
                    start = startNs,
                    end = endNs,
                    data = data
                )
            )
            if (enableDeltaCompression) {
                val deltaData = DeltaEncoder.encodeMemory(data) ?: return@withWriteContext
                val json = event.toJson() as JsonObject
                json.getAsJsonObject("timeseries").add("data", deltaData)
                writeScope { batchWriter ->
                    writer.write(batchWriter, json, EventType.DEFAULT)
                }
                val normalSize = event.toJson().toString().length
                val deltaSize = json.toString().length
                Log.d(
                    "Timeseries",
                    "[Timeseries] delta flush: signal=memory normal=${normalSize}B delta=${deltaSize}B ratio=${"%.1f".format(normalSize.toDouble() / deltaSize.toDouble())}x"
                )
            } else {
                writeScope { batchWriter ->
                    writer.write(batchWriter, event, EventType.DEFAULT)
                }
            }
        }
    }

    private fun flushCpuBatch() {
        if (cpuBuffer.isEmpty()) return
        val startNs = cpuBuffer.first().timestamp
        val endNs = cpuBuffer.last().timestamp
        val data = cpuBuffer.toList()
        val currentSessionId = sessionId
        val currentApplicationId = applicationId
        val currentSessionType = sessionType

        sdkCore.getFeature(Feature.RUM_FEATURE_NAME)?.withWriteContext { datadogContext, writeScope ->
            val event = RumTimeseriesCpuEvent(
                dd = RumTimeseriesCpuEvent.Dd(),
                application = RumTimeseriesCpuEvent.Application(id = currentApplicationId),
                session = RumTimeseriesCpuEvent.Session(
                    id = currentSessionId,
                    type = currentSessionType.toTimeseriesCpuSessionType()
                ),
                source = RumTimeseriesCpuEvent.Source.ANDROID,
                date = datadogContext.time.serverTimeNs / NANOS_PER_MS,
                service = datadogContext.service,
                version = datadogContext.version,
                timeseries = RumTimeseriesCpuEvent.Timeseries(
                    id = UUID.randomUUID().toString(),
                    name = CPU_METRIC_NAME,
                    start = startNs,
                    end = endNs,
                    data = data
                )
            )
            if (enableDeltaCompression) {
                val deltaData = DeltaEncoder.encodeCpu(data) ?: return@withWriteContext
                val json = event.toJson() as JsonObject
                json.getAsJsonObject("timeseries").add("data", deltaData)
                writeScope { batchWriter ->
                    writer.write(batchWriter, json, EventType.DEFAULT)
                }
                val normalSize = event.toJson().toString().length
                val deltaSize = json.toString().length
                Log.d(
                    "Timeseries",
                    "[Timeseries] delta flush: signal=cpu normal=${normalSize}B delta=${deltaSize}B ratio=${"%.1f".format(normalSize.toDouble() / deltaSize.toDouble())}x"
                )
            } else {
                writeScope { batchWriter ->
                    writer.write(batchWriter, event, EventType.DEFAULT)
                }
            }
        }
    }

    private fun readCpuTicks(): Long {
        return try {
            val tokens = STAT_FILE.readText().split(' ')
            val utime = tokens.getOrNull(UTIME_IDX)?.toLongOrNull() ?: 0L
            val stime = tokens.getOrNull(STIME_IDX)?.toLongOrNull() ?: 0L
            utime + stime
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            0L
        }
    }

    private fun readCpuDeltaPercent(): Double? {
        if (cpuClockTicks <= 0L) return null
        val newTicks = readCpuTicks()
        val delta = newTicks - prevCpuTicks
        prevCpuTicks = newTicks
        return delta.toDouble() / cpuClockTicks * PERCENT_FACTOR
    }

    companion object {
        internal const val DEFAULT_BATCH_SIZE = 30
        internal const val DEFAULT_SAMPLING_INTERVAL_MS = 1000L
        private const val NANOS_PER_MS = 1_000_000L
        internal const val MEMORY_METRIC_NAME = "memory"
        internal const val CPU_METRIC_NAME = "cpu"
        internal const val OPERATION_NAME = "Timeseries sampling"
        private const val PERCENT_FACTOR = 100.0
        private const val UTIME_IDX = 13
        private const val STIME_IDX = 14
        private val STAT_FILE = File("/proc/self/stat")
    }
}
