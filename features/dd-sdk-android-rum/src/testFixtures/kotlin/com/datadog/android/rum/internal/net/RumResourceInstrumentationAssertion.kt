/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.net

import com.datadog.android.rum.RumResourceAttributesProvider
import org.assertj.core.api.Assertions

class RumResourceInstrumentationAssertion private constructor(private val instrumentation: RumResourceInstrumentation) {

    fun hasNetworkLayerName(expected: String) = apply {
        Assertions.assertThat(instrumentation.networkLayerName).isEqualTo(expected)
    }

    fun hasSdkInstanceName(expected: String) = apply {
        Assertions.assertThat(instrumentation.sdkInstanceName).isEqualTo(expected)
    }

    fun hasRumResourceAttributesProvider(expected: RumResourceAttributesProvider) = apply {
        Assertions.assertThat(instrumentation.rumResourceAttributesProvider).isEqualTo(expected)
    }

    companion object {
        fun assertThat(instrumentation: RumResourceInstrumentation): RumResourceInstrumentationAssertion {
            return RumResourceInstrumentationAssertion(instrumentation)
        }
    }
}
