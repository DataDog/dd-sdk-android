/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.okhttp

import com.datadog.android.okhttp.internal.ApmInstrumentationOkHttpAdapter
import com.datadog.android.okhttp.internal.CompositeEventListener
import com.datadog.android.okhttp.internal.RumInstrumentationOkHttpAdapter
import com.datadog.android.rum.ExperimentalRumApi
import com.datadog.android.rum.configuration.RumNetworkInstrumentationConfiguration
import com.datadog.android.trace.ApmNetworkInstrumentationConfiguration
import com.datadog.android.trace.ExperimentalTraceApi
import com.datadog.tools.unit.forge.BaseConfigurator
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import okhttp3.EventListener
import okhttp3.Interceptor
import okhttp3.OkHttpClient
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
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(BaseConfigurator::class)
@OptIn(ExperimentalRumApi::class, ExperimentalTraceApi::class)
internal class OkHttpIntegrationPluginTest {

    @Mock
    private lateinit var mockBuilderDelegate: OkHttpClient.Builder

    @Mock
    private lateinit var mockOkHttpClient: OkHttpClient

    @BeforeEach
    fun `set up`() {
        whenever(mockBuilderDelegate.addInterceptor(any<Interceptor>()))
            .thenReturn(mockBuilderDelegate)
        whenever(mockBuilderDelegate.addNetworkInterceptor(any<Interceptor>()))
            .thenReturn(mockBuilderDelegate)
        whenever(mockBuilderDelegate.eventListenerFactory(any()))
            .thenReturn(mockBuilderDelegate)
        whenever(mockBuilderDelegate.build()).thenReturn(mockOkHttpClient)
    }

    @Test
    fun `M add RUM interceptor W build() { rumInstrumentationConfiguration }`() {
        // Given
        val testedPlugin = OkHttpIntegrationPlugin(
            mockBuilderDelegate,
            rumInstrumentationConfiguration = RumNetworkInstrumentationConfiguration(),
            apmInstrumentationConfiguration = null
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
            rumInstrumentationConfiguration = RumNetworkInstrumentationConfiguration(),
            apmInstrumentationConfiguration = null
        )

        // When
        testedPlugin.build()

        // Then
        argumentCaptor<EventListener.Factory> {
            verify(mockBuilderDelegate).eventListenerFactory(capture())
            assertThat(firstValue).isInstanceOf(CompositeEventListener.Factory::class.java)
        }
    }

