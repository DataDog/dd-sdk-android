/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sdk.benchmark.core

import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.datadog.android.Datadog
import com.datadog.android.DatadogConfig
import com.datadog.tools.unit.invokeMethod
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CoreInitBenchmark {
    @get:Rule
    val benchmark = BenchmarkRule()

    @Test
    fun benchmark_initialize() {
        val context = InstrumentationRegistry.getInstrumentation().context
        val config = DatadogConfig
            .Builder("NO_TOKEN")
            .setTracesEnabled(true)
            .setLogsEnabled(true)
            .setCrashReportsEnabled(true)
            .build()

        benchmark.measureRepeated {
            Datadog.initialize(context, config)

            runWithTimingDisabled {
                Datadog.invokeMethod("stop")
            }
        }
    }
}
