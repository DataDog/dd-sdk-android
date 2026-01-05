/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.utils.assertj

import com.datadog.android.internal.telemetry.InternalTelemetryEvent
import org.assertj.core.api.AbstractAssert
import org.assertj.core.api.Assertions.assertThat

internal class InternalTrackWebViewEventAssert(actual: InternalTelemetryEvent.ApiUsage.TrackWebView) :
    AbstractAssert<InternalTrackWebViewEventAssert, InternalTelemetryEvent.ApiUsage.TrackWebView>(
        actual,
        InternalTrackWebViewEventAssert::class.java
    ) {

    fun isEqualTo(expected: InternalTelemetryEvent.ApiUsage.TrackWebView) {
        hasAdditionalProperties(expected.additionalProperties)
    }

    fun hasAdditionalProperties(expected: Map<String, Any?>): InternalTrackWebViewEventAssert {
        assertThat(actual.additionalProperties)
            .overridingErrorMessage(
                "Expected trackWebView event to have" +
                    " additionalProperties $expected but was ${actual.additionalProperties}"
            )
            .isEqualTo(expected)
        return this
    }

    companion object {
        fun assertThat(actual: InternalTelemetryEvent.ApiUsage.TrackWebView) =
            InternalTrackWebViewEventAssert(actual)
    }
}
