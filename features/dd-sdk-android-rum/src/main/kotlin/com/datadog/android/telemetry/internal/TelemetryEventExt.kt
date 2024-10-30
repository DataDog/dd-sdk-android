/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.telemetry.internal

import com.datadog.android.api.InternalLogger
import com.datadog.android.telemetry.model.TelemetryConfigurationEvent
import com.datadog.android.telemetry.model.TelemetryDebugEvent
import com.datadog.android.telemetry.model.TelemetryErrorEvent
import com.datadog.android.telemetry.model.TelemetryUsageEvent
import java.util.Locale

internal fun TelemetryDebugEvent.Source.Companion.tryFromSource(
    source: String,
    internalLogger: InternalLogger
): TelemetryDebugEvent.Source? {
    return try {
        fromJson(source)
    } catch (e: NoSuchElementException) {
        internalLogger.log(
            InternalLogger.Level.ERROR,
            InternalLogger.Target.USER,
            { UNKNOWN_SOURCE_WARNING_MESSAGE_FORMAT.format(Locale.US, source) },
            e
        )
        null
    }
}

internal fun TelemetryErrorEvent.Source.Companion.tryFromSource(
    source: String,
    internalLogger: InternalLogger
): TelemetryErrorEvent.Source? {
    return try {
        fromJson(source)
    } catch (e: NoSuchElementException) {
        internalLogger.log(
            InternalLogger.Level.ERROR,
            InternalLogger.Target.USER,
            { UNKNOWN_SOURCE_WARNING_MESSAGE_FORMAT.format(Locale.US, source) },
            e
        )
        null
    }
}

internal fun TelemetryUsageEvent.Source.Companion.tryFromSource(
    source: String,
    internalLogger: InternalLogger
): TelemetryUsageEvent.Source? {
    return try {
        fromJson(source)
    } catch (e: NoSuchElementException) {
        internalLogger.log(
            InternalLogger.Level.ERROR,
            InternalLogger.Target.USER,
            { UNKNOWN_SOURCE_WARNING_MESSAGE_FORMAT.format(Locale.US, source) },
            e
        )
        null
    }
}

internal fun TelemetryConfigurationEvent.Source.Companion.tryFromSource(
    source: String,
    internalLogger: InternalLogger
): TelemetryConfigurationEvent.Source? {
    return try {
        fromJson(source)
    } catch (e: NoSuchElementException) {
        internalLogger.log(
            InternalLogger.Level.ERROR,
            InternalLogger.Target.USER,
            { UNKNOWN_SOURCE_WARNING_MESSAGE_FORMAT.format(Locale.US, source) },
            e
        )
        null
    }
}

internal const val UNKNOWN_SOURCE_WARNING_MESSAGE_FORMAT = "You are using an unknown " +
    "source %s for your events"
