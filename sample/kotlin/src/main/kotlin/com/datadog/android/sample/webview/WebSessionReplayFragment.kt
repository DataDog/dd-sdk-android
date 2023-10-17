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
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.datadog.android.sample.R
import com.datadog.android.webview.WebViewTracking
import java.util.Locale

internal class WebSessionReplayFragment : Fragment() {

    private lateinit var webView1: WebView
    private lateinit var currentTimestampView: TextView
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
        currentTimestampView = rootView.findViewById(R.id.current_timestamp_view)
        webView1 = rootView.findViewById(R.id.webview1)
        webView1.webViewClient = WebViewClient()
        webView1.settings.javaScriptEnabled = true
        WebViewTracking.enable(webView1, webViewTrackingHosts)
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

    private fun displayCurrentTimeInMillisPeriodically() {
        handler.postDelayed(
            object : Runnable {
                override fun run() {
                    webView1.evaluateJavascript("String(new Date().getTime())") {
                        val webTimestamp = it.replace("\"", "")
                        val deviceTimestamp = System.currentTimeMillis().toString()
                        currentTimestampView.text = TIMESTAMP_LABEL_FORMAT.format(Locale.US, deviceTimestamp, webTimestamp)
                        handler.postDelayed(this, 2000)
                    }
                }
            },
            10000
        )
    }

    companion object {
        private const val TIMESTAMP_LABEL_FORMAT = "Device Timestamp: %s \n Webview Timestamp: %s"
        fun newInstance(): WebSessionReplayFragment {
            return WebSessionReplayFragment()
        }
    }
}
