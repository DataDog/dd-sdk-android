/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.telemetry.internal

import com.datadog.android.api.InternalLogger
import com.datadog.android.rum.internal.generated.DdSdkAndroidRumLogger
import com.datadog.android.telemetry.model.TelemetryConfigurationEvent
import com.datadog.android.telemetry.model.TelemetryDebugEvent
import com.datadog.android.telemetry.model.TelemetryErrorEvent
import com.datadog.android.telemetry.model.TelemetryUsageEvent

internal fun TelemetryDebugEvent.Source.Companion.tryFromSource(
    source: String,
    internalLogger: InternalLogger
): TelemetryDebugEvent.Source? {
    return try {
        fromJson(source)
    } catch (e: NoSuchElementException) {
        DdSdkAndroidRumLogger(internalLogger).logUnknownSource(source = source, throwable = e)
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
        DdSdkAndroidRumLogger(internalLogger).logUnknownSource(source = source, throwable = e)
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
        DdSdkAndroidRumLogger(internalLogger).logUnknownSource(source = source, throwable = e)
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
        DdSdkAndroidRumLogger(internalLogger).logUnknownSource(source = source, throwable = e)
        null
    }
}
