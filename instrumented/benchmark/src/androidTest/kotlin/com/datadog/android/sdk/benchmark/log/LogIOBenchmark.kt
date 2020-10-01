/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sdk.benchmark.log

import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.datadog.android.Datadog
import com.datadog.android.DatadogConfig
import com.datadog.android.sdk.benchmark.mockResponse
import com.datadog.tools.unit.createInstance
import com.datadog.tools.unit.forge.aThrowable
import com.datadog.tools.unit.getStaticValue
import com.datadog.tools.unit.invokeGenericMethod
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
class LogIOBenchmark {

    @get:Rule
    val benchmark = BenchmarkRule()

    @get:Rule
    val forge = ForgeRule()

    private lateinit var mockWebServer: MockWebServer

    private lateinit var testedStrategy: Any
    private lateinit var testedWriter: Any
    private lateinit var testedReader: Any

    @Before
    fun setUp() {
        mockWebServer = MockWebServer().apply { start() }
        mockWebServer.setDispatcher(
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    return mockResponse(200)
                }
            }
        )
        val fakeEndpoint = mockWebServer.url("/").toString().removeSuffix("/")

        val context = InstrumentationRegistry.getInstrumentation().context
        Datadog.initialize(
            context,
            DatadogConfig.Builder("NO_TOKEN", "benchmark")
                .useCustomLogsEndpoint(fakeEndpoint)
                .build()
        )
        val classLoader = Datadog::class.java.classLoader!!
        val LogFeatureClass =
            classLoader.loadClass("com.datadog.android.log.internal.LogsFeature") as Class<Any>
        testedStrategy = LogFeatureClass.getStaticValue("persistenceStrategy")
        testedWriter = testedStrategy.invokeMethod("getWriter")!!
        testedReader = testedStrategy.invokeMethod("getReader")!!
    }

    @After
    fun tearDown() {
        testedReader.invokeGenericMethod("dropAllBatches")
        Datadog.invokeMethod("stop")
        mockWebServer.shutdown()
    }

    @Test
    fun benchmark_write_logs_on_disk() {

        benchmark.measureRepeated {
            val log = runWithTimingDisabled { createLog() }
            testedWriter.invokeGenericMethod("write", log)
        }
    }

    @Test
    fun benchmark_read_logs_from_disk() {
        Thread.sleep(5500L)

        for (i in 0..100) {
            val log = createLog()
            testedWriter.invokeGenericMethod("write", log)
        }

        Thread.sleep(5500L)

        benchmark.measureRepeated {
            val batch = testedReader.invokeMethod("readNextBatch")
            runWithTimingDisabled {
                checkNotNull(batch)
                val id = batch.invokeMethod("getId") as String
                testedReader.invokeMethod("releaseBatch", id)
            }
        }
    }

    // region Internal

    private fun createLog(): Any {

        val serviceName = forge.anAlphaNumericalString()
        val loggerName: String = forge.anAlphaNumericalString()
        val threadName: String = Thread.currentThread().name
        val level: Int = forge.anInt(2, 8)
        val message: String = forge.anAlphabeticalString()
        val timestamp: Long = System.currentTimeMillis()
        val attributes: Map<String, Any?> = forge.aMap { anAlphabeticalString() to anAsciiString() }
        val tags: List<String> = forge.aList { aStringMatching("[a-z]+:[0-9]+") }
        val throwable: Throwable = forge.aThrowable()
        val networkInfo: Any? = null
        val userInfo: Any = createInstance(
            "com.datadog.android.log.internal.user.UserInfo",
            forge.anHexadecimalString(),
            forge.anAlphabeticalString(),
            forge.aStringMatching("[a-z0-9]+@[a-z0-9]+\\.com")
        )

        return createInstance(
            "com.datadog.android.log.internal.domain.Log",
            serviceName,
            level,
            message,
            timestamp,
            attributes,
            tags,
            throwable,
            networkInfo,
            userInfo,
            loggerName,
            threadName
        )
    }

    // endregion
}
