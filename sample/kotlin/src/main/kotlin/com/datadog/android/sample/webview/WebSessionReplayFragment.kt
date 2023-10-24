/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.sample.webview

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.fragment.app.Fragment
import com.datadog.android.sample.R
import com.datadog.android.webview.WebViewTracking

internal class WebSessionReplayFragment : Fragment() {

    private lateinit var webView1: WebView
//    private lateinit var webView2: WebView

    private val webViewTrackingHosts = listOf(
        "datadoghq.dev"
    )

    // region Fragment Lifecycle

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val rootView = inflater.inflate(R.layout.fragment_web_session_replay, container, false)
        webView1 = rootView.findViewById(R.id.webview1)
        webView1.webViewClient = WebViewClient()
        webView1.settings.javaScriptEnabled = true
        WebViewTracking.enable(webView1, webViewTrackingHosts)
//        webView2 = rootView.findViewById(R.id.webview2)
//        webView2.webViewClient = WebViewClient()
//        webView2.settings.javaScriptEnabled = true
//        WebViewTracking.enable(webView2, webViewTrackingHosts)
        return rootView
    }

    override fun onResume() {
        super.onResume()
        webView1.loadUrl("https://datadoghq.dev/browser-sdk-test-playground/webview-support/#basic-text")
//        webView2.loadUrl("https://datadoghq.dev/browser-sdk-test-playground/webview-support/#basic-text")
    }

    // endregion

    companion object {
        fun newInstance(): WebSessionReplayFragment {
            return WebSessionReplayFragment()
        }
    }
}
