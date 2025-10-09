/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

@file:Suppress("MethodOverloading")

package com.datadog.android.rum.utils

import com.datadog.android.api.InternalLogger
import com.datadog.android.internal.telemetry.InternalTelemetryEvent
import com.datadog.android.rum.utils.assertj.InternalApiUsageEventAssert
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify

fun InternalLogger.verifyApiUsage(
    apiUsage: InternalTelemetryEvent.ApiUsage,
    samplingRate: Float
) {
    argumentCaptor<() -> InternalTelemetryEvent.ApiUsage> {
        verify(this@verifyApiUsage).logApiUsage(eq(samplingRate), capture())
        val event = firstValue()
        InternalApiUsageEventAssert.assertThat(event).isEqualTo(apiUsage)
    }
}
