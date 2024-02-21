/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.webview.internal

import com.datadog.android.utils.forge.Configurator
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
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.quality.Strictness
import java.net.URL

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class DatadogEventBridgeTest {

    lateinit var testedDatadogEventBridge: DatadogEventBridge

    @Mock
    lateinit var mockWebViewEventConsumer: MixedWebViewEventConsumer

    @StringForgery
    lateinit var fakePrivacyLevel: String

    @BeforeEach
    fun `set up`() {
        testedDatadogEventBridge = DatadogEventBridge(
            mockWebViewEventConsumer,
            emptyList(),
            fakePrivacyLevel
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
        testedDatadogEventBridge = DatadogEventBridge(mock(), hosts, fakePrivacyLevel)

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
        testedDatadogEventBridge = DatadogEventBridge(
            mockWebViewEventConsumer,
            hosts,
            fakePrivacyLevel
        )

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
        testedDatadogEventBridge = DatadogEventBridge(
            mockWebViewEventConsumer,
            hosts,
            fakePrivacyLevel
        )

        // When
        val allowedWebViewHosts = testedDatadogEventBridge.getAllowedWebViewHosts()

        // Then
        assertThat(allowedWebViewHosts).isEqualTo(expectedHosts)
    }

    @Test
    fun `M return the provided privacy level W getPrivacyLevel()`() {
        // When
        val privacyLevel = testedDatadogEventBridge.getPrivacyLevel()

        // Then
        assertThat(privacyLevel).isEqualTo(fakePrivacyLevel)
    }

    @Test
    fun `M return the supported capabilities W getCapabilities()`() {
        // Given
        val expectedCapabilities = "[\"records\"]"

        // When
        val capabilities = testedDatadogEventBridge.getCapabilities()

        // Then
        assertThat(capabilities).isEqualTo(expectedCapabilities)
    }
}
