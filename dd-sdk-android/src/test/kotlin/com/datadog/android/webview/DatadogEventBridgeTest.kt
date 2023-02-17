/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.webview

import android.content.Context
import android.webkit.WebSettings
import android.webkit.WebView
import com.datadog.android.utils.config.ApplicationContextTestConfiguration
import com.datadog.android.utils.config.CoreFeatureTestConfiguration
import com.datadog.android.utils.config.InternalLoggerTestConfiguration
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.v2.api.FeatureScope
import com.datadog.android.v2.api.InternalLogger
import com.datadog.android.v2.core.DatadogCore
import com.datadog.android.webview.internal.MixedWebViewEventConsumer
import com.datadog.android.webview.internal.log.WebViewLogEventConsumer
import com.datadog.android.webview.internal.log.WebViewLogsFeature
import com.datadog.android.webview.internal.rum.WebViewRumEventConsumer
import com.datadog.android.webview.internal.rum.WebViewRumFeature
import com.datadog.tools.unit.annotations.TestConfigurationsProvider
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import com.datadog.tools.unit.extensions.config.TestConfiguration
import com.nhaarman.mockitokotlin2.argThat
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
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
    lateinit var mockCore: DatadogCore

    @Mock
    lateinit var mockWebViewRumFeatureScope: FeatureScope

    @Mock
    lateinit var mockWebViewLogsFeatureScope: FeatureScope

    @Mock
    lateinit var mockWebViewRumFeature: WebViewRumFeature

    @Mock
    lateinit var mockWebViewLogsFeature: WebViewLogsFeature

    @BeforeEach
    fun `set up`() {
        whenever(
            mockCore.getFeature(WebViewRumFeature.WEB_RUM_FEATURE_NAME)
        ) doReturn mockWebViewRumFeatureScope
        whenever(
            mockCore.getFeature(WebViewLogsFeature.WEB_LOGS_FEATURE_NAME)
        ) doReturn mockWebViewLogsFeatureScope

        whenever(
            mockWebViewRumFeatureScope.unwrap<WebViewRumFeature>()
        ) doReturn mockWebViewRumFeature
        whenever(
            mockWebViewLogsFeatureScope.unwrap<WebViewLogsFeature>()
        ) doReturn mockWebViewLogsFeature

        whenever(mockWebViewRumFeature.dataWriter) doReturn mock()
        whenever(mockWebViewLogsFeature.dataWriter) doReturn mock()

        whenever(mockCore.coreFeature) doReturn coreFeature.mockInstance

        testedDatadogEventBridge = DatadogEventBridge(
            mockCore,
            mockWebViewEventConsumer,
            emptyList()
        )
    }

    @Test
    fun `M create a default WebEventConsumer W init()`() {
        // When
        val bridge = DatadogEventBridge(mockCore)

        // Then
        val consumer = bridge.webViewEventConsumer
        assertThat(consumer).isInstanceOf(MixedWebViewEventConsumer::class.java)
        val mixedConsumer = consumer as MixedWebViewEventConsumer
        assertThat(mixedConsumer.logsEventConsumer)
            .isInstanceOf(WebViewLogEventConsumer::class.java)
        assertThat(mixedConsumer.rumEventConsumer)
            .isInstanceOf(WebViewRumEventConsumer::class.java)
    }

    @Test
    fun `M delegate to WebEventConsumer W send()`(@StringForgery fakeEvent: String) {
        // When
        testedDatadogEventBridge.send(fakeEvent)

        // Then
        verify(mockWebViewEventConsumer).consume(fakeEvent)
    }

    @Test
    fun `M return the webViewTrackingHosts as JsonArray W getAllowedWebViewHosts() { global }`(
        @Forgery fakeUrls: List<URL>
    ) {
        // Given
        val fakeHosts = fakeUrls.map { it.host }
        val expectedHosts = fakeHosts.joinToString(",", prefix = "[", postfix = "]") { "\"$it\"" }
        whenever(coreFeature.mockInstance.webViewTrackingHosts) doReturn fakeHosts

        // When
        val allowedWebViewHosts = testedDatadogEventBridge.getAllowedWebViewHosts()

        // Then
        assertThat(allowedWebViewHosts).isEqualTo(expectedHosts)
    }

    @Test
    fun `M return the webViewTrackingHosts as JsonArray W getAllowedWebViewHosts() { local }`(
        @Forgery fakeUrls: List<URL>
    ) {
        // Given
        val fakeHosts = fakeUrls.map { it.host }
        val expectedHosts = fakeHosts.joinToString(",", prefix = "[", postfix = "]") { "\"$it\"" }
        whenever(coreFeature.mockInstance.webViewTrackingHosts) doReturn emptyList()
        testedDatadogEventBridge = DatadogEventBridge(mockCore, fakeHosts)

        // When
        val allowedWebViewHosts = testedDatadogEventBridge.getAllowedWebViewHosts()

        // Then
        assertThat(allowedWebViewHosts).isEqualTo(expectedHosts)
    }

    @Test
    fun `M return the webViewTrackingHosts as JsonArray W getAllowedWebViewHosts() { mixed }`(
        @Forgery fakeGlobalUrls: List<URL>,
        @Forgery fakeLocalUrls: List<URL>
    ) {
        // Given
        val fakeLocalHosts = fakeLocalUrls.map { it.host }
        val fakeGlobalHosts = fakeGlobalUrls.map { it.host }
        val expectedHosts = (fakeLocalHosts + fakeGlobalHosts)
            .joinToString(",", prefix = "[", postfix = "]") { "\"$it\"" }
        whenever(coreFeature.mockInstance.webViewTrackingHosts) doReturn fakeGlobalHosts
        testedDatadogEventBridge = DatadogEventBridge(mockCore, fakeLocalHosts)

        // When
        val allowedWebViewHosts = testedDatadogEventBridge.getAllowedWebViewHosts()

        // Then
        assertThat(allowedWebViewHosts).isEqualTo(expectedHosts)
    }

    @Test
    fun `M attach the bridge W setup`() {
        // Given

        val mockSettings: WebSettings = mock {
            whenever(it.javaScriptEnabled).thenReturn(true)
        }
        val mockWebView: WebView = mock {
            whenever(it.settings).thenReturn(mockSettings)
        }

        // When
        DatadogEventBridge.setup(mockCore, mockWebView)

        // Then
        verify(mockWebView).addJavascriptInterface(
            argThat { this is DatadogEventBridge },
            eq(DatadogEventBridge.DATADOG_EVENT_BRIDGE_NAME)
        )
    }

    @Test
    fun `M attach the bridge and send a warn log W setup { javascript not enabled }`() {
        // Given

        val mockSettings: WebSettings = mock {
            whenever(it.javaScriptEnabled).thenReturn(false)
        }
        val mockWebView: WebView = mock {
            whenever(it.settings).thenReturn(mockSettings)
        }

        // When
        DatadogEventBridge.setup(mockCore, mockWebView)

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
        val appContext = ApplicationContextTestConfiguration(Context::class.java)
        val coreFeature = CoreFeatureTestConfiguration(appContext)

        @TestConfigurationsProvider
        @JvmStatic
        fun getTestConfigurations(): List<TestConfiguration> {
            return listOf(logger, appContext, coreFeature)
        }
    }
}
