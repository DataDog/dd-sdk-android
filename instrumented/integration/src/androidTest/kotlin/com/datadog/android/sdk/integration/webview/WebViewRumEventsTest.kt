/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sdk.integration.webview

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.datadog.android.Datadog
import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.privacy.TrackingConsent
import com.datadog.android.rum.GlobalRumMonitor
import com.datadog.android.sdk.integration.RuntimeConfig
import com.datadog.android.sdk.integration.rum.RumTest
import com.datadog.android.sdk.integration.rum.rumPayloadToJsonList
import com.datadog.android.sdk.rules.RumMockServerActivityTestRule
import com.datadog.tools.unit.ConditionWatcher
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import org.assertj.core.api.Assertions.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
@LargeTest
internal class WebViewRumEventsTest {

    @get:Rule
    val mockServerRule = RumMockServerActivityTestRule(
        WebViewBridgePlaygroundActivity::class.java,
        keepRequests = true,
        trackingConsent = TrackingConsent.GRANTED
    )

    @Test
    fun verifyWebViewEventSessionHasTimeseries() {
        // Given
        GlobalRumMonitor.get().startView("view-1", "TestView")
        ConditionWatcher { currentRumContext()[SESSION_STATE_KEY] == "TRACKED" }
            .doWait(timeoutMs = RumTest.FINAL_WAIT_MS)
        val sessionId = currentRumContext()[SESSION_ID_KEY]
        ConditionWatcher {
            refreshRumContext()
            currentRumContext()[SESSION_HAS_TIMESERIES_KEY] == true
        }.doWait(timeoutMs = RumTest.FINAL_WAIT_MS)
        val webViewId = UUID.randomUUID().toString()

        // When
        sendWebViewEvent(webViewId)

        // Then
        var webViewEvent: JsonObject? = null
        ConditionWatcher {
            webViewEvent = rumEvents().firstOrNull { it.viewId == webViewId }
            webViewEvent != null
        }.doWait(timeoutMs = RumTest.FINAL_WAIT_MS)
        val session = checkNotNull(webViewEvent).getAsJsonObject(SESSION_KEY)
        assertThat(session.get(ID_KEY).asString).isEqualTo(sessionId)
        assertThat(session.get(HAS_TIMESERIES_KEY)).isEqualTo(JsonPrimitive(true))
        assertThat(session.get(IS_MAIN_PROCESS_KEY)).isEqualTo(JsonPrimitive(true))
    }

    private fun sendWebViewEvent(viewId: String) {
        val bundledEvent = JsonObject().apply {
            addProperty("eventType", "view")
            add(
                "event",
                JsonObject().apply {
                    addProperty("date", System.currentTimeMillis())
                    addProperty("type", "view")
                    add("application", JsonObject().apply { addProperty(ID_KEY, UUID.randomUUID().toString()) })
                    add(SESSION_KEY, JsonObject().apply { addProperty(ID_KEY, UUID.randomUUID().toString()) })
                    add(
                        VIEW_KEY,
                        JsonObject().apply {
                            addProperty(ID_KEY, viewId)
                            addProperty("url", "https://localhost/")
                        }
                    )
                    add("_dd", JsonObject().apply { addProperty("document_version", 1) })
                }
            )
        }
        val script = "DatadogEventBridge.send(${JsonPrimitive(bundledEvent.toString())})"
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            mockServerRule.activity.webView.evaluateJavascript(script, null)
        }
    }

    private fun currentRumContext(): Map<String, Any?> =
        (Datadog.getInstance() as FeatureSdkCore).getFeatureContext(Feature.RUM_FEATURE_NAME)

    // The RUM feature context is republished only after RUM handles an event, not when a timeseries
    // data point is collected. Sends the same no-op notification the WebView bridge sends before each event.
    private fun refreshRumContext() {
        (Datadog.getInstance() as FeatureSdkCore).getFeature(Feature.RUM_FEATURE_NAME)
            ?.sendEvent(mapOf("type" to WEB_VIEW_INGESTED_NOTIFICATION))
    }

    private fun rumEvents(): List<JsonObject> = mockServerRule
        .getRequests(RuntimeConfig.rumEndpointUrl)
        .flatMap { request -> request.textBody?.let(::rumPayloadToJsonList).orEmpty() }

    private val JsonObject.viewId: String?
        get() = getAsJsonObject(VIEW_KEY)?.get(ID_KEY)?.asString

    companion object {
        private const val SESSION_STATE_KEY = "session_state"
        private const val SESSION_ID_KEY = "session_id"
        private const val SESSION_HAS_TIMESERIES_KEY = "session_has_timeseries"
        private const val WEB_VIEW_INGESTED_NOTIFICATION = "web_view_ingested_notification"
        private const val SESSION_KEY = "session"
        private const val VIEW_KEY = "view"
        private const val ID_KEY = "id"
        private const val HAS_TIMESERIES_KEY = "has_timeseries"
        private const val IS_MAIN_PROCESS_KEY = "is_main_process"
    }
}
