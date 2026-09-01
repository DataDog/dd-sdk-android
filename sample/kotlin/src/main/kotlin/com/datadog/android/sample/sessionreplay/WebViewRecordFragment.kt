/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.sample.sessionreplay

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import com.datadog.android.rum.ExperimentalRumApi
import com.datadog.android.rum.GlobalRumMonitor
import com.datadog.android.sample.R
import com.datadog.android.webview.WebViewTracking

internal class WebViewRecordFragment : Fragment(), MenuProvider {

    private lateinit var webView: WebView
    private lateinit var startCustomRumViewButton: Button
    private val webViewTrackingHosts = listOf(
        "datadoghq.dev"
    )

    @OptIn(ExperimentalRumApi::class)
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
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                GlobalRumMonitor.get().addViewLoadingTime(overwrite = false)
            }
        }
        webView.settings.javaScriptEnabled = true
        WebViewTracking.enable(webView, webViewTrackingHosts)
        startCustomRumViewButton.setOnClickListener {
            GlobalRumMonitor.get().startView(this, "Custom RUM View")
        }
        // add webview on load listener
        return rootView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        requireActivity().addMenuProvider(this, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }

    override fun onResume() {
        super.onResume()
        webView.loadUrl("https://datadoghq.dev/browser-sdk-test-playground/webview-support/#click_event")
    }

    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
        menuInflater.inflate(R.menu.sr_webview, menu)
        if (webView.visibility == View.VISIBLE) {
            menu.findItem(R.id.webview_show).isVisible = false
        } else {
            menu.findItem(R.id.webview_hide).isVisible = false
        }
    }

    override fun onMenuItemSelected(item: MenuItem): Boolean {
        val newVisibility = when (item.itemId) {
            R.id.webview_show -> View.VISIBLE
            R.id.webview_hide -> View.GONE
            else -> null
        }

        return if (newVisibility == null) {
            false
        } else {
            webView.visibility = newVisibility
            requireActivity().invalidateMenu()
            true
        }
    }
}
