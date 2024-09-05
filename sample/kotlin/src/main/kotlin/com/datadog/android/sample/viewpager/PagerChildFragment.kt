/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sample.viewpager

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.datadog.android.rum.ExperimentalRumApi
import com.datadog.android.rum.GlobalRumMonitor
import com.datadog.android.sample.R
import com.datadog.android.webview.WebViewTracking

internal open class PagerChildFragment : Fragment() {

    private val pageName: String
        get() = requireArguments().getString(ARG_PAGE_NAME) ?: "UNKNOWN"

    private val webViewUrl: String
        get() = requireArguments().getString(ARG_WEB_VIEW_URL) ?: "https://datadoghq.com"

    private val webViewTrackingHosts = listOf(
        "datadoghq.dev"
    )
    private lateinit var webView: WebView

    @Volatile
    private var pageWasLoaded: Boolean = false

    @OptIn(ExperimentalRumApi::class)
    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.view_pager_child_fragment_layout, container, false)
        view.findViewById<TextView>(R.id.textView).text = pageName

        webView = view.findViewById<WebView>(R.id.webview)

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                if (!pageWasLoaded) {
                    GlobalRumMonitor.get().addViewLoadingTime()
                    pageWasLoaded = true
                }
            }
        }
        webView.settings.javaScriptEnabled = true
        WebViewTracking.enable(webView, webViewTrackingHosts)

        return view
    }

    override fun onResume() {
        super.onResume()
        webView.loadUrl(webViewUrl)
    }

    companion object {
        internal const val ARG_PAGE_NAME: String = "com.datadog.android.sample.viewpager.PagerChildFragment.PAGE_NAME"

        internal const val ARG_WEB_VIEW_URL: String =
            "com.datadog.android.sample.viewpager.PagerChildFragment.WEB_VIEW_URL"
    }
}
