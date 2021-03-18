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
import com.datadog.android.core.configuration.Configuration
import com.datadog.android.core.configuration.Credentials
import com.datadog.android.privacy.TrackingConsent
import com.datadog.android.rum.tracking.MixedViewTrackingStrategy
import com.datadog.tools.unit.invokeMethod
import java.util.UUID
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
        val config = Configuration
            .Builder(
                logsEnabled = true,
                tracesEnabled = true,
                crashReportsEnabled = true,
                rumEnabled = true
            )
            .trackInteractions()
            .useViewTrackingStrategy(MixedViewTrackingStrategy(trackExtras = true))
            .build()
        benchmark.measureRepeated {
            Datadog.initialize(
                context,
                Credentials(
                    "NO_TOKEN",
                    "benchmark",
                    "benchmark",
                    UUID.randomUUID().toString()
                ),
                config,
                TrackingConsent.GRANTED
            )

            runWithTimingDisabled {
                Datadog.invokeMethod("stop")
            }
        }
    }
}
