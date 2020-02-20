/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2019 Datadog, Inc.
 */

package com.datadog.android.sdk.benchmark.trace

import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.datadog.android.Datadog
import com.datadog.android.DatadogConfig
import com.datadog.android.sdk.benchmark.aThrowable
import com.datadog.android.sdk.benchmark.mockResponse
import com.datadog.android.tracing.Tracer
import com.datadog.tools.unit.invokeMethod
import fr.xgouchet.elmyr.junit4.ForgeRule
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TraceApiBenchmark {
    @get:Rule
    val benchmark = BenchmarkRule()
    @get:Rule
    val forge = ForgeRule()

    lateinit var testedTracer: Tracer

    lateinit var mockWebServer: MockWebServer

    @Before
    fun setUp() {
        mockWebServer = MockWebServer()
            .apply {
                start()
            }
        mockWebServer.setDispatcher(object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                return mockResponse(200)
            }
        })
        val fakeEndpoint = mockWebServer.url("/").toString().removeSuffix("/")

        val context = InstrumentationRegistry.getInstrumentation().context
        val config = DatadogConfig
            .Builder("NO_TOKEN")
            .useCustomTracesEndpoint(fakeEndpoint)
            .build()
        Datadog.initialize(context, config)

        testedTracer = Tracer
            .Builder()
            .setPartialFlushThreshold(1)
            .build()
    }

    @After
    fun tearDown() {
        Datadog.invokeMethod("stop")
        mockWebServer.shutdown()
    }

    @Test
    fun benchmark_creating_span() {
        benchmark.measureRepeated {
            val operationName = runWithTimingDisabled { forge.anAlphabeticalString() }
            val span = testedTracer.buildSpan(operationName).start()
            span.finish()
        }
    }

    @Test
    fun benchmark_creating_span_with_throwable() {
        benchmark.measureRepeated {
            val (operationName, throwable) = runWithTimingDisabled {
                forge.anAlphabeticalString() to forge.aThrowable()
            }

            val span = testedTracer.buildSpan(operationName).start()
            span.setErrorMeta(throwable)
            span.finish()
        }
    }

    @Test
    fun benchmark_creating_spans_with_baggage_items_and_logs() {
        createSpansWithLogsTagsAndBaggageItems(1)
    }

    @Test
    fun benchmark_creating_medium_load_of_spans() {
        createSpansWithLogsTagsAndBaggageItems(MEDIUM_ITERATIONS)
    }

    @Test
    fun benchmark_creating_heavy_load_of_spans() {
        createSpansWithLogsTagsAndBaggageItems(BIG_ITERATIONS)
    }

    private fun createSpansWithLogsTagsAndBaggageItems(iterations: Int) {
        benchmark.measureRepeated {
            var counter = 0
            do {
                val operationName = runWithTimingDisabled { forge.anAlphabeticalString() }
                val tagsNumber = forge.anInt(max = 50)
                val logsNumber = forge.anInt(max = 50)
                val span = testedTracer.buildSpan(operationName).start()
                for (i in 0..tagsNumber) {
                    val (key, keyValue) = runWithTimingDisabled {
                        forge.anAlphabeticalString() to forge.anAlphabeticalString()
                    }
                    span.setBaggageItem(key, keyValue)
                }
                for (i in 0..tagsNumber) {
                    val (key, keyValue) = runWithTimingDisabled {
                        forge.anAlphabeticalString() to forge.anAlphabeticalString()
                    }
                    span.setTag(key, keyValue)
                }
                for (i in 0..logsNumber) {
                    val logValue = runWithTimingDisabled {
                        forge.anAlphabeticalString()
                    }
                    span.log(logValue)
                }
                span.finish()
                counter++
            } while (counter < iterations)
        }
    }

    companion object {
        const val MAX_SPANS_PER_BATCH = 500
        const val MEDIUM_ITERATIONS = MAX_SPANS_PER_BATCH / 2
        const val BIG_ITERATIONS =
            MAX_SPANS_PER_BATCH
    }
}
