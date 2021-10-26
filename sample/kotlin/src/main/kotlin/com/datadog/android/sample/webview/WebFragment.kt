/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.sample.webview

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProviders
import com.datadog.android.rum.webview.RumWebChromeClient
import com.datadog.android.rum.webview.RumWebViewClient
import com.datadog.android.rum.webview.RumWebViewTrackingClient
import com.datadog.android.sample.BuildConfig
import com.datadog.android.sample.R

class WebFragment : Fragment() {

    private lateinit var viewModel: WebViewModel
    private lateinit var webView: WebView

    // region Fragment Lifecycle

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val rootView = inflater.inflate(R.layout.fragment_web, container, false)
        webView = rootView.findViewById(R.id.webview)
        webView.webViewClient = RumWebViewTrackingClient()
        webView.webChromeClient = RumWebChromeClient()
        return rootView
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        viewModel = ViewModelProviders.of(this).get(WebViewModel::class.java)
    }

    override fun onResume() {
        super.onResume()
        viewModel.onResume()
        webView.settings.javaScriptEnabled = true
        webView.loadUrl(
            "https://datadoghq.dev/browser-sdk-test-playground/webview.html" +
                "?client_token=${BuildConfig.DD_CLIENT_TOKEN}" +
                "&application_id=${BuildConfig.APPLICATION_ID}" +
                "&site=datadoghq.com"
        )
    }

    override fun onPause() {
        super.onPause()
        viewModel.onPause()
    }

    // endregion

    companion object {
        fun newInstance(): WebFragment {
            return WebFragment()
        }
    }
}
