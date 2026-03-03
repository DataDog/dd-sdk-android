/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.cronet

import com.datadog.android.cronet.internal.DatadogCronetEngine
import com.datadog.android.rum.ExperimentalRumApi
import com.datadog.android.rum.configuration.RumNetworkInstrumentationConfiguration
import com.datadog.android.tests.elmyr.aHostName
import com.datadog.android.trace.ApmNetworkInstrumentationConfiguration
import com.datadog.android.trace.ApmNetworkTracingScope
import com.datadog.android.trace.ExperimentalTraceApi
import com.datadog.android.utils.forge.Configurator
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.chromium.net.CronetEngine
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.util.concurrent.Executor

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
@OptIn(
    ExperimentalRumApi::class,
    ExperimentalTraceApi::class
)
internal class CronetIntegrationPluginTest {

    @Mock
    lateinit var mockDelegateBuilder: CronetEngine.Builder

    @Mock
    lateinit var mockCronetEngine: CronetEngine

    private lateinit var fakeTracedHost: List<String>

    @BeforeEach
    fun `set up`(forge: Forge) {
        fakeTracedHost = listOf(forge.aHostName())
        whenever(mockDelegateBuilder.build()) doReturn mockCronetEngine
    }

    @Test
    fun `M return plain CronetEngine W build() {both configs null}`() {
        // When
        val result = mockDelegateBuilder
            .configureDatadogInstrumentation(
                rumInstrumentationConfiguration = null,
                apmInstrumentationConfiguration = null
            )
            .build()

        // Then
        assertThat(result).isSameAs(mockCronetEngine)
        assertThat(result).isNotInstanceOf(DatadogCronetEngine::class.java)
    }

    @Test
    fun `M return DatadogCronetEngine W build() {APM config only}`() {
        // When
        val engine = mockDelegateBuilder
            .configureDatadogInstrumentation(
                rumInstrumentationConfiguration = null,
                apmInstrumentationConfiguration = ApmNetworkInstrumentationConfiguration(fakeTracedHost)
            )
            .build()

        // Then
        check(engine is DatadogCronetEngine)
        assertThat(engine.apmNetworkInstrumentation).isNotNull
        assertThat(engine.rumNetworkInstrumentation).isNull()
        assertThat(engine.distributedTracingInstrumentation).isNull()
    }

    @Test
    fun `M return DatadogCronetEngine W build() {RUM config only}`() {
        // When
        val engine = mockDelegateBuilder
            .configureDatadogInstrumentation(
                rumInstrumentationConfiguration = RumNetworkInstrumentationConfiguration(),
                apmInstrumentationConfiguration = null
            )
            .build()

        // Then
        check(engine is DatadogCronetEngine)
        assertThat(engine.apmNetworkInstrumentation).isNull()
        assertThat(engine.rumNetworkInstrumentation).isNotNull
        assertThat(engine.distributedTracingInstrumentation).isNull()
    }

    @Test
    fun `M return DatadogCronetEngine W build() {both configs}`() {
        // When
        val engine = mockDelegateBuilder
            .configureDatadogInstrumentation(
                rumInstrumentationConfiguration = RumNetworkInstrumentationConfiguration(),
                apmInstrumentationConfiguration = ApmNetworkInstrumentationConfiguration(fakeTracedHost)
            ).build()

        // Then
        check(engine is DatadogCronetEngine)
        assertThat(engine.apmNetworkInstrumentation).isNotNull
        assertThat(engine.rumNetworkInstrumentation).isNotNull
        assertThat(engine.distributedTracingInstrumentation).isNotNull
    }

    @Test
    fun `M create distributedTracingInstrumentation W build() {RUM + APM configs}`() {
        // When
        val engine = mockDelegateBuilder
            .configureDatadogInstrumentation(
                rumInstrumentationConfiguration = RumNetworkInstrumentationConfiguration(),
                apmInstrumentationConfiguration = ApmNetworkInstrumentationConfiguration(fakeTracedHost)
            ).build()

        // Then
        check(engine is DatadogCronetEngine)
        assertThat(engine.distributedTracingInstrumentation).isNotNull
        assertThat(engine.distributedTracingInstrumentation?.traceOrigin)
            .isEqualTo(CronetIntegrationPlugin.ORIGIN_RUM)
        assertThat(engine.distributedTracingInstrumentation?.networkTracingScope)
            .isEqualTo(ApmNetworkTracingScope.EXCLUDE_INTERNAL_REDIRECTS)
    }

