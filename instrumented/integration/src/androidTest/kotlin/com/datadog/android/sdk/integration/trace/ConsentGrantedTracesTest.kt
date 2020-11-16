/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sdk.integration.trace

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.datadog.android.privacy.TrackingConsent
import com.datadog.android.sdk.assertj.HeadersAssert
import com.datadog.android.sdk.assertj.HeadersAssert.Companion.assertThat
import com.datadog.android.sdk.integration.RuntimeConfig
import com.datadog.android.sdk.rules.MockServerActivityTestRule
import com.datadog.android.sdk.utils.isLogsUrl
import com.datadog.android.sdk.utils.isTracesUrl
import com.datadog.tools.unit.assertj.JsonObjectAssert.Companion.assertThat
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import datadog.opentracing.DDSpan
import java.lang.Long
import java.util.LinkedList
import java.util.concurrent.TimeUnit
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.data.Offset
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
internal class ConsentGrantedTracesTest {

    @get:Rule
    val mockServerRule = MockServerActivityTestRule(
        ActivityLifecycleTrace::class.java,
        keepRequests = true,
        trackingConsent = TrackingConsent.GRANTED
    )

    @Test
    fun verifyExpectedActivitySpans() {

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
        checkSentRequestsForSpans()
        checkSentRequestsForLogs()
    }

    private fun checkSentRequestsForSpans() {
        val requests = mockServerRule.getRequests()
        val spansObjects = mutableListOf<JsonObject>()
        requests
            .filter { it.url?.isTracesUrl() ?: false }
            .forEach { request ->
                assertThat(request.headers)
                    .isNotNull
                    .hasHeader(HeadersAssert.HEADER_CT, RuntimeConfig.CONTENT_TYPE_TEXT)
                val sentSpans = tracesPayloadToJsonArray(request.textBody.orEmpty())
                sentSpans.forEach {
                    Log.i("EndToEndTraceTest", "adding span $it")
                    spansObjects.add(it.asJsonObject)
                }
            }
        val sentSpans = mockServerRule.activity.getSentSpans()
        assertThat(sentSpans.size).isEqualTo(spansObjects.size)
        sentSpans.forEach { span ->
            val json = spansObjects.first {
                it.get(TRACE_ID_KEY).asString == Long.toHexString((span.traceId.toLong())) &&
                    it.get(SPAN_ID_KEY).asString == Long.toHexString((span.spanId.toLong()))
            }
            assertMatches(json, span)
        }
    }

    private fun checkSentRequestsForLogs() {
        val requests = mockServerRule.getRequests()
        val logObjects = LinkedList<JsonObject>()
        requests
            .filter { it.url?.isLogsUrl() ?: false }
            .forEach { request ->
                assertThat(request.headers)
                    .isNotNull
                    .hasHeader(HeadersAssert.HEADER_CT, RuntimeConfig.CONTENT_TYPE_JSON)
                request.jsonBody!!.asJsonArray.forEach {
                    Log.i("EndToEndTraceTest", "adding log $it")
                    logObjects.add(it.asJsonObject)
                }
            }
        val sentLogs = mockServerRule.activity.getSentLogs()
        sentLogs.forEach {
            val toMatch = logObjects.removeFirst()
            assertThat(toMatch)
                .hasField(TAG_STATUS, levels[it.first])
                .hasField(TAG_EVENT, it.second)
                .hasField(TAG_MESSAGE, "Span event")
                .hasField(TAG_LOGGER_NAME, "trace")
        }
    }

    // region Internal

    private fun assertMatches(jsonObject: JsonObject, span: DDSpan) {
        assertThat(jsonObject)
            .hasField(SERVICE_NAME_KEY, span.serviceName)
            .hasField(TRACE_ID_KEY, Long.toHexString((span.traceId.toLong())))
            .hasField(SPAN_ID_KEY, Long.toHexString((span.spanId.toLong())))
            .hasField(PARENT_ID_KEY, Long.toHexString((span.parentId.toLong())))
            .hasField(
                START_TIMESTAMP_KEY,
                span.startTime,
                Offset.offset(TimeUnit.MINUTES.toNanos(1))
            )
            .hasField(DURATION_KEY, span.durationNano)
            .hasField(RESOURCE_KEY, span.resourceName)
            .hasField(OPERATION_NAME_KEY, span.operationName)
            .hasField(META_KEY, span.meta)
            .hasField(METRICS_KEY, span.metrics)
    }

    private fun tracesPayloadToJsonArray(payload: String): List<JsonElement> {
        return payload.split('\n').mapNotNull {
            if (it.isEmpty()) {
                null
            } else {
                JsonParser.parseString(it).asJsonObject
            }
        }.flatMap {
            it.getAsJsonArray("spans").toList()
        }
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
        private val INITIAL_WAIT_MS = TimeUnit.SECONDS.toMillis(60)

        private const val TAG_STATUS = "status"
        private const val TAG_MESSAGE = "message"
        private const val TAG_EVENT = "event"
        private const val TAG_LOGGER_NAME = "logger.name"
        private val levels = arrayOf(
            "debug",
            "debug",
            "trace",
            "debug",
            "info",
            "warn",
            "error",
            "critical"
        )
    }
}
