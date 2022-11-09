/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sdk.integration.rum

import android.app.Activity
import com.datadog.android.sdk.assertj.HeadersAssert
import com.datadog.android.sdk.integration.RuntimeConfig
import com.datadog.android.sdk.rules.HandledRequest
import com.datadog.android.sdk.rules.MockServerActivityTestRule
import com.datadog.android.sdk.utils.isRumUrl
import com.google.gson.JsonObject
import org.assertj.core.api.Assertions.assertThat
import java.util.concurrent.TimeUnit

internal abstract class RumTest<R : Activity, T : MockServerActivityTestRule<R>> {

    protected abstract fun runInstrumentationScenario(mockServerRule: T): List<ExpectedEvent>

    protected fun verifyExpectedEvents(
        handledRequests: List<HandledRequest>,
        expectedEvents: List<ExpectedEvent>
    ) {
        val sentGestureEvents = mutableListOf<JsonObject>()
        handledRequests
            .filter { it.url?.isRumUrl() ?: false }
            .forEach { request ->
                HeadersAssert.assertThat(request.headers)
                    .isNotNull
                    .hasHeader(HeadersAssert.HEADER_CT, RuntimeConfig.CONTENT_TYPE_TEXT)
                if (request.textBody != null) {
                    val rumPayload = rumPayloadToJsonList(request.textBody).filterNot {
                        it.has("type") &&
                            it.getAsJsonPrimitive("type").asString == "telemetry"
                    }
                    sentGestureEvents += rumPayload
                }
            }
        sentGestureEvents.verifyEventMatches(expectedEvents)
    }

    protected fun verifyNoRumPayloadSent(
        handledRequests: List<HandledRequest>
    ) {
        val rumPayloads = handledRequests
            .filter { it.url?.isRumUrl() ?: false }
        assertThat(rumPayloads).isEmpty()
    }

    companion object {
        internal val FINAL_WAIT_MS = TimeUnit.SECONDS.toMillis(30)
    }
}
