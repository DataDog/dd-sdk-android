/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.log.internal.utils

import com.datadog.android.log.Logger
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import android.util.Log as AndroidLog

internal const val ISO_8601 = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"

@Suppress("UnsafeThirdPartyFunctionCall")
internal fun buildLogDateFormat(): SimpleDateFormat =
    // NPE cannot happen here, ISO_8601 pattern is valid
    SimpleDateFormat(ISO_8601, Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

// region Telemetry logging

// All the things below (fields + methods) are supposed to be used only
// with [sdkLogger] backed by [InternalLogHandler].

// TODO RUMM-2092 This is a temporary hack to simplify migration, should be replaced by the
//  solution described in the ticket.
internal const val TELEMETRY_LOG_FLAG = 32

internal const val ERROR_WITH_TELEMETRY_LEVEL =
    AndroidLog.ERROR or TELEMETRY_LOG_FLAG
internal const val WARN_WITH_TELEMETRY_LEVEL =
    AndroidLog.WARN or TELEMETRY_LOG_FLAG
internal const val DEBUG_WITH_TELEMETRY_LEVEL =
    AndroidLog.DEBUG or TELEMETRY_LOG_FLAG

internal fun Logger.errorWithTelemetry(
    message: String,
    throwable: Throwable? = null,
    attributes: Map<String, Any?> = emptyMap()
) {
    this.log(
        ERROR_WITH_TELEMETRY_LEVEL,
        message,
        throwable,
        attributes
    )
}

internal fun Logger.warningWithTelemetry(
    message: String,
    throwable: Throwable? = null,
    attributes: Map<String, Any?> = emptyMap()
) {
    this.log(
        WARN_WITH_TELEMETRY_LEVEL,
        message,
        throwable,
        attributes
    )
}

internal fun Logger.debugWithTelemetry(
    message: String,
    throwable: Throwable? = null,
    attributes: Map<String, Any?> = emptyMap()
) {
    this.log(
        DEBUG_WITH_TELEMETRY_LEVEL,
        message,
        throwable,
        attributes
    )
}

// endregion
