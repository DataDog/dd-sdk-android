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

    @Before
    fun setUp() {
        initializeSdk(InstrumentationRegistry.getInstrumentation().targetContext)
    }

    /**
     * apiMethodSignature: Span#setOperationName(String)
     */
    @Test
    fun trace_span_set_operation_name() {
        // this will also test the existence of the span itself
        val testMethodName = "trace_span_set_operation_name"
        GlobalTracer.get()
            .buildSpan("random")
            .start()
            .setOperationName(testMethodName)
            .finish()
    }

    /**
     * apiMethodSignature: Span#setTag(String, Boolean)
     */
    @Test
    fun trace_span_set_tag_boolean() {
        val testMethodName = "trace_span_set_tag_boolean"
        GlobalTracer.get()
            .buildSpan(testMethodName)
            .start()
            .setTag(SPECIAL_BOOLEAN_TAG_NAME, forge.aBool())
            .finish()
    }

    /**
     * apiMethodSignature: Span#setTag(String, String)
     */
    @Test
    fun trace_span_set_tag_string() {
        val testMethodName = "trace_span_set_tag_string"
        GlobalTracer.get()
            .buildSpan(testMethodName)
            .start()
            .setTag(SPECIAL_STRING_TAG_NAME, "str${forge.anAlphaNumericalString()}")
            .finish()
    }

    /**
     * apiMethodSignature: Span#setTag(String, Number)
     */
    @Test
    fun trace_span_set_tag_number() {
        val testMethodName = "trace_span_set_tag_number"
        GlobalTracer.get()
            .buildSpan(testMethodName)
            .start()
            .setTag(
                SPECIAL_NUMBER_TAG_NAME,
                forge.anElementFrom(
                    forge.aLong(1, 10),
                    forge.anInt(1, 10),
                    forge.aFloat(1f, 10f),
                    forge.aDouble(1.0, 10.0),
                    forge.anInt(1, 10).toByte()
                )
            )
            .finish()
    }

    /**
     * apiMethodSignature: Span#<T>setTag(Tag<T>, T)
     */
    @Test
    fun trace_span_set_tag_generic() {
        val testMethodName = "trace_span_set_tag_generic"
        GlobalTracer.get()
            .buildSpan(testMethodName)
            .start()
            .setTag(StringTag(SPECIAL_GENERIC_TAG_NAME), "str${forge.anAlphaNumericalString()}")
            .finish()
    }

    /**
     * apiMethodSignature: Span#setBaggageItem(String, String)
     */
    @Test
    fun trace_span_set_baggage_item() {
        val testMethodName = "trace_span_set_baggage_item"
        GlobalTracer.get()
            .buildSpan(testMethodName)
            .start()
            .setBaggageItem(SPECIAL_STRING_TAG_NAME, "str${forge.anAlphaNumericalString()}")
            .finish()
    }

    /**
     * apiMethodSignature: Span#log(Map)
     */
    @Test
    fun trace_span_log_map() {
        val testMethodName = "trace_span_log_map"
        GlobalTracer.get()
            .buildSpan(testMethodName)
            .start()
            .log(
                mapOf(
                    "${SPECIAL_NUMBER_TAG_NAME}_positive" to forge.aSmallInt(),
                    "${SPECIAL_NUMBER_TAG_NAME}_negative" to -forge.aSmallInt(),
                    SPECIAL_DOUBLE_TAG_NAME to forge.aDouble(min = 1.0, max = 10.0),
                    TEST_METHOD_NAME_KEY to testMethodName
                )
            )
            .finish()
    }

    /**
     * apiMethodSignature: Span#log(String)
     */
    @Test
    fun trace_span_log_string() {
        val testMethodName = "trace_span_log_string"
        GlobalTracer.get()
            .buildSpan(testMethodName)
            .start()
            .log("${testMethodName}_str${forge.anAlphaNumericalString()}")
            .finish()
    }

    /**
     * apiMethodSignature: Span#log(long, Map)
     */
    @Test
    fun trace_span_log_timestamp_map() {
        val testMethodName = "trace_span_log_timestamp_map"
        GlobalTracer.get()
            .buildSpan(testMethodName)
            .start()
            .log(
                System.currentTimeMillis() * 1000,
                mapOf(
                    "${SPECIAL_NUMBER_TAG_NAME}_positive" to forge.aSmallInt(),
                    "${SPECIAL_NUMBER_TAG_NAME}_negative" to -forge.aSmallInt(),
                    SPECIAL_DOUBLE_TAG_NAME to forge.aDouble(min = 1.0, max = 10.0),
                    TEST_METHOD_NAME_KEY to testMethodName
                )
            )
            .finish()
    }

    /**
     * apiMethodSignature: Span#log(long, String)
     */
    @Test
    fun trace_span_log_timestamp_string() {
        val testMethodName = "trace_span_log_timestamp_string"
        GlobalTracer.get()
            .buildSpan(testMethodName)
            .start()
            .log(
                System.currentTimeMillis() * 1000,
                "${testMethodName}_str${forge.anAlphaNumericalString()}"
            )
            .finish()
    }
}
