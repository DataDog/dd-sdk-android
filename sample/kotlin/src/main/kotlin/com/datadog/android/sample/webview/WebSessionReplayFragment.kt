/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.sample.webview

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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

internal class WebSessionReplayFragment : Fragment() {

    private lateinit var webView1: WebView
    private lateinit var triggerRumViewButton: Button
    private val handler = Handler(Looper.getMainLooper())

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
        triggerRumViewButton = rootView.findViewById(R.id.trigger_new_rum_view_button)
        webView1 = rootView.findViewById(R.id.webview1)
        webView1.webViewClient = WebViewClient()
        webView1.settings.javaScriptEnabled = true
        WebViewTracking.enable(webView1, webViewTrackingHosts)
        triggerRumViewButton.setOnClickListener {
            GlobalRumMonitor.get().startView(this, "Custom RUM View")
        }
        return rootView
    }

    override fun onResume() {
        super.onResume()
        webView1.loadUrl("https://datadoghq.dev/browser-sdk-test-playground/webview-support/#click_event")
        handler.removeCallbacksAndMessages(null)
        // Uncomment this line if you want to display the system time and the webview time periodically
        // displayCurrentTimeInMillisPeriodically()
    }

    // endregion

    companion object {
        fun newInstance(): WebSessionReplayFragment {
            return WebSessionReplayFragment()
        }
    }
}
