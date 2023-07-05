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
import fr.xgouchet.elmyr.junit4.ForgeRule
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

    @get:Rule
    val forge = ForgeRule()

    /**
     * apiMethodSignature: com.datadog.android.Datadog#fun initialize(android.content.Context, com.datadog.android.core.configuration.Configuration, com.datadog.android.privacy.TrackingConsent): com.datadog.android.api.SdkCore?
     * apiMethodSignature: com.datadog.android.Datadog#fun initialize(String?, android.content.Context, com.datadog.android.core.configuration.Configuration, com.datadog.android.privacy.TrackingConsent): com.datadog.android.api.SdkCore?
     * apiMethodSignature: com.datadog.android.core.configuration.Configuration$Builder#fun build(): Configuration
     * apiMethodSignature: com.datadog.android.trace.TraceConfiguration$Builder#fun build(): TraceConfiguration
     * apiMethodSignature: com.datadog.android.core.configuration.Configuration$Builder#constructor(String, String, String = NO_VARIANT, String? = null)
     * apiMethodSignature: com.datadog.android.trace.AndroidTracer$Builder#fun build(): AndroidTracer
     */
    @Before
    fun setUp() {
        initializeSdk(InstrumentationRegistry.getInstrumentation().targetContext, forgeSeed = forge.seed)
    }

    /**
     * apiMethodSignature: io.opentracing.Tracer$SpanBuilder#fun start()
     */
    @Test
    fun trace_span_builder_start() {
        val testMethodName = "trace_span_builder_start"
        val tracer = GlobalTracer.get()
        var span: Span? = null
        measure(testMethodName) {
            // here we don't measure individual method, but a chain, because this chain is probably
            // most common way to start a span, so these methods will always be used together
            span = tracer.buildSpan(testMethodName)
                .start()
        }
        span?.finish()
    }
}