    @Test
    fun `M not create distributedTracingInstrumentation W build() {APM only, no RUM}`() {
        // When
        val engine = mockDelegateBuilder
            .configureDatadogInstrumentation(
                rumInstrumentationConfiguration = null,
                apmInstrumentationConfiguration = ApmNetworkInstrumentationConfiguration(fakeTracedHost)
            ).build()

        // Then
        check(engine is DatadogCronetEngine)
        assertThat(engine.distributedTracingInstrumentation).isNull()
    }

    @Test
    fun `M use custom executor W build() {setListenerExecutor called}`() {
        // Given
        val mockExecutor = mock<Executor>()

        // When
        val engine = mockDelegateBuilder
            .configureDatadogInstrumentation(
                rumInstrumentationConfiguration = RumNetworkInstrumentationConfiguration(),
                apmInstrumentationConfiguration = null
            )
            .setListenerExecutor(mockExecutor)
            .build()

        // Then
        assertThat(engine).isInstanceOf(DatadogCronetEngine::class.java)
    }

    @Test
    fun `M set traceOrigin to rum on distributedTracing W build() {RUM + APM}`() {
        // When
        val engine = mockDelegateBuilder.configureDatadogInstrumentation(
            rumInstrumentationConfiguration = RumNetworkInstrumentationConfiguration(),
            apmInstrumentationConfiguration = ApmNetworkInstrumentationConfiguration(fakeTracedHost)
        ).build()

        // Then
        check(engine is DatadogCronetEngine)
        assertThat(engine.distributedTracingInstrumentation?.traceOrigin)
            .isEqualTo(CronetIntegrationPlugin.ORIGIN_RUM)
    }

    @Test
    fun `M not set traceOrigin on apmInstrumentation W build() {RUM + APM}`() {
        // When
        val engine = mockDelegateBuilder.configureDatadogInstrumentation(
            rumInstrumentationConfiguration = RumNetworkInstrumentationConfiguration(),
            apmInstrumentationConfiguration = ApmNetworkInstrumentationConfiguration(fakeTracedHost)
        ).build()

        // Then
        check(engine is DatadogCronetEngine)
        assertThat(engine.apmNetworkInstrumentation?.traceOrigin).isNull()
    }

    @Test
    fun `M not create apmInstrumentation W build() {setHeaderPropagationOnly}`() {
        // When
        val engine = mockDelegateBuilder
            .configureDatadogInstrumentation(
                rumInstrumentationConfiguration = RumNetworkInstrumentationConfiguration(),
                apmInstrumentationConfiguration = ApmNetworkInstrumentationConfiguration(fakeTracedHost)
                    .setHeaderPropagationOnly()
            ).build()

        // Then
        check(engine is DatadogCronetEngine)
        assertThat(engine.apmNetworkInstrumentation).isNull()
        assertThat(engine.distributedTracingInstrumentation).isNotNull
    }

    @Test
    fun `M create both instrumentations W build() {canSendSpan is true by default}`() {
        // When
        val engine = mockDelegateBuilder
            .configureDatadogInstrumentation(
                rumInstrumentationConfiguration = RumNetworkInstrumentationConfiguration(),
                apmInstrumentationConfiguration = ApmNetworkInstrumentationConfiguration(fakeTracedHost)
            ).build()

        // Then
        check(engine is DatadogCronetEngine)
        assertThat(engine.apmNetworkInstrumentation).isNotNull
        assertThat(engine.distributedTracingInstrumentation).isNotNull
    }

    @Test
    fun `M delegate to builder W build() {both configs null}`() {
        // When
        mockDelegateBuilder
            .configureDatadogInstrumentation(
                rumInstrumentationConfiguration = null,
                apmInstrumentationConfiguration = null
            )
            .build()

        // Then
        verify(mockDelegateBuilder).build()
    }
}
