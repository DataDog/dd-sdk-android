/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.nightly.webview

import androidx.test.core.app.ActivityScenario.launch
import androidx.test.espresso.web.sugar.Web.onWebView
import androidx.test.espresso.web.webdriver.DriverAtoms.findElement
import androidx.test.espresso.web.webdriver.DriverAtoms.webClick
import androidx.test.espresso.web.webdriver.Locator
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.datadog.android.core.configuration.Configuration
import com.datadog.android.nightly.activities.WebViewTrackingActivity
import com.datadog.android.nightly.activities.WebViewTrackingBridgeHostsActivity
import com.datadog.android.nightly.rules.NightlyTestRule
import com.datadog.android.nightly.utils.initializeSdk
import com.datadog.android.nightly.utils.measureSdkInitialize
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
internal class WebViewTrackingE2ETests {

    @get:Rule
    val nightlyTestRule = NightlyTestRule()

    @After
    fun tearDown() {
        waitForWebViewEvents()
    }

    // region Tests

    /**
     * apiMethodSignature: com.datadog.android.webview.DatadogEventBridge#constructor()
     * apiMethodSignature: com.datadog.android.webview.DatadogEventBridge#fun getAllowedWebViewHosts(): String
     * apiMethodSignature: com.datadog.android.webview.DatadogEventBridge#fun send(String)
     * apiMethodSignature: com.datadog.android.core.configuration.Configuration$Builder#fun setWebViewTrackingHosts(List<String>): Builder
     */
    @Test
    fun web_view_page_view_tracking() {
        initSdk()
        launch(WebViewTrackingActivity::class.java)
    }

    /**
     * apiMethodSignature: com.datadog.android.webview.DatadogEventBridge#constructor(List<String>)
     * apiMethodSignature: com.datadog.android.webview.DatadogEventBridge#fun getAllowedWebViewHosts(): String
     * apiMethodSignature: com.datadog.android.webview.DatadogEventBridge#fun send(String)
     */
    @Test
    fun web_view_page_view_tracking_allowed_hosts_at_bridge_level() {
        val config = Configuration.Builder(
            logsEnabled = true,
            tracesEnabled = true,
            crashReportsEnabled = true,
            rumEnabled = true
        ).build()
        initSdk(config)
        launch(WebViewTrackingBridgeHostsActivity::class.java)

        // just to make sure the WebView loaded
        onWebView().withElement(findElement(Locator.ID, "change-route")).perform(webClick())
    }

    /**
     * apiMethodSignature: com.datadog.android.webview.DatadogEventBridge#constructor()
     * apiMethodSignature: com.datadog.android.webview.DatadogEventBridge#fun getAllowedWebViewHosts(): String
     * apiMethodSignature: com.datadog.android.webview.DatadogEventBridge#fun send(String)
     */
    @Test
    fun web_view_no_allowed_host_no_tracking() {
        val config = Configuration.Builder(
            logsEnabled = true,
            tracesEnabled = true,
            crashReportsEnabled = true,
            rumEnabled = true
        ).build()
        initSdk(config)
        launch(WebViewTrackingActivity::class.java)

        onWebView().withElement(findElement(Locator.ID, "make-fetch-request"))
            .perform(webClick())
    }

    /**
     * apiMethodSignature: com.datadog.android.webview.DatadogEventBridge#constructor()
     * apiMethodSignature: com.datadog.android.webview.DatadogEventBridge#fun send(String)
     * apiMethodSignature: com.datadog.android.core.configuration.Configuration$Builder#fun setWebViewTrackingHosts(List<String>): Builder
     */
    @Test
    fun web_view_action_tracking() {
        initSdk()
        launch(WebViewTrackingActivity::class.java)

        onWebView().withElement(findElement(Locator.ID, "display-image")).perform(webClick())
    }

    /**
     * apiMethodSignature: com.datadog.android.webview.DatadogEventBridge#constructor()
     * apiMethodSignature: com.datadog.android.webview.DatadogEventBridge#fun getAllowedWebViewHosts(): String
     * apiMethodSignature: com.datadog.android.webview.DatadogEventBridge#fun send(String)
     * apiMethodSignature: com.datadog.android.core.configuration.Configuration$Builder#fun setWebViewTrackingHosts(List<String>): Builder
     */
    @Test
    fun web_view_error_tracking() {
        initSdk()
        launch(WebViewTrackingActivity::class.java)

        onWebView().withElement(findElement(Locator.ID, "throw-error")).perform(webClick())
    }

    /**
     * apiMethodSignature: com.datadog.android.webview.DatadogEventBridge#constructor()
     * apiMethodSignature: com.datadog.android.webview.DatadogEventBridge#fun getAllowedWebViewHosts(): String
     * apiMethodSignature: com.datadog.android.webview.DatadogEventBridge#fun send(String)
     * apiMethodSignature: com.datadog.android.core.configuration.Configuration$Builder#fun setWebViewTrackingHosts(List<String>): Builder
     */
    @Test
    fun web_view_resource_tracking() {
        initSdk()
        launch(WebViewTrackingActivity::class.java)

        onWebView().withElement(findElement(Locator.ID, "make-xhr-request"))
            .perform(webClick())
    }

    /**
     * apiMethodSignature: com.datadog.android.webview.DatadogEventBridge#constructor()
     * apiMethodSignature: com.datadog.android.webview.DatadogEventBridge#fun getAllowedWebViewHosts(): String
     * apiMethodSignature: com.datadog.android.webview.DatadogEventBridge#fun send(String)
     * apiMethodSignature: com.datadog.android.core.configuration.Configuration$Builder#fun setWebViewTrackingHosts(List<String>): Builder
     */
    @Test
    fun web_view_log_tracking() {
        initSdk()
        launch(WebViewTrackingActivity::class.java)

        onWebView().withElement(findElement(Locator.ID, "send-log")).perform(webClick())
    }

    // endregion

    // region Internal

    private fun initSdk(
        config: Configuration = Configuration.Builder(
            logsEnabled = true,
            tracesEnabled = true,
            crashReportsEnabled = true,
            rumEnabled = true
        ).setWebViewTrackingHosts(listOf("datadoghq.dev")).build()
    ) {

        measureSdkInitialize {
            initializeSdk(
                InstrumentationRegistry.getInstrumentation().targetContext,
                config = config
            )
        }
    }

    private fun waitForWebViewEvents() {
        Thread.sleep(30000)
    }

    // endregion
}
