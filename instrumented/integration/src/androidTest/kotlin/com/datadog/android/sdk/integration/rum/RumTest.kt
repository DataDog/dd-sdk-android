/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sdk.integration.rum

import android.app.Activity
import com.datadog.android.Datadog
import com.datadog.android.rum.GlobalRumMonitor
import com.datadog.android.sdk.assertj.HeadersAssert
import com.datadog.android.sdk.integration.RuntimeConfig
import com.datadog.android.sdk.rules.HandledRequest
import com.datadog.android.sdk.rules.MockServerActivityTestRule
import com.datadog.android.sdk.utils.isRumUrl
import com.google.gson.JsonObject
import org.assertj.core.api.Assertions.assertThat
import java.lang.Long.max
import java.util.concurrent.TimeUnit

internal abstract class RumTest<R : Activity, T : MockServerActivityTestRule<R>> {

    protected abstract fun runInstrumentationScenario(mockServerRule: T): List<ExpectedEvent>

    @Suppress("NestedBlockDepth")
    protected fun verifyExpectedEvents(
        handledRequests: List<HandledRequest>,
        expectedEvents: List<ExpectedEvent>
    ) {
        val sentGestureEvents = mutableListOf<JsonObject>()
        val sentLaunchEvents = mutableListOf<JsonObject>()
        handledRequests
            .filter { it.url?.isRumUrl() ?: false }
            .forEach { request ->
                HeadersAssert.assertThat(request.headers)
                    .isNotNull
                    .hasHeader(HeadersAssert.HEADER_CT, RuntimeConfig.CONTENT_TYPE_TEXT)
                if (request.textBody != null) {
                    val rumPayload = rumPayloadToJsonList(request.textBody).filterNot {
                        it.isTelemetryEvent
                    }
                    rumPayload
                        .forEach {
                            if (it.isEventRelatedToApplicationLaunch) {
                                sentLaunchEvents += it
                            } else {
                                sentGestureEvents += it
                            }
                        }
                }
            }
        // Because launch events can be weirdly order dependent, consider them separately
        val launchEventPredicate = { event: ExpectedEvent ->
            event is ExpectedApplicationLaunchViewEvent || event is ExpectedApplicationStartActionEvent
        }
        val expectedLaunchEvents = expectedEvents.filter(launchEventPredicate)
        sentLaunchEvents
            .reduceViewEvents()
            .verifyEventMatches(expectedLaunchEvents)

        val otherExpectedEvents = expectedEvents.filterNot(launchEventPredicate)
        sentGestureEvents
            .reduceViewEvents()
            .verifyEventMatches(otherExpectedEvents)
    }

    protected fun verifyNoRumPayloadSent(
        handledRequests: List<HandledRequest>
    ) {
        val rumPayloads = handledRequests
            .filter { it.url?.isRumUrl() ?: false }
        assertThat(rumPayloads).isEmpty()
    }

    protected fun waitForPendingRUMEvents() {
        val sdkCore = Datadog.getInstance()
        val rum = GlobalRumMonitor.get(sdkCore)
        val callMethod = rum.javaClass.declaredMethods.first { it.name.startsWith("waitForPendingEvents") }
        callMethod.isAccessible = true
        callMethod.invoke(rum)
    }

    private val JsonObject.isEventRelatedToApplicationLaunch
        get() = has("view") &&
            getAsJsonObject("view")["name"].asString == "ApplicationLaunch"

    private val JsonObject.isViewEvent
        get() = get("type")?.asString == "view"

    private val JsonObject.isTelemetryEvent
        get() = get("type")?.asString == "telemetry"

    // two methods below are expected to be called only on view events
    private val JsonObject.viewId
        get() = get("view").asJsonObject.get("id").asString

    private val JsonObject.documentVersion: Long
        get() = get("_dd").asJsonObject.get("document_version").asLong

    private fun List<JsonObject>.reduceViewEvents(): List<JsonObject> {
        val maxDocVersionByViewId = mutableMapOf<String, Long>()

        forEach {
            if (it.isViewEvent) {
                val viewId = it.viewId
                val documentVersion = it.documentVersion
                maxDocVersionByViewId[viewId] =
                    max(maxDocVersionByViewId.getOrDefault(viewId, Long.MIN_VALUE), documentVersion)
            }
        }

        return filter {
            if (it.isViewEvent) {
                maxDocVersionByViewId[it.viewId] == it.documentVersion
            } else {
                true
            }
        }
    }

    companion object {
        internal val FINAL_WAIT_MS = TimeUnit.SECONDS.toMillis(60)
    }
}
