/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.webview

import android.webkit.JavascriptInterface
import com.datadog.android.core.internal.CoreFeature
import com.datadog.android.rum.internal.RumFeature

/**
 * This [JavascriptInterface] is used to intercept all the Datadog events produced by
 * the displayed web content when there is already a Datadog browser-sdk attached.
 * The goal is to make those events part of the mobile session.
 * Please note that the WebView will not be tracked unless you added the host name in the hosts
 * lists in the SDK configuration.
 * @see [com.datadog.android.core.configuration.Configuration.Builder]
 */
class DatadogEventBridge {

    private val webEventConsumer =
        WebEventConsumer(
            RumEventConsumer(
                RumFeature.persistenceStrategy.getWriter(),
                CoreFeature.timeProvider
            ),
        )

    @JavascriptInterface
    fun sendEvent(event: String) {
        webEventConsumer.consume(event)
    }

    @JavascriptInterface
    fun allowWebViewOrigins(): String {
        return CoreFeature.webViewTrackingHosts.toString()
    }
}
