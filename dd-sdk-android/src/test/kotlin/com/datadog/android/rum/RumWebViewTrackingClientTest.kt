/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum

import android.graphics.Bitmap
import android.webkit.WebView
import com.datadog.android.core.internal.CoreFeature
import com.datadog.android.core.internal.net.FirstPartyHostDetector
import com.datadog.android.rum.webview.DatadogEventBridge
import com.datadog.android.rum.webview.RumWebViewTrackingClient
import com.datadog.android.utils.forge.Configurator
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argThat
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.junit.jupiter.api.AfterEach
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
    ExtendWith(TestConfigurationExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class RumWebViewTrackingClientTest {

    @Mock
    lateinit var mockWebView: WebView

    @StringForgery(regex = "(https|http)://([a-z][a-z0-9-]{3,9}\\.){1,4}[a-z][a-z0-9]{2,3}")
    lateinit var fakeUrl: String

    var fakeFavicon: Bitmap? = null

    @Mock
    lateinit var mockWebViewTrackingHostsDetector: FirstPartyHostDetector

    lateinit var originalWebViewTrackingHostsDetector: FirstPartyHostDetector

    lateinit var testedRumViewTrackingClient: RumWebViewTrackingClient

    @BeforeEach
    fun `set up`(forge: Forge) {
        fakeFavicon = forge.aNullable { mock() }
        testedRumViewTrackingClient = RumWebViewTrackingClient()
        originalWebViewTrackingHostsDetector = CoreFeature.webViewTrackingHostsDetector
        CoreFeature.webViewTrackingHostsDetector = mockWebViewTrackingHostsDetector
    }

    @AfterEach
    fun `tear down`() {
        CoreFeature.webViewTrackingHostsDetector = originalWebViewTrackingHostsDetector
    }

    @Test
    fun `M add the JS bridge W onPageStarted { host marked for tracking }`() {
        // Given
        whenever(mockWebViewTrackingHostsDetector.isFirstPartyUrl(fakeUrl)).thenReturn(true)

        // When
        testedRumViewTrackingClient.onPageStarted(mockWebView, fakeUrl, fakeFavicon)

        // Then
        verify(mockWebView).addJavascriptInterface(
            argThat { this is DatadogEventBridge },
            eq(RumWebViewTrackingClient.DATADOG_JS_BRIDGE)
        )
    }

    @Test
    fun `M not add the JS bridge W onPageStarted { host not marked for tracking }`() {
        // When
        testedRumViewTrackingClient.onPageStarted(mockWebView, fakeUrl, fakeFavicon)

        // Then
        verify(mockWebView, never()).addJavascriptInterface(any(), any())
    }

    @Test
    fun `M not add the JS bridge W onPageStarted { webview is null }`() {
        // When
        testedRumViewTrackingClient.onPageStarted(mockWebView, fakeUrl, fakeFavicon)

        // Then
        verify(mockWebView, never()).addJavascriptInterface(any(), any())
    }

    @Test
    fun `M not add the JS bridge W onPageStarted { url is null }`() {
        // When
        testedRumViewTrackingClient.onPageStarted(mockWebView, fakeUrl, fakeFavicon)

        // Then
        verify(mockWebView, never()).addJavascriptInterface(any(), any())
    }

    @Test
    fun `M not add the JS bridge W onPageStarted { bitmap is null }`() {
        // When
        testedRumViewTrackingClient.onPageStarted(mockWebView, fakeUrl, fakeFavicon)

        // Then
        verify(mockWebView, never()).addJavascriptInterface(any(), any())
    }
}
