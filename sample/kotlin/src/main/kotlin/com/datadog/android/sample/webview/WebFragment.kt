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
import androidx.lifecycle.ViewModelProviders
import com.datadog.android.rum.ExperimentalRumApi
import com.datadog.android.rum.GlobalRumMonitor
import com.datadog.android.sample.R
import com.datadog.android.sample.SampleApplication
import com.datadog.android.webview.WebViewTracking

internal class WebFragment : Fragment() {

    private lateinit var viewModel: WebViewModel
    private lateinit var webView: WebView

    private val webViewTrackingHosts = listOf(
        "datadoghq.dev"
    )

    // region Fragment Lifecycle

    @OptIn(ExperimentalRumApi::class)
    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val rootView = inflater.inflate(R.layout.fragment_web, container, false)
        webView = rootView.findViewById(R.id.webview)
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                GlobalRumMonitor.get().addViewLoadingTime(overwrite = false)
            }
        }
        webView.settings.javaScriptEnabled = true
        WebViewTracking.enable(webView, webViewTrackingHosts)
        return rootView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val factory = SampleApplication.getViewModelFactory(requireContext())
        viewModel = ViewModelProviders.of(this, factory).get(WebViewModel::class.java)
    }

    override fun onResume() {
        super.onResume()
        viewModel.onResume()
        webView.loadUrl(viewModel.url)
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
