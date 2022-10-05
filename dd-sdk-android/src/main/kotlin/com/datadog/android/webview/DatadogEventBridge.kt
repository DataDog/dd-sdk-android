/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.webview

import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.annotation.MainThread
import com.datadog.android.Datadog
import com.datadog.android.core.configuration.Configuration
import com.datadog.android.core.internal.utils.devLogger
import com.datadog.android.v2.core.DatadogCore
import com.datadog.android.webview.internal.MixedWebViewEventConsumer
import com.datadog.android.webview.internal.NoOpWebViewEventConsumer
import com.datadog.android.webview.internal.WebViewEventConsumer
import com.datadog.android.webview.internal.log.WebViewLogEventConsumer
import com.datadog.android.webview.internal.rum.WebViewRumEventConsumer
import com.datadog.android.webview.internal.rum.WebViewRumEventContextProvider
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
        val coreFeature = (Datadog.globalSdkCore as? DatadogCore)?.coreFeature
        coreFeature?.webViewTrackingHosts?.forEach {
            origins.add(it)
        }
        return origins.toString()
    }

    // endregion

    companion object {

        internal const val JAVA_SCRIPT_NOT_ENABLED_WARNING_MESSAGE =
            "You are trying to enable the WebView" +
                "tracking but the java script capability was not enabled for the given WebView."
        internal const val DATADOG_EVENT_BRIDGE_NAME = "DatadogEventBridge"

        /**
         * Attach the [DatadogEventBridge] to track events from the WebView as part of the same session.
         * This method must be called from the Main Thread.
         * Please note that:
         * - you need to enable the JavaScript support in the WebView settings for this feature
         * to be functional:
         * ```
         * webView.settings.javaScriptEnabled = true
         * ```
         * - by default, navigation will happen outside of your application (in a browser or a different app). To prevent that and ensure Datadog can track the full WebView user journey, attach a [android.webkit.WebViewClient] to your WebView, as follow:
         * ```
         * webView.webViewClient = WebViewClient()
         * ```
         * @param webView the webView on which to attach the bridge
         * [More here](https://developer.android.com/guide/webapps/webview#HandlingNavigation).
         */
        @MainThread
        fun setup(webView: WebView) {
            if (!webView.settings.javaScriptEnabled) {
                devLogger.w(JAVA_SCRIPT_NOT_ENABLED_WARNING_MESSAGE)
            }
            webView.addJavascriptInterface(DatadogEventBridge(), DATADOG_EVENT_BRIDGE_NAME)
        }

        private fun buildWebViewEventConsumer(): WebViewEventConsumer<String> {
            val datadogCore = Datadog.globalSdkCore as? DatadogCore
            val coreFeature = datadogCore?.coreFeature
            val webViewRumFeature = datadogCore?.webViewRumFeature
            val webViewLogsFeature = datadogCore?.webViewLogsFeature
            val contextProvider = WebViewRumEventContextProvider()

            if (coreFeature == null || webViewLogsFeature == null || webViewRumFeature == null) {
                return NoOpWebViewEventConsumer()
            } else {
                return MixedWebViewEventConsumer(
                    WebViewRumEventConsumer(
                        dataWriter = webViewRumFeature.persistenceStrategy.getWriter(),
                        timeProvider = coreFeature.timeProvider,
                        contextProvider = contextProvider
                    ),
                    WebViewLogEventConsumer(
                        userLogsWriter = webViewLogsFeature.persistenceStrategy.getWriter(),
                        rumContextProvider = contextProvider,
                        timeProvider = coreFeature.timeProvider,
                        coreFeature = coreFeature
                    )
                )
            }
        }
    }
}
