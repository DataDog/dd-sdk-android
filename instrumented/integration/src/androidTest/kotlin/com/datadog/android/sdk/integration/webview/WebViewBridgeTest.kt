/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sdk.integration.webview

import android.webkit.WebView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.datadog.android.rum.GlobalRumMonitor
import com.datadog.android.sdk.rules.MockServerActivityTestRule
import org.assertj.core.api.Assertions.fail
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
internal class WebViewBridgeTest {

    @get:Rule
    val mockServerRule = MockServerActivityTestRule(WebViewBridgePlaygroundActivity::class.java)

    @Test
    fun testGetIsTraceSampledReturnsNullAfterSessionStop() {
        val webView = mockServerRule.activity.webView

        GlobalRumMonitor.get().startView("view-1", "TestView")
        assertBridgeEventually(webView, "true")

        GlobalRumMonitor.get().stopSession()
        assertBridgeEventually(webView, "null")
    }

    @Test
    fun testGetIsTraceSampledUpdatesDecisionOnSessionRollover() {
        val webView = mockServerRule.activity.webView

        GlobalRumMonitor.get().startView("view-1", "TestView")
        assertBridgeEventually(webView, "true")

        GlobalRumMonitor.get().stopSession()
        assertBridgeEventually(webView, "null")

        GlobalRumMonitor.get().startView("view-2", "TestView2")
        assertBridgeEventually(webView, "true")
    }

    // Polls getIsTraceSampled() until it matches the expected value. Retrying is necessary
    // because RUM events are processed on a background thread, so state changes from
    // startView()/stopSession() aren't immediately visible to the bridge.
    private fun assertBridgeEventually(
        webView: WebView,
        expectedResult: String,
        timeoutMs: Long = 1_000
    ) {
        val deadline = System.currentTimeMillis() + timeoutMs
        var lastActual: String? = null

        while (System.currentTimeMillis() < deadline) {
            val latch = CountDownLatch(1)
            var actual: String? = null
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                webView.evaluateJavascript(GET_IS_TRACE_SAMPLED) { value ->
                    // evaluateJavascript returns JSON-encoded results — strip surrounding quotes
                    // for string values so "\"true\"" becomes "true".
                    actual = value?.removeSurrounding("\"")
                    latch.countDown()
                }
            }
            latch.await(5, TimeUnit.SECONDS)
            lastActual = actual
            if (actual == expectedResult) return
            Thread.sleep(100)
        }

        @Suppress("UNCHECKED_CAST")
        fail<Unit>(
            "Expected getIsTraceSampled() to return '$expectedResult' " +
                "but got '$lastActual' after ${timeoutMs}ms"
        )
    }

    companion object {
        private const val GET_IS_TRACE_SAMPLED = "DatadogEventBridge.getIsTraceSampled()"
    }
}
