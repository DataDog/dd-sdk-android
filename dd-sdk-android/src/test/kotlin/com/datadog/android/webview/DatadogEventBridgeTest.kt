/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.webview

import com.datadog.android.core.internal.CoreFeature
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.webview.internal.WebEventConsumer
import com.datadog.tools.unit.setFieldValue
import com.google.gson.JsonArray
import com.nhaarman.mockitokotlin2.verify
import fr.xgouchet.elmyr.Forge
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
    lateinit var mockWebEventConsumer: WebEventConsumer

    @BeforeEach
    fun `set up`() {
        testedDatadogEventBridge = DatadogEventBridge()
        testedDatadogEventBridge.setFieldValue("webEventConsumer", mockWebEventConsumer)
    }

    @Test
    fun `M delegate to WebEventConsumer W send()`(@StringForgery fakeEvent: String) {
        // When
        testedDatadogEventBridge.send(fakeEvent)

        // Then
        verify(mockWebEventConsumer).consume(fakeEvent)
    }

    @Test
    fun `M return the webViewTrackingHosts as JsonArray W getAllowedWebViewHosts()`(forge: Forge) {
        // Given
        val fakeHosts = forge.aList { getForgery<URL>().host }
        CoreFeature.webViewTrackingHosts = fakeHosts
        val expectedHosts = JsonArray()
        fakeHosts.forEach {
            expectedHosts.add(it)
        }
        // When
        val allowedWebViewHosts = testedDatadogEventBridge.getAllowedWebViewHosts()

        // Then
        assertThat(allowedWebViewHosts).isEqualTo(expectedHosts.toString())
    }
}
