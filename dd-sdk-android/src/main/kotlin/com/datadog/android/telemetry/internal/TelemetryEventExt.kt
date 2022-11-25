/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.telemetry.internal

import com.datadog.android.core.internal.utils.devLogger
import com.datadog.android.telemetry.model.TelemetryConfigurationEvent
import com.datadog.android.telemetry.model.TelemetryDebugEvent
import com.datadog.android.telemetry.model.TelemetryErrorEvent
import java.util.Locale

internal fun TelemetryDebugEvent.Source.Companion.tryFromSource(source: String):
    TelemetryDebugEvent.Source? {
    return try {
        fromJson(source)
    } catch (e: NoSuchElementException) {
        devLogger.e(UNKNOWN_SOURCE_WARNING_MESSAGE_FORMAT.format(Locale.US, source), e)
        null
    }
}

internal fun TelemetryErrorEvent.Source.Companion.tryFromSource(source: String):
    TelemetryErrorEvent.Source? {
    return try {
        fromJson(source)
    } catch (e: NoSuchElementException) {
        devLogger.e(UNKNOWN_SOURCE_WARNING_MESSAGE_FORMAT.format(Locale.US, source), e)
        null
    }
}

internal fun TelemetryConfigurationEvent.Source.Companion.tryFromSource(source: String):
    TelemetryConfigurationEvent.Source? {
    return try {
        fromJson(source)
    } catch (e: NoSuchElementException) {
        devLogger.e(UNKNOWN_SOURCE_WARNING_MESSAGE_FORMAT.format(Locale.US, source), e)
        null
    }
}

internal const val UNKNOWN_SOURCE_WARNING_MESSAGE_FORMAT = "You are using an unknown " +
    "source %s for your events"
