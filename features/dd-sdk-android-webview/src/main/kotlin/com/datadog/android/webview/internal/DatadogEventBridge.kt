/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.webview.internal

import android.webkit.JavascriptInterface
import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.core.configuration.HostsSanitizer
import com.datadog.android.core.sampling.DeterministicSampler
import com.datadog.android.internal.sampling.DeterministicSampling
import com.datadog.android.internal.sampling.SessionSamplingIdProvider
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
    private val privacyLevel: String,
    private val sdkCore: FeatureSdkCore
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

    /**
     * Called from the browser-sdk to get the privacy level of the session replay feature.
     * @return the privacy level as a String ("allow", "mask", "mask_user_input")
     */
    @JavascriptInterface
    fun getPrivacyLevel(): String {
        return privacyLevel
    }

    /**
     *  Called from the browser-sdk to know the capabilities supported by this version of the bridge.
     *  @return the capabilities as an array of Strings.
     */
    @JavascriptInterface
    fun getCapabilities(): String {
        return capabilities.toString()
    }

    /**
     * Called from the browser-sdk to get the trace sampling decision from the native SDK.
     * Returns 'true', 'false', or 'null' as a string:
     * - 'true' if traces should be sampled
     * - 'false' if traces should not be sampled
     * - 'null' if no decision can be made (no active session, or no tracing configured)
     */
    @JavascriptInterface
    fun getIsTraceSampled(): String {
        val rumContext = sdkCore.getFeatureContext(Feature.RUM_FEATURE_NAME, useContextThread = false)
        val sessionId = rumContext[SESSION_ID_KEY] as? String ?: return NULL_STRING
        val sessionState = rumContext[SESSION_STATE_KEY] as? String
        if (sessionState != TRACKED_STATE) return NULL_STRING

        val sessionSampleRate = (rumContext[SESSION_SAMPLE_RATE_KEY] as? Number)?.toFloat()
            ?: return NULL_STRING

        val tracingContext = sdkCore.getFeatureContext(Feature.TRACING_FEATURE_NAME, useContextThread = false)
        val traceSampleRate = (tracingContext[TRACE_SAMPLE_RATE_KEY] as? Number)?.toFloat()
            ?: return NULL_STRING

        val combinedRate = DeterministicSampling.combinedSampleRate(sessionSampleRate, traceSampleRate)
        val sampler = DeterministicSampler<String>(
            SessionSamplingIdProvider::provideId,
            combinedRate
        )

        return if (sampler.sample(sessionId)) TRUE_STRING else FALSE_STRING
    }

    // endregion

    companion object {
        internal const val WEB_VIEW_TRACKING_FEATURE_NAME = "WebView"

        private const val SESSION_ID_KEY = "session_id"
        private const val SESSION_STATE_KEY = "session_state"
        private const val SESSION_SAMPLE_RATE_KEY = "session_sample_rate"
        private const val TRACKED_STATE = "TRACKED"
        private const val TRACE_SAMPLE_RATE_KEY = "okhttp_interceptor_sample_rate"

        private const val TRUE_STRING = "true"
        private const val FALSE_STRING = "false"
        private const val NULL_STRING = "null"

        internal val capabilities = JsonArray().apply {
            add("records")
        }
    }
}
