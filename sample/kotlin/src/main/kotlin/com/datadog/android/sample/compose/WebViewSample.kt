/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sample.compose

import android.annotation.SuppressLint
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ImageView
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.datadog.android.rum.ExperimentalRumApi
import com.datadog.android.rum.GlobalRumMonitor
import com.datadog.android.sample.R
import com.datadog.android.webview.WebViewTracking

private val webViewTrackingHosts = listOf(
    "datadoghq.dev"
)

@OptIn(ExperimentalRumApi::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun InteropViewSample() {
    Column {
        Text(
            text = "ImageView"
        )
        AndroidView(
            modifier = Modifier.height(120.dp),
            factory = { context ->
                ImageView(context).apply {
                    setImageResource(R.drawable.ic_dd_icon_red)
                }
            },
            update = {
            }
        )
        Text(
            text = "WebView"
        )
        AndroidView(
            modifier = Modifier.height(240.dp),
            factory = { context ->
                WebView(context).apply {
                    settings.javaScriptEnabled = true
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            GlobalRumMonitor.get().addViewLoadingTime(overwrite = false)
                        }
                    }
                    WebViewTracking.enable(this, webViewTrackingHosts)
                    settings.loadWithOverviewMode = true
                    settings.useWideViewPort = true
                    settings.setSupportZoom(true)
                }
            },
            update = { webView ->
                webView.loadUrl("https://datadoghq.dev/browser-sdk-test-playground/webview-support/#click_event")
            }
        )
    }
}

@Preview(showBackground = true)
@Composable
internal fun PreviewAndroidViewSample() {
    InteropViewSample()
}
