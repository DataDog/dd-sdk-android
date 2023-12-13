/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.webview

import android.webkit.WebView
import androidx.annotation.FloatRange
import androidx.annotation.MainThread
import com.datadog.android.Datadog
import com.datadog.android.api.InternalLogger
import com.datadog.android.api.SdkCore
import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.api.feature.StorageBackedFeature
import com.datadog.android.lint.InternalApi
import com.datadog.android.webview.internal.DatadogEventBridge
import com.datadog.android.webview.internal.MixedWebViewEventConsumer
import com.datadog.android.webview.internal.NoOpWebViewEventConsumer
import com.datadog.android.webview.internal.WebViewEventConsumer
import com.datadog.android.webview.internal.log.WebViewLogEventConsumer
import com.datadog.android.webview.internal.log.WebViewLogsFeature
import com.datadog.android.webview.internal.replay.ReplayWebViewEventConsumer
import com.datadog.android.webview.internal.rum.WebViewRumEventConsumer
import com.datadog.android.webview.internal.rum.WebViewRumEventContextProvider
import com.datadog.android.webview.internal.rum.WebViewRumFeature
import com.datadog.android.webview.internal.storage.NoOpDataWriter

/**
 * An entry point to Datadog WebView Tracking feature.
 */
object WebViewTracking {

    /**
     * Attach the bridge to track events from the WebView as part of the same session.
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
     * The WebView events will not be tracked unless the web page's URL Host is part of
     * the list of allowed hosts.
     *
     * @param webView the webView on which to attach the bridge.
     * @param allowedHosts a list of all the hosts that you want to track when loaded in the
     * WebView (e.g.: `listOf("example.com", "example.net")`).
     * @param logsSampleRate the sample rate for logs coming from the WebView, in percent. A value of `30` means we'll
     * send 30% of the logs. If value is `0`, no logs will be sent to Datadog. Default is 100.0 (ie: all logs are sent).
     * @param sdkCore SDK instance on which to attach the bridge.
     * [More here](https://developer.android.com/guide/webapps/webview#HandlingNavigation).
     */
    @MainThread
    @JvmOverloads
    @JvmStatic
    fun enable(
        webView: WebView,
        allowedHosts: List<String>,
        @FloatRange(from = 0.0, to = 100.0) logsSampleRate: Float = 100f,
        sdkCore: SdkCore = Datadog.getInstance()
    ) {
        if (!webView.settings.javaScriptEnabled) {
            (sdkCore as FeatureSdkCore).internalLogger.log(
                InternalLogger.Level.WARN,
                InternalLogger.Target.USER,
                { JAVA_SCRIPT_NOT_ENABLED_WARNING_MESSAGE }
            )
        }
        val webViewEventConsumer = buildWebViewEventConsumer(
            sdkCore as FeatureSdkCore,
            logsSampleRate,
            webView
        )
        val featureContext = sdkCore.getFeatureContext(Feature.SESSION_REPLAY_FEATURE_NAME)
        val privacy = featureContext[SESSION_REPLAY_PRIVACY_KEY] as? String ?: "mask"
        webView.addJavascriptInterface(
            DatadogEventBridge(
                webViewEventConsumer,
                allowedHosts,
                privacy
            ),
            DATADOG_EVENT_BRIDGE_NAME
        )
    }

    private fun buildWebViewEventConsumer(
        sdkCore: FeatureSdkCore,
        logsSampleRate: Float,
        webView: WebView
    ): WebViewEventConsumer<String> {
        val rumFeature = sdkCore.getFeature(Feature.RUM_FEATURE_NAME)
            ?.unwrap<StorageBackedFeature>()
        val logsFeature = sdkCore.getFeature(Feature.LOGS_FEATURE_NAME)
            ?.unwrap<StorageBackedFeature>()

        val webViewRumFeature = if (rumFeature != null) {
            WebViewRumFeature(sdkCore, rumFeature.requestFactory)
                .apply { sdkCore.registerFeature(this) }
        } else {
            sdkCore.internalLogger.log(
                InternalLogger.Level.INFO,
                InternalLogger.Target.USER,
                { RUM_FEATURE_MISSING_INFO }
            )
            null
        }

        val webViewLogsFeature = if (logsFeature != null) {
            WebViewLogsFeature(sdkCore, logsFeature.requestFactory)
                .apply { sdkCore.registerFeature(this) }
        } else {
            sdkCore.internalLogger.log(
                InternalLogger.Level.INFO,
                InternalLogger.Target.USER,
                { LOGS_FEATURE_MISSING_INFO }
            )
            null
        }

        val contextProvider = WebViewRumEventContextProvider(sdkCore.internalLogger)

        if (webViewLogsFeature == null && webViewRumFeature == null) {
            return NoOpWebViewEventConsumer()
        } else {
            return MixedWebViewEventConsumer(
                WebViewRumEventConsumer(
                    sdkCore = sdkCore,
                    dataWriter = webViewRumFeature?.dataWriter ?: NoOpDataWriter(),
                    contextProvider = contextProvider
                ),
                WebViewLogEventConsumer(
                    sdkCore = sdkCore,
                    userLogsWriter = webViewLogsFeature?.dataWriter ?: NoOpDataWriter(),
                    rumContextProvider = contextProvider,
                    sampleRate = logsSampleRate
                ),
                ReplayWebViewEventConsumer(sdkCore, System.identityHashCode(webView).toLong()),
                sdkCore.internalLogger
            )
        }
    }

    @InternalApi
    @Suppress(
        "UndocumentedPublicClass",
        "UndocumentedPublicFunction",
        "ClassName",
        "ClassNaming"
    )
    class _InternalWebViewProxy(sdkCore: SdkCore, webView: WebView) {
        private val consumer = buildWebViewEventConsumer(
            sdkCore as FeatureSdkCore,
            WebViewLogEventConsumer.DEFAULT_SAMPLE_RATE,
            webView
        )

        fun consumeWebviewEvent(event: String) {
            consumer.consume(event)
        }
    }

    internal const val SESSION_REPLAY_PRIVACY_KEY = "session_replay_privacy"

    internal const val JAVA_SCRIPT_NOT_ENABLED_WARNING_MESSAGE =
        "You are trying to enable the WebView" +
            "tracking but the java script capability was not enabled for the given WebView."
    internal const val DATADOG_EVENT_BRIDGE_NAME = "DatadogEventBridge"

    internal const val RUM_FEATURE_MISSING_INFO =
        "RUM feature is not registered, will ignore RUM events from WebView."
    internal const val LOGS_FEATURE_MISSING_INFO =
        "Logs feature is not registered, will ignore Log events from WebView."
}
