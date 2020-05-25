/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.webview

import android.graphics.Bitmap
import android.net.http.SslError
import android.os.Build
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.annotation.RequiresApi
import com.datadog.android.rum.GlobalRum
import com.datadog.android.rum.RumAttributes
import com.datadog.android.rum.RumResourceType

/**
 * A [WebViewClient] propagating all relevant events to the [GlobalRum] monitor.
 *
 * This will map the page loading, and webview errors into Rum Resource and
 * Error events respectively.
 */
open class RumWebViewClient : WebViewClient() {

    // region WebViewClient

    /** @inheritdoc */
    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        if (url != null) {
            val key = url
            GlobalRum.get().startResource(key,
                METHOD_GET, url)
        }
    }

    /** @inheritdoc */
    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        if (url != null) {
            val key = url
            GlobalRum.get().stopResource(key,
                RumResourceType.DOCUMENT
            )
        }
    }

    /** @inheritdoc */
    override fun onReceivedError(
        view: WebView?,
        errorCode: Int,
        description: String?,
        failingUrl: String?
    ) {
        super.onReceivedError(view, errorCode, description, failingUrl)
        GlobalRum.get().addError(
            "Error $errorCode: $description",
            SOURCE,
            null,
            mapOf(RumAttributes.RESOURCE_URL to failingUrl)
        )
    }

    /** @inheritdoc */
    @RequiresApi(Build.VERSION_CODES.M)
    override fun onReceivedError(
        view: WebView?,
        request: WebResourceRequest?,
        error: WebResourceError?
    ) {
        super.onReceivedError(view, request, error)
        GlobalRum.get().addError(
            "Error ${error?.errorCode}: ${error?.description}",
            SOURCE,
            null,
            mapOf(RumAttributes.RESOURCE_URL to request?.url)
        )
    }

    /** @inheritdoc */
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    override fun onReceivedHttpError(
        view: WebView?,
        request: WebResourceRequest?,
        errorResponse: WebResourceResponse?
    ) {
        super.onReceivedHttpError(view, request, errorResponse)
        GlobalRum.get().addError(
            "Error ${errorResponse?.statusCode}: ${errorResponse?.reasonPhrase}",
            SOURCE,
            null,
            mapOf(RumAttributes.RESOURCE_URL to request?.url)
        )
    }

    /** @inheritdoc */
    override fun onReceivedSslError(
        view: WebView?,
        handler: SslErrorHandler?,
        error: SslError?
    ) {
        super.onReceivedSslError(view, handler, error)
        GlobalRum.get().addError(
            "SSL Error ${error?.primaryError}",
            SOURCE,
            null,
            mapOf(RumAttributes.RESOURCE_URL to error?.url)
        )
    }

    // endregion

    companion object {
        internal const val SOURCE = "WebViewClient"
        internal const val METHOD_GET = "GET"
    }
}
