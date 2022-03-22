/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.webview

import android.webkit.JavascriptInterface
import com.datadog.android.core.configuration.Configuration
import com.datadog.android.core.internal.CoreFeature
import com.datadog.android.webview.internal.MixedWebViewEventConsumer
import com.datadog.android.webview.internal.WebViewEventConsumer
import com.datadog.android.webview.internal.log.WebViewLogEventConsumer
import com.datadog.android.webview.internal.log.WebViewLogsFeature
import com.datadog.android.webview.internal.rum.WebViewRumEventConsumer
import com.datadog.android.webview.internal.rum.WebViewRumEventContextProvider
import com.datadog.android.webview.internal.rum.WebViewRumFeature
import com.google.gson.JsonArray

/**
 * This [JavascriptInterface] is used to intercept all the Datadog events produced by
 * the displayed web page (if Datadog's browser-sdk is enabled).
 * The goal is to make those events part of a unique mobile session.
 * Please note that the WebView events will not be tracked unless the web page's URL Host is part of
 * the list defined in the global [Configuration], or in this constructor.
 * @param allowedHosts a list of all the hosts that you want to track when loaded in the
 * WebView.
 * @see [Configuration.Builder.setWebViewTrackingHosts]
 */
class DatadogEventBridge
internal constructor(
    internal val webViewEventConsumer: WebViewEventConsumer<String>,
    private val allowedHosts: List<String>
) {

    /**
     * This [JavascriptInterface] is used to intercept all the Datadog events produced by
     * the displayed web page (if Datadog's browser-sdk is enabled).
     * The goal is to make those events part of a unique mobile session.
     * Please note that the WebView events will not be tracked unless the web page's URL Host is part of
     * the list defined in the global [Configuration], or in the constructor.
     * @see [Configuration.Builder.setWebViewTrackingHosts]
     */
    constructor() : this(
        buildWebViewEventConsumer(),
        emptyList()
    )

    /**
     * This [JavascriptInterface] is used to intercept all the Datadog events produced by
     * the displayed web page (if Datadog's browser-sdk is enabled).
     * The goal is to make those events part of a unique mobile session.
     * Please note that the WebView events will not be tracked unless the web page's URL Host is part of
     * the list defined in the global [Configuration], or in the constructor.
     * @param allowedHosts a list of all the hosts that you want to track when loaded in the
     * WebView (e.g.: `listOf("example.com", "example.net")`).
     * @see [Configuration.Builder.setWebViewTrackingHosts]
     */
    constructor(allowedHosts: List<String>) : this(
        buildWebViewEventConsumer(),
        allowedHosts
    )

    // region Bridge

    /**
     * Called from the browser-sdk side whenever there is a new RUM/LOG event
     * available related with the tracked WebView.
     * @param event as the bundled web event as a Json string
     */
    @JavascriptInterface
    fun send(event: String) {
        webViewEventConsumer.consume(event)
    }

    /**
     * Called from the browser-sdk to get the list of hosts for which the WebView tracking is
     * allowed.
     * @return the list of hosts as a String JsonArray
     */
    @JavascriptInterface
    fun getAllowedWebViewHosts(): String {
        // We need to use a JsonArray here otherwise it cannot be parsed on the JS side
        val origins = JsonArray()
        allowedHosts.forEach {
            origins.add(it)
        }
        CoreFeature.webViewTrackingHosts.forEach {
            origins.add(it)
        }
        return origins.toString()
    }

    // endregion

    companion object {
        private fun buildWebViewEventConsumer(): WebViewEventConsumer<String> {
            val contextProvider = WebViewRumEventContextProvider()
            return MixedWebViewEventConsumer(
                WebViewRumEventConsumer(
                    dataWriter = WebViewRumFeature.persistenceStrategy.getWriter(),
                    timeProvider = CoreFeature.timeProvider,
                    contextProvider = contextProvider
                ),
                WebViewLogEventConsumer(
                    userLogsWriter = WebViewLogsFeature.persistenceStrategy.getWriter(),
                    rumContextProvider = contextProvider,
                    timeProvider = CoreFeature.timeProvider
                )
            )
        }
    }
}
