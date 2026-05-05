/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.profiling.internal

import com.datadog.android.api.context.DatadogContext
import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.api.storage.EventType
import com.datadog.android.api.storage.RawBatchEvent
import com.datadog.android.core.internal.persistence.file.readBytesSafe
import com.datadog.android.internal.profiling.ProfilerEvent
import com.datadog.android.internal.profiling.ProfilingRumContext
import com.datadog.android.internal.utils.formatIsoUtc
import com.datadog.android.profiling.internal.domain.ProfilingBatchMetadata
import com.datadog.android.profiling.internal.perfetto.PerfettoResult
import com.datadog.android.profiling.model.ProfileEvent
import com.datadog.android.profiling.model.RumMobileEvents
import java.io.File
import java.util.concurrent.TimeUnit

internal class ProfilingDataWriter(
    private val sdkCore: FeatureSdkCore
) : ProfilingWriter {
    override fun write(
        profilingResult: PerfettoResult,
        ttidEvent: ProfilerEvent.TTID?,
        longTasks: List<ProfilerEvent.RumLongTaskEvent>,
        anrEvents: List<ProfilerEvent.RumAnrEvent>
    ) {
        writeWithContext { context ->
            if (ttidEvent != null) {
                buildRawBatchEventTtid(
                    context = context,
                    profilingResult = profilingResult,
                    rumContext = ttidEvent.rumContext,
                    vitalId = ttidEvent.vitalId,
                    vitalName = ttidEvent.vitalName,
                    longTasks = longTasks,
                    anrEvents = anrEvents
                )
            } else {
                buildRawBatchEventContinuous(
                    context = context,
                    profilingResult = profilingResult,
                    longTaskEvents = longTasks,
                    anrEvents = anrEvents
                )
            }
        }
    }

    private fun writeWithContext(rawBatchEventBuilder: (DatadogContext) -> RawBatchEvent?) {
        sdkCore.getFeature(Feature.Companion.PROFILING_FEATURE_NAME)
            ?.withWriteContext { context, writeScope ->
                writeScope { writer ->
                    rawBatchEventBuilder(context)?.let {
                        synchronized(this) {
                            writer.write(
                                event = it,
                                batchMetadata = null,
                                eventType = EventType.DEFAULT
                            )
                        }
                    }
                }
            }
    }

    private fun buildRawBatchEventTtid(
        context: DatadogContext,
        profilingResult: PerfettoResult,
        rumContext: ProfilingRumContext,
        vitalId: String,
        vitalName: String?,
        longTasks: List<ProfilerEvent.RumLongTaskEvent>,
        anrEvents: List<ProfilerEvent.RumAnrEvent>
    ): RawBatchEvent? {
        val byteData = readProfilingData(profilingResult.resultFilePath)
        if (byteData == null || byteData.isEmpty()) {
            return null
        }
        val profileEvent = createProfileEvent(
            context,
            profilingResult,
            rumContext,
            vitalId,
            vitalName,
            longTasks,
            anrEvents
        )
        val serializedEvent =
            profileEvent.toJson().toString().toByteArray(Charsets.UTF_8)
        return RawBatchEvent(data = serializedEvent, metadata = byteData)
    }

    private fun buildRawBatchEventContinuous(
        context: DatadogContext,
        profilingResult: PerfettoResult,
        longTaskEvents: List<ProfilerEvent.RumLongTaskEvent>,
        anrEvents: List<ProfilerEvent.RumAnrEvent>
    ): RawBatchEvent? {
        if (longTaskEvents.isEmpty() && anrEvents.isEmpty()) return null
        val perfettoBytes = readProfilingData(profilingResult.resultFilePath)
        val firstRumContext =
            longTaskEvents.firstOrNull()?.rumContext ?: anrEvents.firstOrNull()?.rumContext
        return if (perfettoBytes == null || perfettoBytes.isEmpty() || firstRumContext == null) {
            null
        } else {
            val profileEvent = createContinuousProfileEvent(
                context = context,
                rumContext = firstRumContext,
                profilingResult = profilingResult,
                longTaskEvents = longTaskEvents,
                anrEvents = anrEvents
            )
            val serializedEvent = profileEvent.toJson().toString().toByteArray(Charsets.UTF_8)
            val rumMobileEventsJson = buildRumMobileEventsJson(longTaskEvents, anrEvents)
            val metadata = ProfilingBatchMetadata(perfettoBytes, rumMobileEventsJson).toBytes()
            RawBatchEvent(data = serializedEvent, metadata = metadata)
        }
    }

    private fun createProfileEvent(
        context: DatadogContext,
        profilingResult: PerfettoResult,
        rumContext: ProfilingRumContext,
        vitalId: String,
        vitalName: String?,
        longTasks: List<ProfilerEvent.RumLongTaskEvent>,
        anrEvents: List<ProfilerEvent.RumAnrEvent>
    ): ProfileEvent {
        // needed to benefit from smart-cast below, reading property only once
        val rumViewId = rumContext.viewId
        val rumViewName = rumContext.viewName
        val viewIds = linkedSetOf<String>()
        val viewNames = linkedSetOf<String>()
        // Only include TTID view context when both id and name are present
        if (rumViewId != null && rumViewName != null) {
            viewIds.add(rumViewId)
            viewNames.add(rumViewName)
        }
        val longTaskIds = mutableListOf<String>()
        val anrIds = mutableListOf<String>()
        for (event in longTasks) {
            longTaskIds.add(event.id)
            val viewId = event.rumContext.viewId
            val viewName = event.rumContext.viewName
            if (viewId != null && viewName != null) {
                viewIds.add(viewId)
                viewNames.add(viewName)
            }
        }
        for (event in anrEvents) {
            anrIds.add(event.id)
            val viewId = event.rumContext.viewId
            val viewName = event.rumContext.viewName
            if (viewId != null && viewName != null) {
                viewIds.add(viewId)
                viewNames.add(viewName)
            }
        }
        return ProfileEvent(
            start = formatIsoUtc(profilingResult.start),
            end = formatIsoUtc(profilingResult.end),
            attachments = listOf(PERFETTO_ATTACHMENT_NAME),
            family = ProfileEvent.Family.ANDROID,
            runtime = ProfileEvent.Family.ANDROID,
            version = VERSION_NUMBER,
            tagsProfiler = buildTags(context, OPERATION_TYPE_LAUNCH),
            application = ProfileEvent.Application(id = rumContext.applicationId),
            session = ProfileEvent.Session(id = rumContext.sessionId),
            vital = ProfileEvent.Vital(
                id = listOf(vitalId),
                label = listOf(vitalName.orEmpty())
            ),
            longTask = ProfileEvent.LongTask(id = longTaskIds),
            error = ProfileEvent.Error(id = anrIds),
            view = if (viewIds.isNotEmpty()) {
                ProfileEvent.View(id = viewIds.toList(), name = viewNames.toList())
            } else {
                null
            }
        )
    }

    private fun createContinuousProfileEvent(
        context: DatadogContext,
        rumContext: ProfilingRumContext,
        profilingResult: PerfettoResult,
        longTaskEvents: List<ProfilerEvent.RumLongTaskEvent>,
        anrEvents: List<ProfilerEvent.RumAnrEvent>
    ): ProfileEvent {
        val viewIds = HashSet<String>()
        val viewNames = HashSet<String>()
        val longTaskIds = HashSet<String>()
        val anrIds = HashSet<String>()
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
        return ProfileEvent(
            start = formatIsoUtc(profilingResult.start),
            end = formatIsoUtc(profilingResult.end),
            attachments = listOf(
                PERFETTO_ATTACHMENT_NAME
                // TODO RUM-15408: Wait for profiling-backend to support RUM events labelling
                // RUM_MOBILE_EVENTS_ATTACHMENT_NAME
            ),
            family = ProfileEvent.Family.ANDROID,
            runtime = ProfileEvent.Family.ANDROID,
            version = VERSION_NUMBER,
            tagsProfiler = buildTags(context, OPERATION_TYPE_CONTINUOUS),
            application = ProfileEvent.Application(id = rumContext.applicationId),
            session = ProfileEvent.Session(id = rumContext.sessionId),
            longTask = ProfileEvent.LongTask(id = longTaskIds.toList()),
            error = ProfileEvent.Error(id = anrIds.toList()),
            view = ProfileEvent.View(
                id = viewIds.toList(),
                name = viewNames.toList()
            )
        )
    }

    private fun buildRumMobileEventsJson(
        longTasks: List<ProfilerEvent.RumLongTaskEvent>,
        anrEvents: List<ProfilerEvent.RumAnrEvent>
    ): ByteArray {
        val rumMobileEvents = RumMobileEvents(
            errors = anrEvents.takeIf { it.isNotEmpty() }?.map { event ->
                RumMobileEvents.Error(
                    id = event.id,
                    startNs = TimeUnit.MILLISECONDS.toNanos(event.startMs),
                    durationNs = event.durationNs
                )
            },
            longTasks = longTasks.takeIf { it.isNotEmpty() }?.map { event ->
                RumMobileEvents.LongTask(
                    id = event.id,
                    startNs = TimeUnit.MILLISECONDS.toNanos(event.startMs),
                    durationNs = event.durationNs
                )
            }
        )
        return rumMobileEvents.toJson().toString().toByteArray(Charsets.UTF_8)
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

    companion object {
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
        private const val OPERATION_TYPE_LAUNCH = "launch"
        private const val OPERATION_TYPE_CONTINUOUS = "continuous"

        // Only `4` is supported by profiling Backend
        private const val VERSION_NUMBER = 4L
    }
}
