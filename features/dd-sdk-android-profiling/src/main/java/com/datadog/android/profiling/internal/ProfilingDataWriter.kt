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
import com.datadog.android.profiling.internal.perfetto.PerfettoResult
import com.datadog.android.profiling.model.ProfileEvent
import java.io.File

internal class ProfilingDataWriter(
    private val sdkCore: FeatureSdkCore
) : ProfilingWriter {
    override fun write(
        profilingResult: PerfettoResult,
        rumContext: ProfilingRumContext,
        vitalId: String,
        vitalName: String?
    ) {
        sdkCore.getFeature(Feature.PROFILING_FEATURE_NAME)
            ?.withWriteContext { context, writeScope ->
                writeScope { writer ->
                    val rawBatchEvent = buildRawBatchEvent(
                        context = context,
                        profilingResult = profilingResult,
                        rumContext = rumContext,
                        vitalId = vitalId,
                        vitalName = vitalName
                    )
                    if (rawBatchEvent != null) {
                        synchronized(this) {
                            writer.write(
                                event = rawBatchEvent,
                                batchMetadata = null,
                                eventType = EventType.DEFAULT
                            )
                        }
                    }
                }
            }
    }

    override fun write(
        profilingResult: PerfettoResult,
        longTasks: List<ProfilerEvent.RumLongTaskEvent>,
        anrEvents: List<ProfilerEvent.RumAnrEvent>
    ) {
        // TODO RUM-15193: write continuous profiling event
    }

    private fun buildRawBatchEvent(
        context: DatadogContext,
        profilingResult: PerfettoResult,
        rumContext: ProfilingRumContext,
        vitalId: String,
        vitalName: String?
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
            vitalName
        )
        val serializedEvent =
            profileEvent.toJson().toString().toByteArray(Charsets.UTF_8)
        return RawBatchEvent(data = serializedEvent, metadata = byteData)
    }

    private fun createProfileEvent(
        context: DatadogContext,
        profilingResult: PerfettoResult,
        rumContext: ProfilingRumContext,
        vitalId: String,
        vitalName: String?
    ): ProfileEvent {
        // needed to benefit from smart-cast below, reading property only once
        val rumViewId = rumContext.viewId
        val rumViewName = rumContext.viewName
        return ProfileEvent(
            start = formatIsoUtc(profilingResult.start),
            end = formatIsoUtc(profilingResult.end),
            attachments = listOf(PERFETTO_ATTACHMENT_NAME),
            family = ProfileEvent.Family.ANDROID,
            runtime = ProfileEvent.Family.ANDROID,
            version = VERSION_NUMBER,
            tagsProfiler = buildTags(context),
            application = ProfileEvent.Application(id = rumContext.applicationId),
            session = ProfileEvent.Session(id = rumContext.sessionId),
            vital = ProfileEvent.Vital(
                id = listOf(vitalId),
                label = listOf(vitalName.orEmpty())
            ),
            view = if (rumViewId != null && rumViewName != null) {
                ProfileEvent.View(
                    id = listOf(rumViewId),
                    name = listOf(rumViewName)
                )
            } else {
                null
            }
        )
    }

    private fun buildTags(
        context: DatadogContext
    ): String = buildString {
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
        append("$TAG_KEY_OPERATION:$OPERATION_TYPE_LAUNCH")
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
        private const val PERFETTO_ATTACHMENT_NAME = "perfetto.proto"
        private const val OPERATION_TYPE_LAUNCH = "launch"

        // Only `4` is supported by profiling Backend
        private const val VERSION_NUMBER = 4L
    }
}
