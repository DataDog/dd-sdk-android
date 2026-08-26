/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

// detekt's UnusedImports rule false-positives here: the companion `assertThat` shadows the
// imported one by name, but the actual call below resolves to the import (type mismatch rules
// out the companion overload) — confirmed by successful compilation.
@file:Suppress("UnusedImports")

package com.datadog.android.utils.assertj

import com.datadog.android.internal.telemetry.InternalTelemetryEvent
import org.assertj.core.api.AbstractAssert
import org.assertj.core.api.Assertions.assertThat

class InternalTrackWebViewEventAssert(actual: InternalTelemetryEvent.ApiUsage.TrackWebView) :
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
