/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sdk.integration.webview

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.webkit.WebView
import androidx.appcompat.app.AppCompatActivity
import com.datadog.android.Datadog
import com.datadog.android.okhttp.DatadogInterceptor
import com.datadog.android.rum.Rum
import com.datadog.android.sdk.integration.RuntimeConfig
import com.datadog.android.sdk.utils.getTrackingConsent
import com.datadog.android.trace.Trace
import com.datadog.android.webview.WebViewTracking

internal class WebViewBridgePlaygroundActivity : AppCompatActivity() {

    lateinit var webView: WebView

    @SuppressLint("SetJavaScriptEnabled")
    @Suppress("CheckInternal")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val config = RuntimeConfig.configBuilder().build()
        val trackingConsent = intent.getTrackingConsent()

        Datadog.setVerbosity(Log.VERBOSE)
        val sdkCore = checkNotNull(
            Datadog.initialize(this, config, trackingConsent)
        )

        Rum.enable(RuntimeConfig.rumConfigBuilder().build(), sdkCore)

        // Trace.enable registers the "tracing" feature scope, which is required for
        // updateFeatureContext to work. The interceptor then populates
        // okhttp_interceptor_sample_rate, which getIsTraceSampled() reads.
        Trace.enable(RuntimeConfig.tracesConfigBuilder().build(), sdkCore)
        DatadogInterceptor.Builder(listOf("localhost"))
            .setTraceSampleRate(100f)
            .build()

        webView = WebView(this).also { wv ->
            wv.settings.javaScriptEnabled = true
            WebViewTracking.enable(wv, listOf("localhost"), sdkCore = sdkCore)
            setContentView(wv)
            wv.loadData("<html><body></body></html>", "text/html", "UTF-8")
        }
    }
}
