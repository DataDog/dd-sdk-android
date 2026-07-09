/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.profiling.internal

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.context.DatadogContext
import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.api.storage.EventType
import com.datadog.android.api.storage.RawBatchEvent
import com.datadog.android.core.internal.persistence.file.readBytesSafe
import com.datadog.android.core.metrics.MethodCallSamplingRate
import com.datadog.android.internal.profiling.ProfilerEvent
import com.datadog.android.internal.profiling.ProfilingRumContext
import com.datadog.android.internal.utils.formatIsoUtc
import com.datadog.android.profiling.internal.domain.ProfilingBatchMetadata
import com.datadog.android.profiling.internal.perfetto.PerfettoResult
import com.datadog.android.profiling.internal.telemetry.ProfilingTelemetry
import com.datadog.android.profiling.model.ProfileEvent
import com.datadog.android.profiling.model.RumMetadataEvent
import com.google.gson.JsonArray
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.math.abs

internal class ProfilingDataWriter(
    private val sdkCore: FeatureSdkCore
) : ProfilingWriter {
    override fun write(
        profilingResult: PerfettoResult,
        longTasks: List<ProfilerEvent.RumLongTaskEvent>,
        anrEvents: List<ProfilerEvent.RumAnrEvent>,
        vitalEvents: List<ProfilerEvent.RumVitalEvent>
    ) {
        val feature = sdkCore.getFeature(Feature.PROFILING_FEATURE_NAME)
        if (feature == null) {
            safeDelete(profilingResult.resultFilePath)
            return
        }
        feature.withWriteContext { context, writeScope ->
            writeScope { writer ->
                synchronized(this) {
                    buildRawBatchEvent(
                        context = context,
                        profilingResult = profilingResult,
                        longTaskEvents = longTasks,
                        anrEvents = anrEvents,
                        vitalEvents = vitalEvents
                    )?.let {
                        writer.write(
                            event = it,
                            batchMetadata = null,
                            eventType = EventType.DEFAULT
                        )
                    }
                    safeDelete(profilingResult.resultFilePath)
                }
            }
        }
    }

    private fun buildRawBatchEvent(
        context: DatadogContext,
        profilingResult: PerfettoResult,
        longTaskEvents: List<ProfilerEvent.RumLongTaskEvent>,
        anrEvents: List<ProfilerEvent.RumAnrEvent>,
        vitalEvents: List<ProfilerEvent.RumVitalEvent>
    ): RawBatchEvent? {
        val driftMs = context.time.serverTimeOffsetMs
        val dropReason = when {
            longTaskEvents.isEmpty() && anrEvents.isEmpty() && vitalEvents.isEmpty() -> DROP_REASON_NO_RUM_EVENTS
            profilingResult.startReason != ProfilingStartReason.APPLICATION_LAUNCH &&
                abs(driftMs) > MAX_CLOCK_DRIFT_MS -> DROP_REASON_CLOCK_DRIFT

            else -> null
        }
        if (dropReason != null) {
            logWriteResultMetric(
                dropped = true,
                dropReason = dropReason,
                driftMs = driftMs,
                startReason = profilingResult.startReason.value,
                longTaskEvents = longTaskEvents,
                anrEvents = anrEvents,
                vitalEvents = vitalEvents
            )
            return null
        }

        return assembleBatchEvent(
            context,
            profilingResult,
            driftMs,
            longTaskEvents,
            anrEvents,
            vitalEvents
        )
    }

    private fun assembleBatchEvent(
        context: DatadogContext,
        profilingResult: PerfettoResult,
        driftMs: Long,
        longTaskEvents: List<ProfilerEvent.RumLongTaskEvent>,
        anrEvents: List<ProfilerEvent.RumAnrEvent>,
        vitalEvents: List<ProfilerEvent.RumVitalEvent>
    ): RawBatchEvent? {
        val perfettoBytes = readProfilingData(profilingResult.resultFilePath)
        val firstRumContext =
            longTaskEvents.firstOrNull()?.rumContext
                ?: anrEvents.firstOrNull()?.rumContext
                ?: vitalEvents.firstOrNull()?.rumContext
        return when {
            perfettoBytes == null || perfettoBytes.isEmpty() -> {
                logWriteResultMetric(
                    dropped = true,
                    dropReason = DROP_REASON_PERFETTO_UNREADABLE,
                    driftMs = driftMs,
                    startReason = profilingResult.startReason.value,
                    longTaskEvents = longTaskEvents,
                    anrEvents = anrEvents,
                    vitalEvents = vitalEvents
                )
                null
            }

            firstRumContext == null -> {
                logWriteResultMetric(
                    dropped = true,
                    dropReason = DROP_REASON_NO_RUM_CONTEXT,
                    driftMs = driftMs,
                    startReason = profilingResult.startReason.value,
                    longTaskEvents = longTaskEvents,
                    anrEvents = anrEvents,
                    vitalEvents = vitalEvents
                )
                null
            }

            else -> {
                val profileEvent = createProfileEvent(
                    context = context,
                    rumContext = firstRumContext,
                    profilingResult = profilingResult,
                    longTaskEvents = longTaskEvents,
                    anrEvents = anrEvents,
                    vitalEvents = vitalEvents
                )
                val serializedEvent = profileEvent.toJson().toString().toByteArray(Charsets.UTF_8)
                val rumMobileEventsJson =
                    buildRumMobileEventsJson(longTaskEvents, anrEvents, vitalEvents)
                val metadata = ProfilingBatchMetadata(perfettoBytes, rumMobileEventsJson).toBytes()
                logWriteResultMetric(
                    dropped = false,
                    dropReason = null,
                    driftMs = driftMs,
                    startReason = profilingResult.startReason.value,
                    longTaskEvents = longTaskEvents,
                    anrEvents = anrEvents,
                    vitalEvents = vitalEvents
                )
                RawBatchEvent(data = serializedEvent, metadata = metadata)
            }
        }
    }

    private fun logWriteResultMetric(
        dropped: Boolean,
        dropReason: String?,
        driftMs: Long,
        startReason: String,
        longTaskEvents: List<ProfilerEvent.RumLongTaskEvent>,
        anrEvents: List<ProfilerEvent.RumAnrEvent>,
        vitalEvents: List<ProfilerEvent.RumVitalEvent>
    ) {
        sdkCore.internalLogger.logMetric(
            messageBuilder = { ProfilingTelemetry.TELEMETRY_MSG_PROFILING_SESSION },
            additionalProperties = mapOf(
                ProfilingTelemetry.KEY_METRIC_TYPE to METRIC_TYPE_PROFILING_WRITE,
                KEY_PROFILING_WRITE to mapOf(
                    KEY_DROPPED to dropped,
                    KEY_DROP_REASON to dropReason,
                    KEY_CLIENT_CLOCK_DRIFT to driftMs,
                    ProfilingTelemetry.KEY_START_REASON to startReason,
                    KEY_LONG_TASK_COUNT to longTaskEvents.size,
                    KEY_ANR_COUNT to anrEvents.size,
                    KEY_VITAL_COUNT to vitalEvents.size
                )
            ),
            samplingRate = MethodCallSamplingRate.ALL.rate
        )
    }

    private fun createProfileEvent(
        context: DatadogContext,
        rumContext: ProfilingRumContext,
        profilingResult: PerfettoResult,
        longTaskEvents: List<ProfilerEvent.RumLongTaskEvent>,
        anrEvents: List<ProfilerEvent.RumAnrEvent>,
        vitalEvents: List<ProfilerEvent.RumVitalEvent>
    ): ProfileEvent {
        val viewIds = mutableSetOf<String>()
        val viewNames = mutableSetOf<String>()
        val longTaskIds = mutableSetOf<String>()
        val anrIds = mutableSetOf<String>()
        val vitalIds = mutableSetOf<String>()
        val vitalNames = mutableSetOf<String>()
        for (event in longTaskEvents) {
            longTaskIds.add(event.id)
            event.rumContext.viewId?.let { viewIds.add(it) }
            event.rumContext.viewName?.let { viewNames.add(it) }
        }
        for (event in anrEvents) {
            anrIds.add(event.id)
            event.rumContext.viewId?.let { viewIds.add(it) }
            event.rumContext.viewName?.let { viewNames.add(it) }
        }
        for (event in vitalEvents) {
            vitalIds.add(event.id)
            event.name?.let {
                vitalNames.add(it)
            }
            event.rumContext.viewId?.let { viewIds.add(it) }
            event.rumContext.viewName?.let { viewNames.add(it) }
        }
        return ProfileEvent(
            start = formatIsoUtc(profilingResult.start),
            end = formatIsoUtc(profilingResult.end),
            attachments = listOf(
                PERFETTO_ATTACHMENT_NAME,
                RUM_MOBILE_EVENTS_ATTACHMENT_NAME
            ),
            family = ProfileEvent.Family.ANDROID,
            runtime = ProfileEvent.Family.ANDROID,
            version = VERSION_NUMBER,
            tagsProfiler = buildTags(context, profilingResult.startReason.value),
            application = ProfileEvent.Application(id = rumContext.applicationId),
            session = ProfileEvent.Session(id = rumContext.sessionId),
            longTask = ProfileEvent.LongTask(id = longTaskIds.toList()),
            error = ProfileEvent.Error(id = anrIds.toList()),
            vital = ProfileEvent.Vital(id = vitalIds.toList(), label = vitalNames.toList()),
            view = ProfileEvent.View(
                id = viewIds.toList(),
                name = viewNames.toList()
            )
        )
    }

    private fun buildRumMobileEventsJson(
        longTasks: List<ProfilerEvent.RumLongTaskEvent>,
        anrEvents: List<ProfilerEvent.RumAnrEvent>,
        vitalEvents: List<ProfilerEvent.RumVitalEvent>
    ): ByteArray {
        val rumMobileEvents = mutableListOf<RumMetadataEvent>()
        anrEvents.forEach {
            rumMobileEvents += RumMetadataEvent(
                id = it.id,
                type = RumMetadataEvent.Type.ERROR,
                startNs = TimeUnit.MILLISECONDS.toNanos(it.startMs),
                durationNs = it.durationNs
            )
        }
        longTasks.forEach {
            rumMobileEvents += RumMetadataEvent(
                id = it.id,
                type = RumMetadataEvent.Type.LONG_TASK,
                startNs = TimeUnit.MILLISECONDS.toNanos(it.startMs),
                durationNs = it.durationNs
            )
        }
        vitalEvents.forEach {
            rumMobileEvents += RumMetadataEvent(
                id = it.id,
                name = it.name,
                type = RumMetadataEvent.Type.VITAL,
                startNs = TimeUnit.MILLISECONDS.toNanos(it.startMs),
                durationNs = it.durationNs
            )
        }
        return JsonArray(rumMobileEvents.size).apply {
            rumMobileEvents.forEach {
                add(it.toJson())
            }
        }.toString().toByteArray(Charsets.UTF_8)
    }

    private fun buildTags(context: DatadogContext, operation: String): String = buildString {
        append("$TAG_KEY_SERVICE:${context.service}")
        append(",")
        append("$TAG_KEY_ENV:${context.env}")
        append(",")
        append("$TAG_KEY_VERSION:${context.version}")
        append(",")
        append("$TAG_KEY_SDK_VERSION:${context.sdkVersion}")
        append(",")
        append("$TAG_KEY_PROFILER_VERSION:${context.sdkVersion}")
        append(",")
        append("$TAG_KEY_RUNTIME_VERSION:${context.deviceInfo.osVersion}")
        append(",")
        append("$TAG_KEY_OPERATION:$operation")
        context.appBuildId?.let { buildId ->
            append(",")
            append("$TAG_KEY_BUILD_ID:$buildId")
        }
    }

    private fun readProfilingData(profilingPath: String): ByteArray? {
        @Suppress("UnsafeThirdPartyFunctionCall")
        // profilingPath is not null in kotlin, it can't throw NPE
        return File(profilingPath).readBytesSafe(internalLogger = sdkCore.internalLogger)
    }

    private fun safeDelete(path: String) {
        try {
            @Suppress("UnsafeThirdPartyFunctionCall")
            val deleted = File(path).delete()
            if (!deleted) {
                sdkCore.internalLogger.log(
                    InternalLogger.Level.WARN,
                    InternalLogger.Target.MAINTAINER,
                    { LOG_FILE_DELETE_FAILED.format(path) }
                )
            }
        } catch (@Suppress("TooGenericExceptionCaught") t: Throwable) {
            sdkCore.internalLogger.log(
                InternalLogger.Level.WARN,
                InternalLogger.Target.MAINTAINER,
                { LOG_FILE_DELETE_FAILED.format(path) },
                t
            )
        }
    }

    companion object {
        internal const val MAX_CLOCK_DRIFT_MS = 1000L
        private const val LOG_FILE_DELETE_FAILED = "Failed to delete Perfetto trace file: %s"

        internal const val METRIC_TYPE_PROFILING_WRITE = "profiling write"
        internal const val KEY_PROFILING_WRITE = "profiling_write"
        internal const val KEY_DROPPED = "dropped"
        internal const val KEY_DROP_REASON = "drop_reason"
        internal const val KEY_CLIENT_CLOCK_DRIFT = "client_clock_drift_ms"
        internal const val KEY_LONG_TASK_COUNT = "long_task_count"
        internal const val KEY_ANR_COUNT = "anr_count"
        internal const val KEY_VITAL_COUNT = "vital_count"
        internal const val DROP_REASON_NO_RUM_EVENTS = "no_rum_events"
        internal const val DROP_REASON_CLOCK_DRIFT = "clock_drift_exceeded"
        internal const val DROP_REASON_PERFETTO_UNREADABLE = "perfetto_unreadable"
        internal const val DROP_REASON_NO_RUM_CONTEXT = "no_rum_context"

        private const val TAG_KEY_SERVICE = "service"
        private const val TAG_KEY_VERSION = "version"
        private const val TAG_KEY_BUILD_ID = "build_id"
        private const val TAG_KEY_SDK_VERSION = "sdk_version"
        private const val TAG_KEY_PROFILER_VERSION = "profiler_version"
        private const val TAG_KEY_RUNTIME_VERSION = "runtime_version"
        private const val TAG_KEY_ENV = "env"
        private const val TAG_KEY_OPERATION = "operation"
        internal const val PERFETTO_ATTACHMENT_NAME = "perfetto.proto"
        internal const val RUM_MOBILE_EVENTS_ATTACHMENT_NAME = "rum-mobile-events.json"

        // Only `4` is supported by profiling Backend
        private const val VERSION_NUMBER = 4L
    }
}
