/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.profiling.internal.telemetry

internal sealed class ProfilingTelemetryEvent {

    data class SessionEnd(
        val startReason: String,
        val appStartInfo: String?,
        val errorCode: Int,
        val errorMessage: String?,
        val fileSize: Long,
        val durationMs: Long,
        val resultCallbackDelayMs: Long,
        val stopReason: String,
        val bufferSizeKb: Int,
        val samplingFrequencyHz: Int
    ) : ProfilingTelemetryEvent()

    data class AnrTriggerResult(
        val errorCode: Int,
        val errorMessage: String?,
        val fileSize: Long,
        val callbackDelayMs: Long?,
        val droppedAsStale: Boolean
    ) : ProfilingTelemetryEvent()
}
