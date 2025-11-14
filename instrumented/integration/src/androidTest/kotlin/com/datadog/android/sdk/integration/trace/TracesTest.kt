/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sdk.integration.trace

import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import com.datadog.android.api.context.DatadogContext
import com.datadog.android.sdk.assertj.HeadersAssert
import com.datadog.android.sdk.assertj.HeadersAssert.Companion.assertThat
import com.datadog.android.sdk.integration.RuntimeConfig
import com.datadog.android.sdk.rules.HandledRequest
import com.datadog.android.sdk.rules.MockServerActivityTestRule
import com.datadog.android.sdk.utils.isLogsUrl
import com.datadog.android.sdk.utils.isTracesUrl
import com.datadog.android.trace.api.span.DatadogSpan
import com.datadog.android.trace.internal.DatadogTracingToolkit
import com.datadog.tools.unit.assertj.JsonObjectAssert.Companion.assertThat
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.data.Offset
import java.util.LinkedList
import java.util.concurrent.TimeUnit

internal abstract class TracesTest {

    protected fun runInstrumentationScenario(
        mockServerRule: MockServerActivityTestRule<ActivityLifecycleTrace>
    ) {
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
    }

    protected fun verifyExpectedSpans(
        context: DatadogContext,
        handledRequests: List<HandledRequest>,
        expectedSpans: List<DatadogSpan>
    ) {
        val sentSpansObjects = mutableListOf<JsonObject>()
        handledRequests
            .filter { it.url?.isTracesUrl() ?: false }
            .forEach { request ->
                assertThat(request.headers)
                    .isNotNull
                    .hasHeader(HeadersAssert.HEADER_CT, RuntimeConfig.CONTENT_TYPE_TEXT)

                tracesPayloadToJsonArray(request.textBody.orEmpty())
                    .forEach {
                        Log.i("EndToEndTraceTest", "adding span $it")
                        sentSpansObjects.add(it.asJsonObject)
                    }
            }
        assertThat(expectedSpans.size)
            .withFailMessage {
                "Expected spans doesn't equal to sent spans. Expected=$expectedSpans, sent=$sentSpansObjects"
            }
            .isEqualTo(sentSpansObjects.size)

        expectedSpans.forEach { span ->
            val json = sentSpansObjects.first { spanJson ->
                val leastSignificantTraceId = spanJson.get(TRACE_ID_KEY).asString
                val mostSignificantTraceId = spanJson
                    .getAsJsonObject(META_KEY)
                    .getAsJsonPrimitive(MOST_SIGNIFICANT_64_BITS_TRACE_ID_KEY).asString

                leastSignificantTraceId == span.leastSignificant64BitsTraceId() &&
                    mostSignificantTraceId == span.mostSignificant64BitsTraceId() &&
                    spanJson.get(SPAN_ID_KEY).asString == span.spanIdAsHexString()
            }
            assertMatches(json, span, context)
        }
    }

    protected fun verifyExpectedLogs(
        handledRequests: List<HandledRequest>,
        expectedLogs: LinkedList<Pair<Int, String>>
    ) {
        val logObjects = LinkedList<JsonObject>()
        handledRequests
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
        assertThat(expectedLogs.size).isEqualTo(logObjects.size)
        expectedLogs.forEach {
            val toMatch = logObjects.removeFirst()
            assertThat(toMatch)
                .hasField(TAG_STATUS, levels[it.first])
                .hasField(TAG_EVENT, it.second)
                .hasField(TAG_MESSAGE, "Span event")
                .hasField(TAG_LOGGER) {
                    hasField(TAG_LOGGER_NAME, "trace")
                }
        }
    }

