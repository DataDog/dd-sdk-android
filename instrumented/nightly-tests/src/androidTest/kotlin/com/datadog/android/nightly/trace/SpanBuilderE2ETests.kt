/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.nightly.trace

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.datadog.android.nightly.rules.NightlyTestRule
import com.datadog.android.nightly.utils.initializeSdk
import com.datadog.android.nightly.utils.measure
import io.opentracing.Span
import io.opentracing.util.GlobalTracer
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
class SpanBuilderE2ETests {

    @get:Rule
    val nightlyTestRule = NightlyTestRule()

    @Before
    fun setUp() {
        initializeSdk(InstrumentationRegistry.getInstrumentation().targetContext)
    }

    /**
     * apiMethodSignature: SpanBuilder#fun start()
     */
    @Test
    fun trace_span_builder_start() {
        val testMethodName = "trace_span_builder_start"
        val tracer = GlobalTracer.get()
        var span: Span? = null
        measure(PERF_PREFIX + testMethodName) {
            // here we don't measure individual method, but a chain, because this chain is probably
            // most common way to start a span, so these methods will always be used together
            span = tracer.buildSpan(testMethodName)
                .start()
        }
        span?.finish()
    }

    private companion object {
        // this is needed because pure test method names are reserved for the spans under test
        const val PERF_PREFIX = "perf_"
    }
}
