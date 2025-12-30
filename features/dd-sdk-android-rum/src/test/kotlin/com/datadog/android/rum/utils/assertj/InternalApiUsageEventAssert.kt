/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.utils.assertj

import com.datadog.android.internal.telemetry.InternalTelemetryEvent
import org.assertj.core.api.AbstractAssert

class InternalApiUsageEventAssert(actual: InternalTelemetryEvent.ApiUsage) :
    AbstractAssert<InternalApiUsageEventAssert, InternalTelemetryEvent.ApiUsage>(
        actual,
        InternalApiUsageEventAssert::class.java
    ) {

    fun isEqualTo(expected: InternalTelemetryEvent.ApiUsage): InternalApiUsageEventAssert {
        when (actual) {
            is InternalTelemetryEvent.ApiUsage.AddViewLoadingTime -> {
                InternalAddViewLoadingTimeEventAssert
                    .assertThat(actual as InternalTelemetryEvent.ApiUsage.AddViewLoadingTime)
                    .isEqualTo(expected as InternalTelemetryEvent.ApiUsage.AddViewLoadingTime)
            }

            is InternalTelemetryEvent.ApiUsage.AddOperationStepVital -> {
                InternalAddOperationStepVitalAssert
                    .assertThat(actual as InternalTelemetryEvent.ApiUsage.AddOperationStepVital)
                    .isEqualTo(expected as InternalTelemetryEvent.ApiUsage.AddOperationStepVital)
            }

            is InternalTelemetryEvent.ApiUsage.TrackWebView -> {
                InternalTrackWebViewEventAssert
                    .assertThat(actual as InternalTelemetryEvent.ApiUsage.TrackWebView)
                    .isEqualTo(expected as InternalTelemetryEvent.ApiUsage.TrackWebView)
            }

            else -> {
                failWithMessage("Unknown event type: ${actual::class.java.simpleName}")
            }
        }
        return this
    }

    companion object {
        fun assertThat(actual: InternalTelemetryEvent.ApiUsage): InternalApiUsageEventAssert {
            return InternalApiUsageEventAssert(actual)
        }
    }
}
