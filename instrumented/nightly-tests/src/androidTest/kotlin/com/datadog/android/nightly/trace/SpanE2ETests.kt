/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.nightly.trace

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.datadog.android.nightly.SPECIAL_BOOLEAN_TAG_NAME
import com.datadog.android.nightly.SPECIAL_DOUBLE_TAG_NAME
import com.datadog.android.nightly.SPECIAL_GENERIC_TAG_NAME
import com.datadog.android.nightly.SPECIAL_NUMBER_TAG_NAME
import com.datadog.android.nightly.SPECIAL_STRING_TAG_NAME
import com.datadog.android.nightly.TEST_METHOD_NAME_KEY
import com.datadog.android.nightly.rules.NightlyTestRule
import com.datadog.android.nightly.utils.initializeSdk
import com.datadog.android.nightly.utils.measure
import fr.xgouchet.elmyr.junit4.ForgeRule
import io.opentracing.tag.StringTag
import io.opentracing.util.GlobalTracer
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
class SpanE2ETests {

    @get:Rule
    val forge = ForgeRule()

    @get:Rule
    val nightlyTestRule = NightlyTestRule()

    /**
     * apiMethodSignature: com.datadog.android.Datadog#fun initialize(android.content.Context, com.datadog.android.core.configuration.Credentials, com.datadog.android.core.configuration.Configuration, com.datadog.android.privacy.TrackingConsent): com.datadog.android.v2.api.SdkCore?
     * apiMethodSignature: com.datadog.android.Datadog#fun initialize(String?, android.content.Context, com.datadog.android.core.configuration.Credentials, com.datadog.android.core.configuration.Configuration, com.datadog.android.privacy.TrackingConsent): com.datadog.android.v2.api.SdkCore?
     * apiMethodSignature: com.datadog.android.core.configuration.Configuration$Builder#fun build(): Configuration
     * apiMethodSignature: com.datadog.android.trace.TracesConfiguration$Builder#fun build(): TracesConfiguration
     * apiMethodSignature: com.datadog.android.core.configuration.Configuration$Builder#constructor(Boolean)
     */
    @Before
    fun setUp() {
        initializeSdk(InstrumentationRegistry.getInstrumentation().targetContext, forgeSeed = forge.seed)
    }

    /**
     * apiMethodSignature: io.opentracing.Span#fun setOperationName(String)
     */
    @Test
    fun trace_span_set_operation_name() {
        // this will also test the existence of the span itself
        val testMethodName = "trace_span_set_operation_name"
        val tracer = GlobalTracer.get()
        val span = tracer.buildSpan("random")
            .start()
        measure(testMethodName) {
            span.setOperationName(testMethodName)
        }
        span.finish()
    }

    /**
     * apiMethodSignature: io.opentracing.Span#fun setTag(String, Boolean)
     */
    @Test
    fun trace_span_set_tag_boolean() {
        val testMethodName = "trace_span_set_tag_boolean"
        val span = GlobalTracer.get()
            .buildSpan(testMethodName)
            .start()
        val tagValue = forge.aBool()
        measure(testMethodName) {
            span.setTag(SPECIAL_BOOLEAN_TAG_NAME, tagValue)
        }
        span.finish()
    }

    /**
     * apiMethodSignature: io.opentracing.Span#fun setTag(String, String)
     */
    @Test
    fun trace_span_set_tag_string() {
        val testMethodName = "trace_span_set_tag_string"
        val span = GlobalTracer.get()
            .buildSpan(testMethodName)
            .start()
        val tagValue = "str${forge.anAlphaNumericalString()}"
        measure(testMethodName) {
            span.setTag(SPECIAL_STRING_TAG_NAME, tagValue)
        }
        span.finish()
    }

    /**
     * apiMethodSignature: io.opentracing.Span#fun setTag(String, Number)
     */
    @Test
    fun trace_span_set_tag_number() {
        val testMethodName = "trace_span_set_tag_number"
        val span = GlobalTracer.get()
            .buildSpan(testMethodName)
            .start()
        val tagValue = forge.anElementFrom(
            forge.aLong(1, 10),
            forge.anInt(1, 10),
            forge.aFloat(1f, 10f),
            forge.aDouble(1.0, 10.0),
            forge.anInt(1, 10).toByte()
        )
        measure(testMethodName) {
            span.setTag(SPECIAL_NUMBER_TAG_NAME, tagValue)
        }
        span.finish()
    }

