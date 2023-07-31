/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.nightly.activities

import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import com.datadog.android.nightly.R
import com.datadog.android.nightly.utils.measure
import com.datadog.android.webview.WebViewTracking
import java.util.UUID
import kotlin.random.Random

internal class WebViewTrackingActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.webview_tracking_activity)
        val webView: WebView = findViewById(R.id.webview)
        webView.webViewClient = WebViewClient()
        webView.settings.javaScriptEnabled = true
        setupWebView(webView)
        val fakeToken = "pub${Random.nextLong().toString(HEX_RADIX)}"
        val fakeAppId = UUID.randomUUID().toString()
        webView.loadUrl(
            "https://datadoghq.dev/browser-sdk-test-playground/" +
                "?client_token=$fakeToken" +
                "&application_id=$fakeAppId" +
                "&site=datadoghq.com"
        )
    }

    @Suppress("CheckInternal")
    private fun setupWebView(webView: WebView) {
        measure(TEST_METHOD_NAME) {
            val isNoAllowedHosts = intent.extras
                ?.getBoolean(NO_ALLOWED_HOSTS_KEY, false) ?: false
            val allowedHosts = if (isNoAllowedHosts) {
                emptyList()
            } else {
                listOf("datadoghq.dev")
            }
            WebViewTracking.enable(webView, allowedHosts)
        }
    }

    companion object {
        const val HEX_RADIX = 16
        const val NO_ALLOWED_HOSTS_KEY = "no_allowed_hosts"
        internal const val TEST_METHOD_NAME = "web_view_tracking"
    }
}
