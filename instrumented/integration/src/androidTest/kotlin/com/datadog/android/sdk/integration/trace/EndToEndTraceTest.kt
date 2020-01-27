/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2020 Datadog, Inc.
 */

package com.datadog.android.sdk.integration.trace

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.datadog.android.sdk.assertj.HeadersAssert
import com.datadog.android.sdk.assertj.HeadersAssert.Companion.assertThat
import com.datadog.android.sdk.integration.RuntimeConfig
import com.datadog.android.sdk.rules.MockServerActivityTestRule
import com.datadog.tools.unit.assertj.JsonObjectAssert.Companion.assertThat
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import datadog.opentracing.DDSpan
import java.lang.Long
import java.util.concurrent.TimeUnit
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
internal class EndToEndTraceTest {

    @get:Rule
    val mockServerRule = MockServerActivityTestRule(
        ActivityLifecycleTrace::class.java,
        keepRequests = true
    )

    @Test
    fun verifyExpectedActivityLogs() {

        // Wait to make sure all batches are consumed
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()

        // put activity in background
        instrumentation.runOnMainSync {
            instrumentation
                .callActivityOnPause(mockServerRule.activity)
            instrumentation
                .callActivityOnStop(mockServerRule.activity)
        }

        instrumentation.waitForIdleSync()
        Thread.sleep(INITIAL_WAIT_MS)

        // Check sent requests
        checkSentRequests()
    }

    private fun checkSentRequests() {
        val requests = mockServerRule.getRequests()
        val spansObjects = mutableListOf<JsonObject>()
        requests.forEach { request ->
            assertThat(request.headers)
                .isNotNull
                .hasHeader(HeadersAssert.HEADER_CT, RuntimeConfig.DD_CONTENT_TYPE)
            val sentSpans =
                if (request.textBody != null) tracesPayloadToJsonArray(request.textBody)
                else JsonArray()
            sentSpans.forEach {
                Log.i("EndToEndTraceTest", "adding span $it")
                spansObjects.add(it.asJsonObject)
            }
        }

        spansObjects.forEach {
            it.assertMatches(mockServerRule.activity.getSentSpans().removeFirst())
        }
    }

    // region Internal

    private fun JsonObject.assertMatches(span: DDSpan) {
        assertThat(this)
            .hasField(START_TIMESTAMP_KEY, span.startTime)
            .hasField(DURATION_KEY, span.durationNano)
            .hasField(SERVICE_NAME_KEY, span.serviceName)
            .hasField(TRACE_ID_KEY, Long.toHexString((span.traceId.toLong())))
            .hasField(SPAN_ID_KEY, Long.toHexString((span.spanId.toLong())))
            .hasField(PARENT_ID_KEY, Long.toHexString((span.parentId.toLong())))
            .hasField(RESOURCE_KEY, span.resourceName)
            .hasField(OPERATION_NAME_KEY, span.operationName)
            .hasField(META_KEY, span.meta)
            .hasField(METRICS_KEY, span.metrics)
    }

    private fun tracesPayloadToJsonArray(payload: String): JsonArray {
        val container = JsonParser.parseString(payload).asJsonObject
        return container.getAsJsonArray("spans")
    }

    // endregion

    companion object {
        const val START_TIMESTAMP_KEY = "start"
        const val DURATION_KEY = "duration"
        const val SERVICE_NAME_KEY = "service"
        const val TRACE_ID_KEY = "trace_id"
        const val SPAN_ID_KEY = "span_id"
        const val PARENT_ID_KEY = "parent_id"
        const val RESOURCE_KEY = "resource"
        const val OPERATION_NAME_KEY = "name"
        const val META_KEY = "meta"
        const val METRICS_KEY = "metrics"
        private val INITIAL_WAIT_MS = TimeUnit.SECONDS.toMillis(30)
    }
}