    /**
     * apiMethodSignature: io.opentracing.Span#fun <T>setTag(Tag<T>, T)
     */
    @Test
    fun trace_span_set_tag_generic() {
        val testMethodName = "trace_span_set_tag_generic"
        val span = GlobalTracer.get()
            .buildSpan(testMethodName)
            .start()
        val tagValue = "str${forge.anAlphaNumericalString()}"
        val stringTag = StringTag(SPECIAL_GENERIC_TAG_NAME)
        measure(testMethodName) { span.setTag(stringTag, tagValue) }
        span.finish()
    }

    /**
     * apiMethodSignature: io.opentracing.Span#fun setBaggageItem(String, String)
     */
    @Test
    fun trace_span_set_baggage_item() {
        val testMethodName = "trace_span_set_baggage_item"
        val span = GlobalTracer.get()
            .buildSpan(testMethodName)
            .start()
        val baggage = "str${forge.anAlphaNumericalString()}"
        measure(testMethodName) {
            span.setBaggageItem(SPECIAL_STRING_TAG_NAME, baggage)
        }
        span.finish()
    }

    /**
     * apiMethodSignature: io.opentracing.Span#fun log(Map)
     */
    @Test
    fun trace_span_log_map() {
        val testMethodName = "trace_span_log_map"
        val span = GlobalTracer.get()
            .buildSpan(testMethodName)
            .start()
        val logValue = mapOf(
            "${SPECIAL_NUMBER_TAG_NAME}_positive" to forge.aSmallInt(),
            "${SPECIAL_NUMBER_TAG_NAME}_negative" to -forge.aSmallInt(),
            SPECIAL_DOUBLE_TAG_NAME to forge.aDouble(min = 1.0, max = 10.0),
            TEST_METHOD_NAME_KEY to testMethodName
        )
        measure(testMethodName) {
            span.log(logValue)
        }
        span.finish()
    }

    /**
     * apiMethodSignature: io.opentracing.Span#fun log(String)
     */
    @Test
    fun trace_span_log_string() {
        val testMethodName = "trace_span_log_string"
        val span = GlobalTracer.get()
            .buildSpan(testMethodName)
            .start()
        val logValue = "${testMethodName}_str${forge.anAlphaNumericalString()}"
        measure(testMethodName) {
            span.log(logValue)
        }
        span.finish()
    }

    /**
     * apiMethodSignature: io.opentracing.Span#fun log(long, Map)
     */
    @Test
    fun trace_span_log_timestamp_map() {
        val testMethodName = "trace_span_log_timestamp_map"
        val span = GlobalTracer.get()
            .buildSpan(testMethodName)
            .start()
        val timestampMicros = System.currentTimeMillis() * 1000
        val logValue = mapOf(
            "${SPECIAL_NUMBER_TAG_NAME}_positive" to forge.aSmallInt(),
            "${SPECIAL_NUMBER_TAG_NAME}_negative" to -forge.aSmallInt(),
            SPECIAL_DOUBLE_TAG_NAME to forge.aDouble(min = 1.0, max = 10.0),
            TEST_METHOD_NAME_KEY to testMethodName
        )
        measure(testMethodName) {
            span.log(timestampMicros, logValue)
        }
        span.finish()
    }

    /**
     * apiMethodSignature: io.opentracing.Span#fun log(long, String)
     */
    @Test
    fun trace_span_log_timestamp_string() {
        val testMethodName = "trace_span_log_timestamp_string"
        val span = GlobalTracer.get()
            .buildSpan(testMethodName)
            .start()
        val timestampMicros = System.currentTimeMillis() * 1000
        val logValue = "${testMethodName}_str${forge.anAlphaNumericalString()}"
        measure(testMethodName) {
            span.log(timestampMicros, logValue)
        }
        span.finish()
    }

    /**
     * apiMethodSignature: io.opentracing.Span#fun finish()
     */
    @Test
    fun trace_span_finish() {
        val testMethodName = "trace_span_finish"
        val span = GlobalTracer.get()
            .buildSpan(testMethodName)
            .start()
        measure(testMethodName) {
            span.finish()
        }
    }
}
