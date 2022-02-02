/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.nightly.activities

import android.webkit.WebView
import com.datadog.android.nightly.utils.measure
import com.datadog.android.webview.DatadogEventBridge

internal class WebViewTrackingBridgeHostsActivity : WebViewTrackingActivity() {

    override fun setupWebView(webView: WebView) {
        measure(TEST_METHOD_NAME) {
            webView.addJavascriptInterface(
                DatadogEventBridge(listOf("datadoghq.dev")), "DatadogEventBridge"
            )
        }
    }
}
