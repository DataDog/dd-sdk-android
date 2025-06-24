package com.datadog.android.macrobenchmark

import android.content.Intent
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.TraceSectionMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * This is an example startup benchmark.
 *
 * It navigates to the device's home screen, and launches the default activity.
 *
 * Before running this benchmark:
 * 1) switch your app's active build variant in the Studio (affects Studio runs only)
 * 2) add `<profileable android:shell="true" />` to your app's manifest, within the `<application>` tag
 *
 * Run this benchmark from Studio to see startup measurements, and captured system traces
 * for investigating your app's performance.
 */
@RunWith(AndroidJUnit4::class)
class InstrumentedStartupBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @OptIn(ExperimentalMetricApi::class)
    @RequiresApi(Build.VERSION_CODES.Q)
    @Test
    fun startup() = benchmarkRule.measureRepeated(
        packageName = "com.datadog.sample.benchmark",
        metrics = listOf(
            TraceSectionMetric(
                sectionName = "wahaha_section",
            )
        ),
        iterations = 10,
        startupMode = StartupMode.COLD
    ) {
        pressHome()
        val intent = Intent().apply {
            setClassName("com.datadog.sample.benchmark", "com.datadog.benchmark.sample.activities.LaunchActivity")
            putExtra("synthetics.benchmark.scenario", "rum_auto")
            putExtra("synthetics.benchmark.run", "instrumented")
        }
        startActivityAndWait(intent)
    }
}
