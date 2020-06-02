/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sdk.benchmark.rum

import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.datadog.android.Datadog
import com.datadog.android.DatadogConfig
import com.datadog.android.rum.GlobalRum
import com.datadog.android.rum.RumMonitor
import com.datadog.android.rum.RumResourceKind
import com.datadog.android.sdk.benchmark.aThrowable
import com.datadog.android.sdk.benchmark.mockResponse
import com.datadog.tools.unit.invokeMethod
import fr.xgouchet.elmyr.junit4.ForgeRule
import java.util.UUID
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
class RumManualApiBenchmark {
    @get:Rule
    val benchmarkRule = BenchmarkRule()

    @get:Rule
    val forge = ForgeRule()

    lateinit var mockWebServer: MockWebServer

    @Before
    fun setUp() {
        mockWebServer = MockWebServer().apply { start() }
        mockWebServer.setDispatcher(object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                return mockResponse(200)
            }
        })
        val fakeEndpoint = mockWebServer.url("/").toString().removeSuffix("/")

        val context = InstrumentationRegistry.getInstrumentation().context
        val config = DatadogConfig
            .Builder("NO_TOKEN", "benchmark", UUID.randomUUID().toString())
            .useCustomRumEndpoint(fakeEndpoint)
            .setRumEnabled(true)
            .build()
        Datadog.initialize(context, config)
        GlobalRum.registerIfAbsent(RumMonitor.Builder().build())
    }

    @Test
    fun benchmark_start_view() {
        val attributes = forge.aMap(size = 100) {
            forge.anHexadecimalString() to forge.anAlphabeticalString()
        }
        benchmarkRule.measureRepeated {
            val viewKey = runWithTimingDisabled { forge.anHexadecimalString() }
            val viewName = runWithTimingDisabled { forge.anAlphabeticalString() }
            GlobalRum.get().startView(viewKey, viewName, attributes = attributes)
        }
    }

    @Test
    fun benchmark_stop_view() {
        val attributes = forge.aMap(size = 100) {
            forge.anHexadecimalString() to forge.anAlphabeticalString()
        }
        benchmarkRule.measureRepeated {
            val viewKey = runWithTimingDisabled { forge.anHexadecimalString() }
            val viewName = runWithTimingDisabled { forge.anAlphabeticalString() }
            runWithTimingDisabled {
                GlobalRum.get().startView(viewKey, viewName, attributes = attributes)
            }
            GlobalRum.get().stopView(viewKey, attributes)
        }
    }

    @Test
    fun benchmark_start_resource() {
        val attributes = forge.aMap(size = 100) {
            forge.anHexadecimalString() to forge.anAlphabeticalString()
        }
        val viewKey = forge.anHexadecimalString()
        val viewName = forge.anAlphabeticalString()

        benchmarkRule.measureRepeated {
            runWithTimingDisabled {
                GlobalRum.get().startView(viewKey, viewName, attributes = attributes)
            }
            val resourceKey = runWithTimingDisabled { forge.anHexadecimalString() }
            val resourceName = runWithTimingDisabled { forge.anAlphabeticalString() }
            val url = runWithTimingDisabled {
                forge.aStringMatching("http//[a-z1-9]+\\.[a-z]{3}/")
            }
            GlobalRum.get().startResource(resourceKey, resourceName, url)
            runWithTimingDisabled {
                GlobalRum.get().stopView(viewKey)
            }
        }
    }

    @Test
    fun benchmark_stop_resource() {
        val attributes = forge.aMap(size = 100) {
            forge.anHexadecimalString() to forge.anAlphabeticalString()
        }
        val viewKey = forge.anHexadecimalString()
        val viewName = forge.anAlphabeticalString()

        benchmarkRule.measureRepeated {
            runWithTimingDisabled {
                GlobalRum.get().startView(viewKey, viewName, attributes = attributes)
            }
            val resourceKey = runWithTimingDisabled { forge.anHexadecimalString() }
            val resourceName = runWithTimingDisabled { forge.anAlphabeticalString() }
            val url = runWithTimingDisabled {
                forge.aStringMatching("http//[a-z1-9]+\\.[a-z]{3}/")
            }
            val kind =
                runWithTimingDisabled { forge.anElementFrom(RumResourceKind.values().asList()) }
            runWithTimingDisabled { GlobalRum.get().startResource(resourceKey, resourceName, url) }
            GlobalRum.get().stopResource(resourceKey, kind)
            runWithTimingDisabled { GlobalRum.get().stopView(viewKey) }
        }
    }

    @Test
    fun benchmark_start_action() {
        val attributes = forge.aMap(size = 100) {
            forge.anHexadecimalString() to forge.anAlphabeticalString()
        }
        val viewKey = forge.anHexadecimalString()
        val viewName = forge.anAlphabeticalString()

        benchmarkRule.measureRepeated {
            runWithTimingDisabled {
                GlobalRum.get().startView(viewKey, viewName, attributes = attributes)
            }
            val actionName = runWithTimingDisabled { forge.anAlphabeticalString() }
            GlobalRum.get().startUserAction(actionName, attributes)
            runWithTimingDisabled {
                GlobalRum.get().stopView(viewKey)
            }
        }
    }

    @Test
    fun benchmark_stop_action() {
        val attributes = forge.aMap(size = 100) {
            forge.anHexadecimalString() to forge.anAlphabeticalString()
        }
        val viewKey = forge.anHexadecimalString()
        val viewName = forge.anAlphabeticalString()

        benchmarkRule.measureRepeated {
            runWithTimingDisabled {
                GlobalRum.get().startView(viewKey, viewName, attributes = attributes)
            }
            val actionName = runWithTimingDisabled { forge.anAlphabeticalString() }
            runWithTimingDisabled { GlobalRum.get().startUserAction(actionName, attributes) }
            GlobalRum.get().stopUserAction(actionName, attributes)
            runWithTimingDisabled {
                GlobalRum.get().stopView(viewKey)
            }
        }
    }

    @Test
    fun benchmark_add_error() {
        val attributes = forge.aMap(size = 100) {
            forge.anHexadecimalString() to forge.anAlphabeticalString()
        }
        val viewKey = forge.anHexadecimalString()
        val viewName = forge.anAlphabeticalString()

        benchmarkRule.measureRepeated {
            runWithTimingDisabled {
                GlobalRum.get().startView(viewKey, viewName, attributes = attributes)
            }
            val errorMessage = runWithTimingDisabled { forge.anAlphabeticalString() }
            val errorOrigin = runWithTimingDisabled { forge.anAlphabeticalString() }
            val throwable: Throwable = runWithTimingDisabled { forge.aThrowable() }
            GlobalRum.get().addError(errorMessage, errorOrigin, throwable, attributes)
            runWithTimingDisabled {
                GlobalRum.get().stopView(viewKey)
            }
        }
    }

    @After
    fun tearDown() {
        Datadog.invokeMethod("stop")
        mockWebServer.shutdown()
    }
}
