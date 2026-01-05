/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.trace.internal

import com.datadog.android.trace.NetworkTracingInstrumentation
import com.datadog.android.trace.NetworkTracingInstrumentationConfiguration
import com.datadog.android.trace.TracingHeaderType
import com.datadog.android.utils.forge.Configurator
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class DatadogTracingToolkitTest {

    private lateinit var fakeTracedHosts: Map<String, Set<TracingHeaderType>>

    @StringForgery
    lateinit var fakeInstrumentationName: String

    @BeforeEach
    fun `set up`(forge: Forge) {
        fakeTracedHosts = forge.aMap {
            aStringMatching("[a-z]+\\.[a-z]{2,3}") to forge.aList {
                aValueFrom(TracingHeaderType::class.java)
            }.toSet()
        }
    }

    @Test
    fun `M build TracingInstrumentation W build() extension`() {
        // Given
        val builder = NetworkTracingInstrumentationConfiguration(fakeTracedHosts)

        // When
        val result: NetworkTracingInstrumentation = with(DatadogTracingToolkit) {
            builder.build(fakeInstrumentationName)
        }

        // Then
        assertThat(result).isNotNull
        assertThat(result.sdkInstanceName).isNull()
        assertThat(result.traceOrigin).isNull()
    }

    @Test
    fun `M build TracingInstrumentation with configured values W build() extension`(
        @StringForgery fakeSdkInstanceName: String,
        @StringForgery fakeTraceOrigin: String
    ) {
        // Given
        val builder = NetworkTracingInstrumentationConfiguration(fakeTracedHosts)
            .setSdkInstanceName(fakeSdkInstanceName)
            .setTraceOrigin(fakeTraceOrigin)

        // When
        val result: NetworkTracingInstrumentation = with(DatadogTracingToolkit) {
            builder.build(fakeInstrumentationName)
        }

        // Then
        assertThat(result).isNotNull
        assertThat(result.sdkInstanceName).isEqualTo(fakeSdkInstanceName)
        assertThat(result.traceOrigin).isEqualTo(fakeTraceOrigin)
    }
}
