/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2020 Datadog, Inc.
 */

package com.datadog.android.rum

import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.os.Build
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.datadog.android.rum.internal.monitor.NoOpRumMonitor
import com.datadog.android.utils.forge.Configurator
import com.datadog.tools.unit.annotations.TestTargetApi
import com.datadog.tools.unit.extensions.ApiLevelExtension
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyZeroInteractions
import com.nhaarman.mockitokotlin2.whenever
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.annotation.RegexForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.annotation.StringForgeryType
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
    ExtendWith(ApiLevelExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class RumWebViewClientTest {

    private lateinit var testedClient: WebViewClient

    @Mock
    private lateinit var mockRumMonitor: RumMonitor

    @Mock
    private lateinit var mockWebView: WebView

    @Mock
    private lateinit var mockBitmap: Bitmap

    @RegexForgery("http(s?)://[a-z]+\\.com/\\w+")
    private lateinit var fakeUrl: String

    @BeforeEach
    fun `set up`() {
        GlobalRum.registerIfAbsent(mockRumMonitor)
        testedClient = RumWebViewClient()
    }

    @AfterEach
    fun `tear down`() {
        GlobalRum.monitor = NoOpRumMonitor()
        GlobalRum.isRegistered.set(false)
    }

    @Test
    fun `onPageStarted starts a Rum Resource`() {
        testedClient.onPageStarted(mockWebView, fakeUrl, mockBitmap)

        verify(mockRumMonitor).startResource(
            fakeUrl,
            fakeUrl,
            emptyMap()
        )
    }

    @Test
    fun `onPageStarted with null URL does nothing`() {
        testedClient.onPageStarted(mockWebView, null, mockBitmap)

        verifyZeroInteractions(mockRumMonitor)
    }

    @Test
    fun `onPageFinished stops a Rum Resource`() {
        testedClient.onPageFinished(mockWebView, fakeUrl)

        verify(mockRumMonitor).stopResource(
            fakeUrl,
            RumResourceKind.DOCUMENT
        )
    }

    @Test
    fun `onPageFinished with null URL does nothing`() {
        testedClient.onPageFinished(mockWebView, null)

        verifyZeroInteractions(mockRumMonitor)
    }

    @Test
    fun `onReceivedError sends a Rum Error`(
        @IntForgery errorCode: Int,
        @StringForgery(StringForgeryType.ALPHABETICAL) description: String
    ) {
        testedClient.onReceivedError(mockWebView, errorCode, description, fakeUrl)

        verify(mockRumMonitor).addError(
            "Error $errorCode: $description",
            RumWebViewClient.ORIGIN,
            null,
            mapOf(RumAttributes.HTTP_URL to fakeUrl)
        )
    }

    @Test
    fun `onReceivedError with null description sends a Rum Error`(
        @IntForgery errorCode: Int,
        @StringForgery(StringForgeryType.ALPHABETICAL) description: String
    ) {
        testedClient.onReceivedError(mockWebView, errorCode, null, fakeUrl)

        verify(mockRumMonitor).addError(
            "Error $errorCode: null",
            RumWebViewClient.ORIGIN,
            null,
            mapOf(RumAttributes.HTTP_URL to fakeUrl)
        )
    }

    @Test
    fun `onReceivedError with null url sends a Rum Error`(
        @IntForgery errorCode: Int,
        @StringForgery(StringForgeryType.ALPHABETICAL) description: String
    ) {
        testedClient.onReceivedError(mockWebView, errorCode, description, null)

        verify(mockRumMonitor).addError(
            "Error $errorCode: $description",
            RumWebViewClient.ORIGIN,
            null,
            mapOf(RumAttributes.HTTP_URL to null)
        )
    }

    @Test
    @TestTargetApi(Build.VERSION_CODES.M)
    fun `onReceivedError (request) sends a Rum Error`(
        @IntForgery errorCode: Int,
        @StringForgery(StringForgeryType.ALPHABETICAL) description: String
    ) {
        val mockRequest: WebResourceRequest = mock()
        val mockError: WebResourceError = mock()
        val mockUri: Uri = mock()
        whenever(mockRequest.url) doReturn mockUri
        whenever(mockError.description) doReturn description
        whenever(mockError.errorCode) doReturn errorCode

        testedClient.onReceivedError(mockWebView, mockRequest, mockError)

        verify(mockRumMonitor).addError(
            "Error $errorCode: $description",
            RumWebViewClient.ORIGIN,
            null,
            mapOf(RumAttributes.HTTP_URL to mockUri)
        )
    }

    @Test
    @TestTargetApi(Build.VERSION_CODES.M)
    fun `onReceivedError (request) with null request sends a Rum Error`(
        @IntForgery errorCode: Int,
        @StringForgery(StringForgeryType.ALPHABETICAL) description: String
    ) {
        val mockError: WebResourceError = mock()
        whenever(mockError.description) doReturn description
        whenever(mockError.errorCode) doReturn errorCode

        testedClient.onReceivedError(mockWebView, null, mockError)

        verify(mockRumMonitor).addError(
            "Error $errorCode: $description",
            RumWebViewClient.ORIGIN,
            null,
            mapOf(RumAttributes.HTTP_URL to null)
        )
    }

    @Test
    @TestTargetApi(Build.VERSION_CODES.M)
    fun `onReceivedError (request) with null error sends a Rum Error`(
        @IntForgery errorCode: Int,
        @StringForgery(StringForgeryType.ALPHABETICAL) description: String
    ) {
        val mockRequest: WebResourceRequest = mock()
        val mockUri: Uri = mock()
        whenever(mockRequest.url) doReturn mockUri

        testedClient.onReceivedError(mockWebView, mockRequest, null)

        verify(mockRumMonitor).addError(
            "Error null: null",
            RumWebViewClient.ORIGIN,
            null,
            mapOf(RumAttributes.HTTP_URL to mockUri)
        )
    }

    @Test
    @TestTargetApi(Build.VERSION_CODES.LOLLIPOP)
    fun `onReceivedHttpError sends a Rum Error`(
        @IntForgery statusCode: Int,
        @StringForgery(StringForgeryType.ALPHABETICAL) reasonPhrase: String
    ) {
        val mockRequest: WebResourceRequest = mock()
        val mockResponse: WebResourceResponse = mock()
        val mockUri: Uri = mock()
        whenever(mockRequest.url) doReturn mockUri
        whenever(mockResponse.reasonPhrase) doReturn reasonPhrase
        whenever(mockResponse.statusCode) doReturn statusCode

        testedClient.onReceivedHttpError(mockWebView, mockRequest, mockResponse)

        verify(mockRumMonitor).addError(
            "Error $statusCode: $reasonPhrase",
            RumWebViewClient.ORIGIN,
            null,
            mapOf(RumAttributes.HTTP_URL to mockUri)
        )
    }

    @Test
    @TestTargetApi(Build.VERSION_CODES.LOLLIPOP)
    fun `onReceivedHttpError with null response sends a Rum Error`(
        @IntForgery statusCode: Int,
        @StringForgery(StringForgeryType.ALPHABETICAL) reasonPhrase: String
    ) {
        val mockRequest: WebResourceRequest = mock()
        val mockUri: Uri = mock()
        whenever(mockRequest.url) doReturn mockUri

        testedClient.onReceivedHttpError(mockWebView, mockRequest, null)

        verify(mockRumMonitor).addError(
            "Error null: null",
            RumWebViewClient.ORIGIN,
            null,
            mapOf(RumAttributes.HTTP_URL to mockUri)
        )
    }

    @Test
    @TestTargetApi(Build.VERSION_CODES.LOLLIPOP)
    fun `onReceivedHttpError with null request sends a Rum Error`(
        @IntForgery statusCode: Int,
        @StringForgery(StringForgeryType.ALPHABETICAL) reasonPhrase: String
    ) {
        val mockResponse: WebResourceResponse = mock()
        whenever(mockResponse.reasonPhrase) doReturn reasonPhrase
        whenever(mockResponse.statusCode) doReturn statusCode

        testedClient.onReceivedHttpError(mockWebView, null, mockResponse)

        verify(mockRumMonitor).addError(
            "Error $statusCode: $reasonPhrase",
            RumWebViewClient.ORIGIN,
            null,
            mapOf(RumAttributes.HTTP_URL to null)
        )
    }

    @Test
    fun `onReceivedSslError sends a Rum Error`(
        @IntForgery primaryError: Int
    ) {
        val mockError: SslError = mock()
        whenever(mockError.primaryError) doReturn primaryError
        whenever(mockError.url) doReturn fakeUrl

        testedClient.onReceivedSslError(mockWebView, mock(), mockError)

        verify(mockRumMonitor).addError(
            "SSL Error $primaryError",
            RumWebViewClient.ORIGIN,
            null,
            mapOf(RumAttributes.HTTP_URL to fakeUrl)
        )
    }

    @Test
    fun `onReceivedSslError with null handler sends a Rum Error`(
        @IntForgery primaryError: Int
    ) {
        val mockError: SslError = mock()
        whenever(mockError.primaryError) doReturn primaryError
        whenever(mockError.url) doReturn fakeUrl

        testedClient.onReceivedSslError(mockWebView, null, mockError)

        verify(mockRumMonitor).addError(
            "SSL Error $primaryError",
            RumWebViewClient.ORIGIN,
            null,
            mapOf(RumAttributes.HTTP_URL to fakeUrl)
        )
    }

    @Test
    fun `onReceivedSslError with null error sends a Rum Error`(
        @IntForgery primaryError: Int
    ) {
        testedClient.onReceivedSslError(mockWebView, mock(), null)

        verify(mockRumMonitor).addError(
            "SSL Error null",
            RumWebViewClient.ORIGIN,
            null,
            mapOf(RumAttributes.HTTP_URL to null)
        )
    }
}
