/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.webview

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.WebView
import android.webkit.WebViewClient
import com.datadog.android.core.internal.CoreFeature
import com.datadog.android.core.internal.net.FirstPartyHostDetector

/**
 * This [WebViewClient] is used to intercept all the Datadog events produced by the displayed web
 * content when there is already a Datadog browser-sdk attached. By using this client
 * we will enable by default the javaScript execution inside the WebView
 * so have this in mind in case you are considering security constraints.
 * The goal is to make those events part of the mobile session.
 * Please note that the WebView will not be tracked unless you added the host name in the hosts
 * lists in the SDK configuration.
 * @see [com.datadog.android.core.configuration.Configuration.Builder]
 */
class RumWebViewTrackingClient : WebViewClient() {

    private val hostDetector: FirstPartyHostDetector =
            FirstPartyHostDetector(CoreFeature.webViewTrackingHosts)

    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        if (view != null &&
                url != null &&
                hostDetector.isFirstPartyUrl(url)
        ) {
            @SuppressLint("SetJavaScriptEnabled")
            view.settings.javaScriptEnabled = true
            view.addJavascriptInterface(DatadogEventBridge(), DATADOG_JS_BRIDGE)
        }
    }

    internal companion object {
        const val DATADOG_JS_BRIDGE = "DatadogJsInterface"
    }
}
