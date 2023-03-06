/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.webview

import android.webkit.WebSettings
import android.webkit.WebView
import com.datadog.android.utils.config.InternalLoggerTestConfiguration
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.v2.api.Feature
import com.datadog.android.v2.api.FeatureScope
import com.datadog.android.v2.api.InternalLogger
import com.datadog.android.v2.api.RequestFactory
import com.datadog.android.v2.api.SdkCore
import com.datadog.android.v2.api.StorageBackedFeature
import com.datadog.android.webview.internal.MixedWebViewEventConsumer
import com.datadog.android.webview.internal.log.WebViewLogEventConsumer
import com.datadog.android.webview.internal.log.WebViewLogsFeature
import com.datadog.android.webview.internal.rum.WebViewRumEventConsumer
import com.datadog.android.webview.internal.rum.WebViewRumFeature
import com.datadog.android.webview.internal.storage.NoOpDataWriter
import com.datadog.tools.unit.annotations.TestConfigurationsProvider
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import com.datadog.tools.unit.extensions.config.TestConfiguration
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argThat
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.doAnswer
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness
import java.net.URL

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class),
    ExtendWith(TestConfigurationExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class DatadogEventBridgeTest {

    lateinit var testedDatadogEventBridge: DatadogEventBridge

    @Mock
    lateinit var mockWebViewEventConsumer: MixedWebViewEventConsumer

    @Mock
    lateinit var mockCore: SdkCore

    @Mock
    lateinit var mockRumFeatureScope: FeatureScope

    @Mock
    lateinit var mockLogsFeatureScope: FeatureScope

    @Mock
    lateinit var mockRumFeature: StorageBackedFeature

    @Mock
    lateinit var mockLogsFeature: StorageBackedFeature

    @Mock
    lateinit var mockRumRequestFactory: RequestFactory

    @Mock
    lateinit var mockLogsRequestFactory: RequestFactory

    @BeforeEach
    fun `set up`() {
        whenever(
            mockCore.getFeature(Feature.RUM_FEATURE_NAME)
        ) doReturn mockRumFeatureScope
        whenever(
            mockCore.getFeature(Feature.LOGS_FEATURE_NAME)
        ) doReturn mockLogsFeatureScope

        whenever(
            mockRumFeatureScope.unwrap<StorageBackedFeature>()
        ) doReturn mockRumFeature
        whenever(
            mockLogsFeatureScope.unwrap<StorageBackedFeature>()
        ) doReturn mockLogsFeature

        whenever(mockRumFeature.requestFactory) doReturn mockRumRequestFactory
        whenever(mockLogsFeature.requestFactory) doReturn mockLogsRequestFactory

        testedDatadogEventBridge = DatadogEventBridge(
            mockWebViewEventConsumer,
            emptyList()
        )
    }

    @Test
    fun `M create a default WebEventConsumer W init()`(
        @Forgery fakeUrls: List<URL>
    ) {
        // Given
        val fakeHosts = fakeUrls.map { it.host }
        whenever(mockCore.registerFeature(any())) doAnswer {
            val feature = it.getArgument<Feature>(0)
            feature.onInitialize(mockCore, mock())
        }

        // When
        val bridge = DatadogEventBridge(mockCore, fakeHosts)

        // Then
        val consumer = bridge.webViewEventConsumer
        assertThat(consumer).isInstanceOf(MixedWebViewEventConsumer::class.java)
        val mixedConsumer = consumer as MixedWebViewEventConsumer
        assertThat(mixedConsumer.logsEventConsumer)
            .isInstanceOf(WebViewLogEventConsumer::class.java)
        assertThat(mixedConsumer.rumEventConsumer)
            .isInstanceOf(WebViewRumEventConsumer::class.java)

        argumentCaptor<Feature> {
            verify(mockCore, times(2)).registerFeature(capture())

            val webViewRumFeature = firstValue
            val webViewLogsFeature = secondValue

            assertThat((webViewRumFeature as WebViewRumFeature).requestFactory)
                .isSameAs(mockRumRequestFactory)
            assertThat((webViewLogsFeature as WebViewLogsFeature).requestFactory)
                .isSameAs(mockLogsRequestFactory)
        }
    }

    @Test
    fun `M create a default WebEventConsumer W init() {RUM feature is not registered}`(
        @Forgery fakeUrls: List<URL>
    ) {
        // Given
        val fakeHosts = fakeUrls.map { it.host }
        whenever(mockCore.getFeature(Feature.RUM_FEATURE_NAME)) doReturn null
        whenever(mockCore.registerFeature(any())) doAnswer {
            val feature = it.getArgument<Feature>(0)
            feature.onInitialize(mockCore, mock())
        }

        // When
        val bridge = DatadogEventBridge(mockCore, fakeHosts)

        // Then
        val consumer = bridge.webViewEventConsumer
        assertThat(consumer).isInstanceOf(MixedWebViewEventConsumer::class.java)
        val mixedConsumer = consumer as MixedWebViewEventConsumer
        assertThat(mixedConsumer.logsEventConsumer)
            .isInstanceOf(WebViewLogEventConsumer::class.java)
        assertThat((mixedConsumer.logsEventConsumer as WebViewLogEventConsumer).userLogsWriter)
            .isNotInstanceOf(NoOpDataWriter::class.java)
        assertThat(mixedConsumer.rumEventConsumer)
            .isInstanceOf(WebViewRumEventConsumer::class.java)
        assertThat((mixedConsumer.rumEventConsumer as WebViewRumEventConsumer).dataWriter)
            .isInstanceOf(NoOpDataWriter::class.java)

        argumentCaptor<Feature> {
            verify(mockCore, times(1)).registerFeature(capture())

            val webViewLogsFeature = firstValue

            assertThat((webViewLogsFeature as WebViewLogsFeature).requestFactory)
                .isSameAs(mockLogsRequestFactory)
        }

        verify(logger.mockInternalLogger)
            .log(
                InternalLogger.Level.INFO,
                InternalLogger.Target.USER,
                DatadogEventBridge.RUM_FEATURE_MISSING_INFO
            )
    }

    @Test
    fun `M create a default WebEventConsumer W init() {Logs feature is not registered}`(
        @Forgery fakeUrls: List<URL>
    ) {
        // Given
        val fakeHosts = fakeUrls.map { it.host }
        whenever(mockCore.getFeature(Feature.LOGS_FEATURE_NAME)) doReturn null
        whenever(mockCore.registerFeature(any())) doAnswer {
            val feature = it.getArgument<Feature>(0)
            feature.onInitialize(mockCore, mock())
        }

        // When
        val bridge = DatadogEventBridge(mockCore, fakeHosts)

        // Then
        val consumer = bridge.webViewEventConsumer
        assertThat(consumer).isInstanceOf(MixedWebViewEventConsumer::class.java)
        val mixedConsumer = consumer as MixedWebViewEventConsumer
        assertThat(mixedConsumer.logsEventConsumer)
            .isInstanceOf(WebViewLogEventConsumer::class.java)
        assertThat((mixedConsumer.logsEventConsumer as WebViewLogEventConsumer).userLogsWriter)
            .isInstanceOf(NoOpDataWriter::class.java)
        assertThat(mixedConsumer.rumEventConsumer)
            .isInstanceOf(WebViewRumEventConsumer::class.java)
        assertThat((mixedConsumer.rumEventConsumer as WebViewRumEventConsumer).dataWriter)
            .isNotInstanceOf(NoOpDataWriter::class.java)

        argumentCaptor<Feature> {
            verify(mockCore, times(1)).registerFeature(capture())

            val webViewRumFeature = firstValue

            assertThat((webViewRumFeature as WebViewRumFeature).requestFactory)
                .isSameAs(mockRumRequestFactory)
        }

        verify(logger.mockInternalLogger)
            .log(
                InternalLogger.Level.INFO,
                InternalLogger.Target.USER,
                DatadogEventBridge.LOGS_FEATURE_MISSING_INFO
            )
    }

    @Test
    fun `M delegate to WebEventConsumer W send()`(@StringForgery fakeEvent: String) {
        // When
        testedDatadogEventBridge.send(fakeEvent)

        // Then
        verify(mockWebViewEventConsumer).consume(fakeEvent)
    }

    @Test
    fun `M return sanitized webViewTrackingHosts W getAllowedWebViewHosts() { allow IP addresses }`(
        @StringForgery(
            regex = "(([0-9]|[1-9][0-9]|1[0-9]{2}|2[0-4][0-9]|25[0-5])\\.){3}" +
                "([0-9]|[1-9][0-9]|1[0-9]{2}|2[0-4][0-9]|25[0-5])"
        ) hosts: List<String>
    ) {
        // Given
        val expectedHosts = hosts.joinToString(",", prefix = "[", postfix = "]") { "\"$it\"" }
        testedDatadogEventBridge = DatadogEventBridge(mockCore, hosts)

        // When
        val allowedWebViewHosts = testedDatadogEventBridge.getAllowedWebViewHosts()

        // Then
        assertThat(allowedWebViewHosts).isEqualTo(expectedHosts)
    }

    @Test
    fun `M return sanitized webViewTrackingHosts W getAllowedWebViewHosts() { allow host names }`(
        @StringForgery(
            regex = "(([a-zA-Z0-9]|[a-zA-Z0-9][a-zA-Z0-9-]*[a-zA-Z0-9])\\.)+" +
                "([A-Za-z]|[A-Za-z][A-Za-z0-9-]*[A-Za-z0-9])"
        ) hosts: List<String>
    ) {
        // Given
        val expectedHosts = hosts.joinToString(",", prefix = "[", postfix = "]") { "\"$it\"" }
        testedDatadogEventBridge = DatadogEventBridge(mockCore, hosts)

        // When
        val allowedWebViewHosts = testedDatadogEventBridge.getAllowedWebViewHosts()

        // Then
        assertThat(allowedWebViewHosts).isEqualTo(expectedHosts)
    }

    @Test
    fun `M return sanitized webViewTrackingHosts W getAllowedWebViewHosts() { allow URLs }`(
        @StringForgery(
            regex = "(https|http)://([a-z][a-z0-9-]{3,9}\\.){1,4}[a-z][a-z0-9]{2,3}"
        ) hosts: List<String>
    ) {
        // Given
        val expectedHosts = hosts.map { URL(it).host }
            .joinToString(",", prefix = "[", postfix = "]") { "\"$it\"" }
        testedDatadogEventBridge = DatadogEventBridge(mockCore, hosts)

        // When
        val allowedWebViewHosts = testedDatadogEventBridge.getAllowedWebViewHosts()

        // Then
        assertThat(allowedWebViewHosts).isEqualTo(expectedHosts)
    }

    @Test
    fun `M attach the bridge W setup`(@Forgery fakeUrls: List<URL>) {
        // Given
        val fakeHosts = fakeUrls.map { it.host }
        val mockSettings: WebSettings = mock {
            whenever(it.javaScriptEnabled).thenReturn(true)
        }
        val mockWebView: WebView = mock {
            whenever(it.settings).thenReturn(mockSettings)
        }

        // When
        DatadogEventBridge.setup(mockCore, mockWebView, fakeHosts)

        // Then
        verify(mockWebView).addJavascriptInterface(
            argThat { this is DatadogEventBridge },
            eq(DatadogEventBridge.DATADOG_EVENT_BRIDGE_NAME)
        )
    }

    @Test
    fun `M attach the bridge and send a warn log W setup { javascript not enabled }`(
        @Forgery fakeUrls: List<URL>
    ) {
        // Given
        val fakeHosts = fakeUrls.map { it.host }
        val mockSettings: WebSettings = mock {
            whenever(it.javaScriptEnabled).thenReturn(false)
        }
        val mockWebView: WebView = mock {
            whenever(it.settings).thenReturn(mockSettings)
        }

        // When
        DatadogEventBridge.setup(mockCore, mockWebView, fakeHosts)

        // Then
        verify(mockWebView).addJavascriptInterface(
            argThat { this is DatadogEventBridge },
            eq(DatadogEventBridge.DATADOG_EVENT_BRIDGE_NAME)
        )
        verify(logger.mockInternalLogger).log(
            InternalLogger.Level.WARN,
            InternalLogger.Target.USER,
            DatadogEventBridge.JAVA_SCRIPT_NOT_ENABLED_WARNING_MESSAGE
        )
    }

    companion object {
        val logger = InternalLoggerTestConfiguration()

        @TestConfigurationsProvider
        @JvmStatic
        fun getTestConfigurations(): List<TestConfiguration> {
            return listOf(logger)
        }
    }
}