    @Test
    fun `M add APM network interceptor W build() { apmInstrumentationConfiguration }`() {
        // Given
        val apmConfig = ApmNetworkInstrumentationConfiguration(listOf("example.com"))
        val testedPlugin = OkHttpIntegrationPlugin(
            mockBuilderDelegate,
            rumInstrumentationConfiguration = null,
            apmInstrumentationConfiguration = apmConfig
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
        val apmConfig = ApmNetworkInstrumentationConfiguration(listOf("example.com"))
        val testedPlugin = OkHttpIntegrationPlugin(
            mockBuilderDelegate,
            rumInstrumentationConfiguration = RumNetworkInstrumentationConfiguration(),
            apmInstrumentationConfiguration = apmConfig
        )

        // When
        testedPlugin.build()

        // Then
        argumentCaptor<Interceptor> {
            verify(mockBuilderDelegate, times(2)).addInterceptor(capture())
            assertThat(firstValue).isInstanceOf(RumInstrumentationOkHttpAdapter::class.java)
            assertThat(secondValue).isInstanceOf(ApmInstrumentationOkHttpAdapter::class.java)
        }
        argumentCaptor<Interceptor> {
            verify(mockBuilderDelegate).addNetworkInterceptor(capture())
            assertThat(firstValue).isInstanceOf(ApmInstrumentationOkHttpAdapter::class.java)
        }
        argumentCaptor<EventListener.Factory> {
            verify(mockBuilderDelegate).eventListenerFactory(capture())
            assertThat(firstValue).isInstanceOf(CompositeEventListener.Factory::class.java)
        }
    }

    @Test
    fun `M not add interceptors W build() { no instrumentation }`() {
        // Given
        val testedPlugin = OkHttpIntegrationPlugin(
            mockBuilderDelegate,
            rumInstrumentationConfiguration = null,
            apmInstrumentationConfiguration = null
        )

        // When
        testedPlugin.build()

        // Then
        argumentCaptor<EventListener.Factory> {
            verify(mockBuilderDelegate).eventListenerFactory(capture())
            assertThat(firstValue).isInstanceOf(CompositeEventListener.Factory::class.java)
        }
        verify(mockBuilderDelegate).build()
        verifyNoInteractions(mockOkHttpClient)
    }

    @Test
    fun `M use composite factory W build() { rumInstrumentationConfiguration + userEventListenerFactory }`() {
        // Given
        val userFactory = mock<EventListener.Factory>()
        val testedPlugin = OkHttpIntegrationPlugin(
            mockBuilderDelegate,
            rumInstrumentationConfiguration = RumNetworkInstrumentationConfiguration(),
            apmInstrumentationConfiguration = null
        )

        // When
        testedPlugin.eventListenerFactory(userFactory)
        testedPlugin.build()

        // Then
        argumentCaptor<EventListener.Factory> {
            verify(mockBuilderDelegate).eventListenerFactory(capture())
            assertThat(firstValue).isInstanceOf(CompositeEventListener.Factory::class.java)
        }
    }

    @Test
    fun `M use composite factory W build() { no rum + userEventListenerFactory }`() {
        // Given
        val userFactory = mock<EventListener.Factory>()
        val testedPlugin = OkHttpIntegrationPlugin(
            mockBuilderDelegate,
            rumInstrumentationConfiguration = null,
            apmInstrumentationConfiguration = null
        )

        // When
        testedPlugin.eventListenerFactory(userFactory)
        testedPlugin.build()

        // Then
        argumentCaptor<EventListener.Factory> {
            verify(mockBuilderDelegate).eventListenerFactory(capture())
            assertThat(firstValue).isInstanceOf(CompositeEventListener.Factory::class.java)
        }
    }

    @Test
    fun `M set composite event listener factory W build() { no rum + no userEventListenerFactory }`() {
        // Given
        val apmConfig = ApmNetworkInstrumentationConfiguration(listOf("example.com"))
        val testedPlugin = OkHttpIntegrationPlugin(
            mockBuilderDelegate,
            rumInstrumentationConfiguration = null,
            apmInstrumentationConfiguration = apmConfig
        )

        // When
        testedPlugin.build()

        // Then
        argumentCaptor<EventListener.Factory> {
            verify(mockBuilderDelegate).eventListenerFactory(capture())
            assertThat(firstValue).isInstanceOf(CompositeEventListener.Factory::class.java)
        }
    }

    @Test
    fun `M return self W eventListener()`() {
        // Given
        val testedPlugin = OkHttpIntegrationPlugin(
            mockBuilderDelegate,
            rumInstrumentationConfiguration = null,
            apmInstrumentationConfiguration = null
        )

        // When
        val result = testedPlugin.eventListener(mock())

        // Then
        assertThat(result).isSameAs(testedPlugin)
    }

    @Test
    fun `M return self W eventListenerFactory()`() {
        // Given
        val testedPlugin = OkHttpIntegrationPlugin(
            mockBuilderDelegate,
            rumInstrumentationConfiguration = null,
            apmInstrumentationConfiguration = null
        )

        // When
        val result = testedPlugin.eventListenerFactory(mock())

        // Then
        assertThat(result).isSameAs(testedPlugin)
    }

    @Test
    fun `M return OkHttpClient W build()`() {
        // Given
        val testedPlugin = OkHttpIntegrationPlugin(
            mockBuilderDelegate,
            rumInstrumentationConfiguration = null,
            apmInstrumentationConfiguration = null
        )

        // When
        val result = testedPlugin.build()

        // Then
        assertThat(result).isSameAs(mockOkHttpClient)
    }
}
