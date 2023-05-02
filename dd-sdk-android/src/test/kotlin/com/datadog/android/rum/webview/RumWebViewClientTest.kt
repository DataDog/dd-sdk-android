/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.webview

import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.os.Build
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.datadog.android.rum.RumAttributes
import com.datadog.android.rum.RumErrorSource
import com.datadog.android.rum.RumResourceKind
import com.datadog.android.utils.config.GlobalRumMonitorTestConfiguration
import com.datadog.android.utils.forge.Configurator
import com.datadog.tools.unit.annotations.TestConfigurationsProvider
import com.datadog.tools.unit.annotations.TestTargetApi
import com.datadog.tools.unit.extensions.ApiLevelExtension
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import com.datadog.tools.unit.extensions.config.TestConfiguration
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
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
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class),
    ExtendWith(ApiLevelExtension::class),
    ExtendWith(TestConfigurationExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
@Suppress("DEPRECATION")
internal class RumWebViewClientTest {

    private lateinit var testedClient: WebViewClient

    @Mock
    private lateinit var mockWebView: WebView

    @Mock
    private lateinit var mockBitmap: Bitmap

    @StringForgery(regex = "http(s?)://[a-z]+\\.com/\\w+")
    private lateinit var fakeUrl: String

    @BeforeEach
    fun `set up`() {
        testedClient = RumWebViewClient()
    }

    @Test
    fun `onPageStarted starts a RUM Resource`() {
        testedClient.onPageStarted(mockWebView, fakeUrl, mockBitmap)

        verify(rumMonitor.mockInstance).startResource(
            fakeUrl,
            "GET",
            fakeUrl,
            emptyMap()
        )
    }

    @Test
    fun `onPageStarted with null URL does nothing`() {
        testedClient.onPageStarted(mockWebView, null, mockBitmap)

        verifyNoInteractions(rumMonitor.mockInstance)
    }

    @Test
    fun `onPageFinished stops a RUM Resource`() {
        testedClient.onPageFinished(mockWebView, fakeUrl)

        verify(rumMonitor.mockInstance).stopResource(
            fakeUrl,
            200,
            null,
            RumResourceKind.DOCUMENT,
            emptyMap()
        )
    }

    @Test
    fun `onPageFinished with null URL does nothing`() {
        testedClient.onPageFinished(mockWebView, null)

        verifyNoInteractions(rumMonitor.mockInstance)
    }

    @Test
    fun `onReceivedError sends a RUM Error`(
        @IntForgery errorCode: Int,
        @StringForgery description: String
    ) {
        @Suppress("DEPRECATION")
        testedClient.onReceivedError(mockWebView, errorCode, description, fakeUrl)

        verify(rumMonitor.mockInstance).addError(
            "Error $errorCode: $description",
            RumErrorSource.WEBVIEW,
            null,
            mapOf(RumAttributes.ERROR_RESOURCE_URL to fakeUrl)
        )
    }

    @Test
    fun `onReceivedError with null description sends a RUM Error`(
        @IntForgery errorCode: Int
    ) {
        @Suppress("DEPRECATION")
        testedClient.onReceivedError(mockWebView, errorCode, null, fakeUrl)

        verify(rumMonitor.mockInstance).addError(
            "Error $errorCode: null",
            RumErrorSource.WEBVIEW,
            null,
            mapOf(RumAttributes.ERROR_RESOURCE_URL to fakeUrl)
        )
    }

    @Test
    fun `onReceivedError with null url sends a RUM Error`(
        @IntForgery errorCode: Int,
        @StringForgery description: String
    ) {
        @Suppress("DEPRECATION")
        testedClient.onReceivedError(mockWebView, errorCode, description, null)

        verify(rumMonitor.mockInstance).addError(
            "Error $errorCode: $description",
            RumErrorSource.WEBVIEW,
            null,
            mapOf(RumAttributes.ERROR_RESOURCE_URL to null)
        )
    }

    @Test
    @TestTargetApi(Build.VERSION_CODES.M)
    fun `onReceivedError (request) sends a RUM Error`(
        @IntForgery errorCode: Int,
        @StringForgery description: String
    ) {
        val mockRequest: WebResourceRequest = mock()
        val mockError: WebResourceError = mock()
        val mockUri: Uri = mock()
        whenever(mockRequest.url) doReturn mockUri
        whenever(mockError.description) doReturn description
        whenever(mockError.errorCode) doReturn errorCode

        testedClient.onReceivedError(mockWebView, mockRequest, mockError)

        verify(rumMonitor.mockInstance).addError(
            "Error $errorCode: $description",
            RumErrorSource.WEBVIEW,
            null,
            mapOf(RumAttributes.ERROR_RESOURCE_URL to mockUri)
        )
    }

    @Test
    @TestTargetApi(Build.VERSION_CODES.M)
    fun `onReceivedError (request) with null request sends a RUM Error`(
        @IntForgery errorCode: Int,
        @StringForgery description: String
    ) {
        val mockError: WebResourceError = mock()
        whenever(mockError.description) doReturn description
        whenever(mockError.errorCode) doReturn errorCode

        testedClient.onReceivedError(mockWebView, null, mockError)

        verify(rumMonitor.mockInstance).addError(
            "Error $errorCode: $description",
            RumErrorSource.WEBVIEW,
            null,
            mapOf(RumAttributes.ERROR_RESOURCE_URL to null)
        )
    }

    @Test
    @TestTargetApi(Build.VERSION_CODES.M)
    fun `onReceivedError (request) with null error sends a RUM Error`() {
        val mockRequest: WebResourceRequest = mock()
        val mockUri: Uri = mock()
        whenever(mockRequest.url) doReturn mockUri

        testedClient.onReceivedError(mockWebView, mockRequest, null)

        verify(rumMonitor.mockInstance).addError(
            "Error null: null",
            RumErrorSource.WEBVIEW,
            null,
            mapOf(RumAttributes.ERROR_RESOURCE_URL to mockUri)
        )
    }

    @Test
    @TestTargetApi(Build.VERSION_CODES.LOLLIPOP)
    fun `onReceivedHttpError sends a RUM Error`(
        @IntForgery statusCode: Int,
        @StringForgery reasonPhrase: String
    ) {
        val mockRequest: WebResourceRequest = mock()
        val mockResponse: WebResourceResponse = mock()
        val mockUri: Uri = mock()
        whenever(mockRequest.url) doReturn mockUri
        whenever(mockResponse.reasonPhrase) doReturn reasonPhrase
        whenever(mockResponse.statusCode) doReturn statusCode

        testedClient.onReceivedHttpError(mockWebView, mockRequest, mockResponse)

        verify(rumMonitor.mockInstance).addError(
            "Error $statusCode: $reasonPhrase",
            RumErrorSource.WEBVIEW,
            null,
            mapOf(RumAttributes.ERROR_RESOURCE_URL to mockUri)
        )
    }

    @Test
    @TestTargetApi(Build.VERSION_CODES.LOLLIPOP)
    fun `onReceivedHttpError with null response sends a RUM Error`() {
        val mockRequest: WebResourceRequest = mock()
        val mockUri: Uri = mock()
        whenever(mockRequest.url) doReturn mockUri

        testedClient.onReceivedHttpError(mockWebView, mockRequest, null)

        verify(rumMonitor.mockInstance).addError(
            "Error null: null",
            RumErrorSource.WEBVIEW,
            null,
            mapOf(RumAttributes.ERROR_RESOURCE_URL to mockUri)
        )
    }

    @Test
    @TestTargetApi(Build.VERSION_CODES.LOLLIPOP)
    fun `onReceivedHttpError with null request sends a RUM Error`(
        @IntForgery statusCode: Int,
        @StringForgery reasonPhrase: String
    ) {
        val mockResponse: WebResourceResponse = mock()
        whenever(mockResponse.reasonPhrase) doReturn reasonPhrase
        whenever(mockResponse.statusCode) doReturn statusCode

        testedClient.onReceivedHttpError(mockWebView, null, mockResponse)

        verify(rumMonitor.mockInstance).addError(
            "Error $statusCode: $reasonPhrase",
            RumErrorSource.WEBVIEW,
            null,
            mapOf(RumAttributes.ERROR_RESOURCE_URL to null)
        )
    }

    @Test
    fun `onReceivedSslError sends a RUM Error`(
        @IntForgery primaryError: Int
    ) {
        val mockError: SslError = mock()
        whenever(mockError.primaryError) doReturn primaryError
        whenever(mockError.url) doReturn fakeUrl

        testedClient.onReceivedSslError(mockWebView, mock(), mockError)

        verify(rumMonitor.mockInstance).addError(
            "SSL Error $primaryError",
            RumErrorSource.WEBVIEW,
            null,
            mapOf(RumAttributes.ERROR_RESOURCE_URL to fakeUrl)
        )
    }

    @Test
    fun `onReceivedSslError with null handler sends a RUM Error`(
        @IntForgery primaryError: Int
    ) {
        val mockError: SslError = mock()
        whenever(mockError.primaryError) doReturn primaryError
        whenever(mockError.url) doReturn fakeUrl

        testedClient.onReceivedSslError(mockWebView, null, mockError)

        verify(rumMonitor.mockInstance).addError(
            "SSL Error $primaryError",
            RumErrorSource.WEBVIEW,
            null,
            mapOf(RumAttributes.ERROR_RESOURCE_URL to fakeUrl)
        )
    }

    @Test
    fun `onReceivedSslError with null error sends a RUM Error`() {
        testedClient.onReceivedSslError(mockWebView, mock(), null)

        verify(rumMonitor.mockInstance).addError(
            "SSL Error null",
            RumErrorSource.WEBVIEW,
            null,
            mapOf(RumAttributes.ERROR_RESOURCE_URL to null)
        )
    }

    companion object {
        val rumMonitor = GlobalRumMonitorTestConfiguration()

        @TestConfigurationsProvider
        @JvmStatic
        fun getTestConfigurations(): List<TestConfiguration> {
            return listOf(rumMonitor)
        }
    }
}
