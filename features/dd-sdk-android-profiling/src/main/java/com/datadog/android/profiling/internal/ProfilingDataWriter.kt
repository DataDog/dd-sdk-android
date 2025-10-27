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
import com.datadog.android.internal.utils.formatIsoUtc
import com.datadog.android.profiling.internal.perfetto.PerfettoResult
import com.datadog.android.profiling.model.ProfileEvent
import java.io.File

internal class ProfilingDataWriter(
    private val sdkCore: FeatureSdkCore
) : ProfilingWriter {
    override fun write(
        profilingResult: PerfettoResult
    ) {
        sdkCore.getFeature(Feature.PROFILING_FEATURE_NAME)
            ?.withWriteContext { context, writeScope ->
                writeScope { writer ->
                    val rawBatchEvent = buildRawBatchEvent(
                        context = context,
                        profilingResult = profilingResult
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

    private fun buildRawBatchEvent(
        context: DatadogContext,
        profilingResult: PerfettoResult
    ): RawBatchEvent? {
        val byteData = readProfilingData(profilingResult.resultFilePath)
        if (byteData == null || byteData.isEmpty()) {
            return null
        }
        val profileEvent = createProfileEvent(context, profilingResult)
        val serializedEvent =
            profileEvent.toJson().toString().toByteArray(Charsets.UTF_8)
        return RawBatchEvent(data = serializedEvent, metadata = byteData)
    }

    private fun createProfileEvent(
        context: DatadogContext,
        profilingResult: PerfettoResult
    ): ProfileEvent {
        return ProfileEvent(
            start = formatIsoUtc(profilingResult.start),
            end = formatIsoUtc(profilingResult.end),
            attachments = listOf(PERFETTO_ATTACHMENT_NAME),
            family = ANDROID_FAMILY_NAME,
            version = VERSION_NUMBER,
            tagsProfiler = buildTags(context)
        )
    }

    private fun buildTags(context: DatadogContext): String = buildString {
        append("$TAG_KEY_SERVICE:${context.service}")
        append(",")
        append("$TAG_KEY_VERSION:${context.version}")
    }

    private fun readProfilingData(profilingPath: String): ByteArray? {
        @Suppress("UnsafeThirdPartyFunctionCall")
        // profilingPath is not null in kotlin, it can't throw NPE
        return File(profilingPath).readBytesSafe(internalLogger = sdkCore.internalLogger)
    }

    companion object {
        private const val TAG_KEY_SERVICE = "service"
        private const val TAG_KEY_VERSION = "version"
        private const val PERFETTO_ATTACHMENT_NAME = "perfetto.proto"
        private const val ANDROID_FAMILY_NAME = "android"

        // Only `4` is supported by profiling Backend
        private const val VERSION_NUMBER = "4"
    }
}
