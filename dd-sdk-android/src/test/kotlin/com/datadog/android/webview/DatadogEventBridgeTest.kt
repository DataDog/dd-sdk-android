/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.webview

import com.datadog.android.core.internal.CoreFeature
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.webview.internal.MixedWebViewEventConsumer
import com.datadog.android.webview.internal.log.WebViewLogEventConsumer
import com.datadog.android.webview.internal.rum.WebViewRumEventConsumer
import com.nhaarman.mockitokotlin2.verify
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import java.net.URL
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class),
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class DatadogEventBridgeTest {

    lateinit var testedDatadogEventBridge: DatadogEventBridge

    @Mock
    lateinit var mockWebViewEventConsumer: MixedWebViewEventConsumer

    @BeforeEach
    fun `set up`() {
        testedDatadogEventBridge = DatadogEventBridge(mockWebViewEventConsumer, emptyList())
    }

    @Test
    fun `M create a default WebEventConsumer W init()`() {
        // When
        val bridge = DatadogEventBridge()

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
        CoreFeature.webViewTrackingHosts = fakeHosts

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
        CoreFeature.webViewTrackingHosts = emptyList()
        testedDatadogEventBridge = DatadogEventBridge(fakeHosts)

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
        CoreFeature.webViewTrackingHosts = fakeGlobalHosts
        testedDatadogEventBridge = DatadogEventBridge(fakeLocalHosts)

        // When
        val allowedWebViewHosts = testedDatadogEventBridge.getAllowedWebViewHosts()

        // Then
        assertThat(allowedWebViewHosts).isEqualTo(expectedHosts)
    }
}
