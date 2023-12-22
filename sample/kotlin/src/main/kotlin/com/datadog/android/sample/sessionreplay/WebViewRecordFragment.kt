/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.sample.sessionreplay

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import androidx.fragment.app.Fragment
import com.datadog.android.rum.GlobalRumMonitor
import com.datadog.android.sample.R
import com.datadog.android.webview.WebViewTracking

internal class WebViewRecordFragment : Fragment() {

    private lateinit var webView: WebView
    private lateinit var startCustomRumViewButton: Button
    private val webViewTrackingHosts = listOf(
        "datadoghq.dev"
    )

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val rootView = inflater.inflate(
            R.layout.fragment_webview_with_replay_support,
            container,
            false
        )
        startCustomRumViewButton = rootView.findViewById(R.id.start_custom_rum_view_button)
        webView = rootView.findViewById(R.id.webview)
        webView.webViewClient = WebViewClient()
        webView.settings.javaScriptEnabled = true
        WebViewTracking.enable(webView, webViewTrackingHosts)
        startCustomRumViewButton.setOnClickListener {
            GlobalRumMonitor.get().startView(this, "Custom RUM View")
        }
        return rootView
    }

    override fun onResume() {
        super.onResume()
        webView.loadUrl("https://datadoghq.dev/browser-sdk-test-playground/webview-support/#click_event")
    }
}
