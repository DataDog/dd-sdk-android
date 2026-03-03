/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.cronet

import com.datadog.android.cronet.CronetIntegrationPlugin.Companion.CRONET_NETWORK_INSTRUMENTATION_NAME
import com.datadog.android.cronet.internal.CronetRequestFinishedInfoListener
import com.datadog.android.cronet.internal.DatadogCronetEngine
import com.datadog.android.rum.ExperimentalRumApi
import com.datadog.android.rum.RumResourceAttributesProvider
import com.datadog.android.rum.configuration.RumNetworkInstrumentationConfiguration
import com.datadog.android.rum.internal.net.RumNetworkInstrumentationAssert
import com.datadog.android.tests.elmyr.aHostName
import com.datadog.android.trace.ApmNetworkInstrumentationConfiguration
import com.datadog.android.trace.ExperimentalTraceApi
import com.datadog.android.utils.forge.Configurator
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.StringForgery
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
import org.mockito.kotlin.argumentCaptor
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
@OptIn(ExperimentalRumApi::class, ExperimentalTraceApi::class)
internal class DatadogCronetEngineBuilderTest {
    @Mock
    private lateinit var mockBuilderDelegate: CronetEngine.Builder

    private lateinit var fakeTracedHost: List<String>

    @Mock
    private lateinit var mockCronetEngine: CronetEngine

    @BeforeEach
    fun `set up`(forge: Forge) {
        fakeTracedHost = listOf(forge.aHostName())
        whenever(mockBuilderDelegate.build()).thenReturn(mockCronetEngine)
    }

    @Test
    fun `M propagate RumResourceAttributesProvider W build() { rumInstrumentationConfiguration }`() {
        // Given
        val customProvider = mock<RumResourceAttributesProvider>()

        // When
        val engine = mockBuilderDelegate
            .configureDatadogInstrumentation(
                apmInstrumentationConfiguration = null,
                rumInstrumentationConfiguration = RumNetworkInstrumentationConfiguration()
                    .setRumResourceAttributesProvider(customProvider)
            )
            .build()

        // Then
        check(engine is DatadogCronetEngine)
        RumNetworkInstrumentationAssert.assertThat(checkNotNull(engine.rumNetworkInstrumentation))
            .hasRumResourceAttributesProvider(customProvider)
    }

    @Test
    fun `M propagate sdkInstanceName W build() { rumInstrumentationConfiguration }`(
        @StringForgery sdkInstanceName: String
    ) {
        // When
        val engine = mockBuilderDelegate
            .configureDatadogInstrumentation(
                apmInstrumentationConfiguration = null,
                rumInstrumentationConfiguration = RumNetworkInstrumentationConfiguration()
                    .setSdkInstanceName(sdkInstanceName)
            )
            .build()

        // Then
        check(engine is DatadogCronetEngine)
        RumNetworkInstrumentationAssert.assertThat(checkNotNull(engine.rumNetworkInstrumentation))
            .hasSdkInstanceName(sdkInstanceName)
    }

    @Test
    fun `M use Cronet as network layer name W build() { rumInstrumentationConfiguration }`() {
        // When
        val engine = mockBuilderDelegate
            .configureDatadogInstrumentation(
                apmInstrumentationConfiguration = null,
                rumInstrumentationConfiguration = RumNetworkInstrumentationConfiguration()
            )
            .build()

        // Then
        check(engine is DatadogCronetEngine)
        RumNetworkInstrumentationAssert.assertThat(checkNotNull(engine.rumNetworkInstrumentation))
            .hasNetworkLayerName(CRONET_NETWORK_INSTRUMENTATION_NAME)
    }

    @Test
    fun `M propagate executor W setListenerExecutor() + build()`() {
        // Given
        val customExecutor = mock<Executor>()

        // When
        val engine = mockBuilderDelegate
            .configureDatadogInstrumentation(
                rumInstrumentationConfiguration = RumNetworkInstrumentationConfiguration(),
                apmInstrumentationConfiguration = null
            )
            .setListenerExecutor(customExecutor)
            .build()

        // Then
        check(engine is DatadogCronetEngine)
        argumentCaptor<CronetRequestFinishedInfoListener> {
            verify(mockCronetEngine).addRequestFinishedListener(capture())
            assertThat(firstValue.executor).isSameAs(customExecutor)
            assertThat(firstValue.rumNetworkInstrumentation).isSameAs(engine.rumNetworkInstrumentation)
        }
    }

    @Test
    fun `M create DatadogCronetEngine with APM W build() { only apmInstrumentationConfiguration }`() {
        // When
        val engine = mockBuilderDelegate
            .configureDatadogInstrumentation(
                rumInstrumentationConfiguration = null,
                apmInstrumentationConfiguration = ApmNetworkInstrumentationConfiguration(fakeTracedHost)
            )
            .build()

        // Then
        check(engine is DatadogCronetEngine)
        assertThat(engine.apmNetworkInstrumentation).isNotNull()
        assertThat(engine.rumNetworkInstrumentation).isNull()
    }

    @Test
    fun `M create DatadogCronetEngine with both W build() { apm + rum configurations }`() {
        // When
        val engine = mockBuilderDelegate
            .configureDatadogInstrumentation(
                rumInstrumentationConfiguration = RumNetworkInstrumentationConfiguration(),
                apmInstrumentationConfiguration = ApmNetworkInstrumentationConfiguration(fakeTracedHost)
            )
            .build()

        // Then
        check(engine is DatadogCronetEngine)
        assertThat(engine.apmNetworkInstrumentation).isNotNull()
        assertThat(engine.rumNetworkInstrumentation).isNotNull()
    }

    @Test
    fun `M return plain CronetEngine W build() { no instrumentation }`() {
        // When
        val engine = mockBuilderDelegate
            .configureDatadogInstrumentation(
                rumInstrumentationConfiguration = null,
                apmInstrumentationConfiguration = null
            )
            .build()

        // Then
        assertThat(engine).isNotInstanceOf(DatadogCronetEngine::class.java)
        assertThat(engine).isSameAs(mockCronetEngine)
    }

    @Test
    fun `M return self W setListenerExecutor()`() {
        // Given
        val testedPlugin = mockBuilderDelegate
            .configureDatadogInstrumentation(
                rumInstrumentationConfiguration = null,
                apmInstrumentationConfiguration = null
            )

        // When
        val result = testedPlugin.setListenerExecutor(mock())

        // Then
        assertThat(result).isSameAs(testedPlugin)
    }
}