    private fun assertMatches(jsonObject: JsonObject, span: DatadogSpan, context: DatadogContext) {
        assertThat(jsonObject)
            .hasField(SERVICE_NAME_KEY, span.serviceName)
            .hasField(TRACE_ID_KEY, span.leastSignificant64BitsTraceId())
            .hasField(SPAN_ID_KEY, span.spanIdAsHexString())
            .hasField(
                PARENT_ID_KEY,
                span.parentSpanId?.let {
                    DatadogTracingToolkit.spanIdConverter.toHexStringPadded(it)
                }
                    ?: throw AssertionError("No parentId provided from $span")
            )
            .hasField(
                START_TIMESTAMP_KEY,
                span.startTimeNanos,
                Offset.offset(TimeUnit.MINUTES.toNanos(1))
            )
            .hasField(DURATION_KEY, span.durationNano)
            .hasField(RESOURCE_KEY, span.resourceName.orEmpty())
            .hasField(OPERATION_NAME_KEY, span.operationName)

        val metaObject = jsonObject.getAsJsonObject(META_KEY)
        assertThat(metaObject)
            .hasField(MOST_SIGNIFICANT_64_BITS_TRACE_ID_KEY, span.mostSignificant64BitsTraceId())
            .hasField(VERSION_KEY, context.version)
            .hasField(VARIANT_KEY, context.variant)

        assertThat(metaObject).hasField(DD_KEY) {
            hasField(SOURCE_KEY, context.source)
        }

        assertThat(metaObject).hasField(SPAN_KEY) {
            hasField(SPAN_KIND_KEY, "client")
        }

        assertThat(metaObject).hasField(TRACER_KEY) {
            hasField(TRACER_VERSION_KEY, context.sdkVersion)
        }

        assertThat(metaObject).hasField(USR_KEY) {}

        assertThat(metaObject).hasField(DEVICE_KEY) {
            hasField(DEVICE_NAME_KEY, context.deviceInfo.deviceName)
            hasField(DEVICE_MODEL_KEY, context.deviceInfo.deviceModel)
            hasField(DEVICE_BRAND_KEY, context.deviceInfo.deviceBrand)
            hasField(DEVICE_ARCHITECTURE_KEY, context.deviceInfo.architecture)
        }

        assertThat(metaObject).hasField(OS_KEY) {
            hasField(OS_NAME_KEY, context.deviceInfo.osName)
            hasField(OS_VERSION_KEY, context.deviceInfo.osVersion)
            hasField(OS_VERSION_MAJOR_KEY, context.deviceInfo.osMajorVersion)
        }

        // Verify metrics object structure
        val metricsObject = jsonObject.getAsJsonObject(METRICS_KEY)
        assertThat(metricsObject).isNotNull

        // Verify topLevel metric based on parentId
        if (span.parentSpanId == 0L) {
            assertThat(metricsObject).hasField(TOP_LEVEL_KEY, 1L)
        }
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

    companion object {
        // Span top-level keys (order matches SpanEvent structure)
        const val TRACE_ID_KEY = "trace_id"
        const val SPAN_ID_KEY = "span_id"
        const val PARENT_ID_KEY = "parent_id"
        const val RESOURCE_KEY = "resource"
        const val OPERATION_NAME_KEY = "name"
        const val SERVICE_NAME_KEY = "service"
        const val DURATION_KEY = "duration"
        const val START_TIMESTAMP_KEY = "start"
        const val META_KEY = "meta"
        const val METRICS_KEY = "metrics"

        // Meta top-level keys (order matches SpanEvent.Meta structure)
        const val VERSION_KEY = "version"
        const val DD_KEY = "_dd"
        const val SPAN_KEY = "span"
        const val TRACER_KEY = "tracer"
        const val USR_KEY = "usr"
        const val DEVICE_KEY = "device"
        const val OS_KEY = "os"

        // Meta additionalProperties keys
        const val MOST_SIGNIFICANT_64_BITS_TRACE_ID_KEY = "_dd.p.id"
        const val VARIANT_KEY = "variant"

        // Dd nested keys
        const val SOURCE_KEY = "source"

        // Span nested keys
        const val SPAN_KIND_KEY = "kind"

        // Tracer nested keys
        const val TRACER_VERSION_KEY = "version"

        // Device nested keys
        const val DEVICE_NAME_KEY = "name"
        const val DEVICE_MODEL_KEY = "model"
        const val DEVICE_BRAND_KEY = "brand"
        const val DEVICE_ARCHITECTURE_KEY = "architecture"

        // OS nested keys
        const val OS_NAME_KEY = "name"
        const val OS_VERSION_KEY = "version"
        const val OS_VERSION_MAJOR_KEY = "version_major"

        // Metrics keys
        const val TOP_LEVEL_KEY = "_top_level"

        internal val INITIAL_WAIT_MS = TimeUnit.SECONDS.toMillis(60)

        private const val TAG_STATUS = "status"
        private const val TAG_MESSAGE = "message"
        private const val TAG_EVENT = "event"
        private const val TAG_LOGGER = "logger"
        private const val TAG_LOGGER_NAME = "name"
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
