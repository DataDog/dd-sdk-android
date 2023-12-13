/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.webview.internal

import android.webkit.JavascriptInterface
import com.datadog.android.core.configuration.HostsSanitizer
import com.google.gson.JsonArray

/**
 * This [JavascriptInterface] is used to intercept all the Datadog events produced by
 * the displayed web page (if Datadog's browser-sdk is enabled).
 * The goal is to make those events part of a unique mobile session.
 * Please note that the WebView events will not be tracked unless the web page's URL Host is part of
 * the list in the constructor.
 */
internal class DatadogEventBridge(
    internal val webViewEventConsumer: WebViewEventConsumer<String>,
    private val allowedHosts: List<String>,
    private val sessionReplayPrivacy: String
) {

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
        HostsSanitizer()
            .sanitizeHosts(allowedHosts, WEB_VIEW_TRACKING_FEATURE_NAME)
            .forEach {
                origins.add(it)
            }
        return origins.toString()
    }

    @JavascriptInterface
    fun getPrivacyLevel(): String {
        return sessionReplayPrivacy
    }

    // endregion

    companion object {
        internal const val WEB_VIEW_TRACKING_FEATURE_NAME = "WebView"
    }
}
