/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.okhttp

import com.datadog.android.api.InternalLogger
import com.datadog.android.okhttp.internal.ApmInstrumentationOkHttpAdapter
import com.datadog.android.okhttp.internal.RegistryTrackingEventListener
import com.datadog.android.okhttp.internal.RumInstrumentationOkHttpAdapter
import com.datadog.android.rum.ExperimentalRumApi
import com.datadog.android.rum.configuration.RumNetworkInstrumentationConfiguration
import com.datadog.android.tests.config.DatadogSingletonTestConfiguration
import com.datadog.android.trace.ApmNetworkInstrumentationConfiguration
import com.datadog.android.trace.ApmNetworkTracingScope
import com.datadog.android.trace.ExperimentalTraceApi
import com.datadog.tools.unit.annotations.TestConfigurationsProvider
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import com.datadog.tools.unit.extensions.config.TestConfiguration
import com.datadog.tools.unit.forge.BaseConfigurator
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import okhttp3.Call
import okhttp3.EventListener
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class),
    ExtendWith(TestConfigurationExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(BaseConfigurator::class)
@OptIn(ExperimentalRumApi::class, ExperimentalTraceApi::class)
internal class OkHttpIntegrationPluginTest {

    @Mock
    private lateinit var mockBuilderDelegate: OkHttpClient.Builder

    @Mock
    private lateinit var mockOkHttpClient: OkHttpClient

    @Mock
    private lateinit var mockInternalLogger: InternalLogger

    @BeforeEach
    fun `set up`() {
        whenever(datadogCore.mockInstance.internalLogger).thenReturn(mockInternalLogger)
        whenever(mockBuilderDelegate.interceptors()).thenReturn(mutableListOf())
        whenever(mockBuilderDelegate.networkInterceptors()).thenReturn(mutableListOf())
        whenever(mockBuilderDelegate.addInterceptor(any<Interceptor>()))
            .thenReturn(mockBuilderDelegate)
        whenever(mockBuilderDelegate.addNetworkInterceptor(any<Interceptor>()))
            .thenReturn(mockBuilderDelegate)
        whenever(mockBuilderDelegate.eventListenerFactory(any()))
            .thenReturn(mockBuilderDelegate)
        whenever(mockBuilderDelegate.eventListener(any()))
            .thenReturn(mockBuilderDelegate)
        whenever(mockOkHttpClient.eventListenerFactory).thenReturn(EventListener.Factory { EventListener.NONE })
        whenever(mockBuilderDelegate.build()).thenReturn(mockOkHttpClient)
    }

    @Test
    fun `M add RUM interceptor W build() { rumInstrumentationConfiguration }`() {
        // Given
        val testedPlugin = OkHttpIntegrationPlugin(
            mockBuilderDelegate,
            rumConfiguration = RumNetworkInstrumentationConfiguration(),
            apmConfiguration = null
        )

        // When
        testedPlugin.build()

        // Then
        argumentCaptor<Interceptor> {
            verify(mockBuilderDelegate).addInterceptor(capture())
            assertThat(firstValue).isInstanceOf(RumInstrumentationOkHttpAdapter::class.java)
        }
    }

    @Test
    fun `M set event listener factory W build() { rumInstrumentationConfiguration }`() {
        // Given
        val testedPlugin = OkHttpIntegrationPlugin(
            mockBuilderDelegate,
            rumConfiguration = RumNetworkInstrumentationConfiguration(),
            apmConfiguration = null
        )

        // When
        testedPlugin.build()

        // Then
        argumentCaptor<EventListener.Factory> {
            verify(mockBuilderDelegate).eventListenerFactory(capture())
            assertThat(firstValue).isInstanceOf(RegistryTrackingEventListener.Factory::class.java)
        }
    }

    @Test
    fun `M add APM application interceptor W build() { apmInstrumentationConfiguration, default scope }`() {
        // Given
        val apmConfig = ApmNetworkInstrumentationConfiguration("example.com")
        val testedPlugin = OkHttpIntegrationPlugin(
            mockBuilderDelegate,
            rumConfiguration = null,
            apmConfiguration = apmConfig
        )

        // When
        testedPlugin.build()

        // Then
        argumentCaptor<Interceptor> {
            verify(mockBuilderDelegate).addInterceptor(capture())
            assertThat(firstValue).isInstanceOf(ApmInstrumentationOkHttpAdapter::class.java)
        }
    }

    @Test
    fun `M add APM application interceptor W build() { headerPropagationOnly + no rum }`() {
        // Given
        val apmConfig = ApmNetworkInstrumentationConfiguration("example.com")
            .setHeaderPropagationOnly()
        val testedPlugin = OkHttpIntegrationPlugin(
            mockBuilderDelegate,
            rumConfiguration = null,
            apmConfiguration = apmConfig
        )

        // When
        testedPlugin.build()

        // Then
        argumentCaptor<Interceptor> {
            verify(mockBuilderDelegate).addInterceptor(capture())
            assertThat(firstValue).isInstanceOf(ApmInstrumentationOkHttpAdapter::class.java)
        }
        verify(mockBuilderDelegate, never()).addNetworkInterceptor(any<Interceptor>())
    }

    @Test
    fun `M add APM network interceptor W build() { apmInstrumentationConfiguration, ALL scope }`() {
        // Given
        val apmConfig = ApmNetworkInstrumentationConfiguration("example.com")
            .setTraceScope(ApmNetworkTracingScope.ALL)

        val testedPlugin = OkHttpIntegrationPlugin(
            mockBuilderDelegate,
            rumConfiguration = null,
            apmConfiguration = apmConfig
        )

        // When
        testedPlugin.build()

        // Then
        argumentCaptor<Interceptor> {
            verify(mockBuilderDelegate).addNetworkInterceptor(capture())
            assertThat(firstValue).isInstanceOf(ApmInstrumentationOkHttpAdapter::class.java)
        }
    }

    @Test
    fun `M add both interceptors W build() { rum + apm configurations }`() {
        // Given
        val apmConfig = ApmNetworkInstrumentationConfiguration("example.com")
        val testedPlugin = OkHttpIntegrationPlugin(
            mockBuilderDelegate,
            rumConfiguration = RumNetworkInstrumentationConfiguration(),
            apmConfiguration = apmConfig
        )

        // When
        testedPlugin.build()

        // Then
        argumentCaptor<Interceptor> {
            verify(mockBuilderDelegate, times(2)).addInterceptor(capture())
            assertThat(firstValue).isInstanceOf(RumInstrumentationOkHttpAdapter::class.java)
            assertThat(secondValue).isInstanceOf(ApmInstrumentationOkHttpAdapter::class.java)
        }
        argumentCaptor<EventListener.Factory> {
            verify(mockBuilderDelegate).eventListenerFactory(capture())
            assertThat(firstValue).isInstanceOf(RegistryTrackingEventListener.Factory::class.java)
        }
    }

    @Test
    fun `M return plain client and not add interceptors W build() { both configs null }`() {
        // Given
        val testedPlugin = OkHttpIntegrationPlugin(
            mockBuilderDelegate,
            rumConfiguration = null,
            apmConfiguration = null
        )

        // When
        val result = testedPlugin.build()

        // Then
        assertThat(result).isSameAs(mockOkHttpClient)
        verify(mockBuilderDelegate).build()
        verify(mockBuilderDelegate, never()).addInterceptor(any<Interceptor>())
        verify(mockBuilderDelegate, never()).addNetworkInterceptor(any<Interceptor>())
        verify(mockBuilderDelegate, never()).eventListenerFactory(any())
    }

    @Test
    fun `M preserve preconfigured event listener W build() { builder eventListener() }`() {
        // Given
        val mockUserEventListener = mock<EventListener>()
        val mockCall = mock<Call>()
        val fakeRequest = Request.Builder().url("https://example.com/").build()
        whenever(mockCall.request()).thenReturn(fakeRequest)
        val testedPlugin = OkHttpIntegrationPlugin(
            OkHttpClient.Builder().eventListener(mockUserEventListener),
            rumConfiguration = null,
            apmConfiguration = null
        )

        // When
        val listener = testedPlugin.build().eventListenerFactory.create(mockCall)
        listener.callStart(mockCall)

        // Then
        verify(mockUserEventListener).callStart(mockCall)
    }

    @Test
    fun `M preserve preconfigured event listener factory W build() { builder eventListenerFactory() }`() {
        // Given
        val mockUserFactory = mock<EventListener.Factory>()
        val mockUserListener = mock<EventListener>()
        val mockCall = mock<Call>()
        val fakeRequest = Request.Builder().url("https://example.com/").build()
        whenever(mockCall.request()).thenReturn(fakeRequest)
        whenever(mockUserFactory.create(mockCall)).thenReturn(mockUserListener)

        val testedPlugin = OkHttpIntegrationPlugin(
            OkHttpClient.Builder().eventListenerFactory(mockUserFactory),
            rumConfiguration = null,
            apmConfiguration = null
        )

        // When
        val listener = testedPlugin.build().eventListenerFactory.create(mockCall)
        listener.callStart(mockCall)

        // Then
        verify(mockUserFactory).create(mockCall)
        verify(mockUserListener).callStart(mockCall)
    }

    @Test
    fun `M set composite event listener factory W build() { no rum + no userEventListenerFactory }`() {
        // Given
        val apmConfig = ApmNetworkInstrumentationConfiguration("example.com")
        val testedPlugin = OkHttpIntegrationPlugin(
            mockBuilderDelegate,
            rumConfiguration = null,
            apmConfiguration = apmConfig
        )

        // When
        testedPlugin.build()

        // Then
        argumentCaptor<EventListener.Factory> {
            verify(mockBuilderDelegate).eventListenerFactory(capture())
            assertThat(firstValue).isInstanceOf(RegistryTrackingEventListener.Factory::class.java)
        }
    }

    @Test
    fun `M return OkHttpClient W build()`() {
        // Given
        val testedPlugin = OkHttpIntegrationPlugin(
            mockBuilderDelegate,
            rumConfiguration = null,
            apmConfiguration = null
        )

        // When
        val result = testedPlugin.build()

        // Then
        assertThat(result).isSameAs(mockOkHttpClient)
    }

    @Test
    fun `M return same instance W build() twice`() {
        // Given
        val testedPlugin = OkHttpIntegrationPlugin(
            mockBuilderDelegate,
            rumConfiguration = null,
            apmConfiguration = null
        )

        // When
        val firstResult = testedPlugin.build()
        val secondResult = testedPlugin.build()

        // Then
        assertThat(secondResult).isSameAs(firstResult)
        verify(mockBuilderDelegate).build()
    }

    companion object {
        val datadogCore = DatadogSingletonTestConfiguration()

        @TestConfigurationsProvider
        @JvmStatic
        fun getTestConfigurations(): List<TestConfiguration> {
            return listOf(datadogCore)
        }
    }
}
