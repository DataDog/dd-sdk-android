/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.net

import org.assertj.core.api.Assertions.assertThat
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.verification.VerificationMode

fun RumNetworkInstrumentation.verifyReportInstrumentationError(
    message: String,
    mode: VerificationMode = times(1)
) {
    argumentCaptor<() -> String> {
        verify(this@verifyReportInstrumentationError, mode).reportInstrumentationError(capture())
        allValues.forEach { assertThat(it()).isEqualTo(message) }
    }
}
