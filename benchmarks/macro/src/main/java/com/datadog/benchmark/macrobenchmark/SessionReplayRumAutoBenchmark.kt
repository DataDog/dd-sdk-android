/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.benchmark.macrobenchmark

import android.content.ComponentName
import android.content.Intent
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.StaleObjectException
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SessionReplayRumAutoBenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun frameTimingWithSessionReplay() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.Full(),
        startupMode = StartupMode.COLD,
        iterations = 10,
        setupBlock = {
            pressHome()
        }
    ) {
        val intent = Intent().apply {
            component = ComponentName(TARGET_PACKAGE, LAUNCH_ACTIVITY)
            putExtra(EXTRA_SCENARIO, SCENARIO_RUM_AUTO)
            putExtra(EXTRA_RUN, RUN_INSTRUMENTED)
        }
        startActivityAndWait(intent)

        device.wait(Until.hasObject(By.scrollable(true)), TIMEOUT_MS)

        repeat(INTERACTION_CYCLES) {
            device.scrollContent(Direction.DOWN)
            device.scrollContent(Direction.DOWN)
            device.scrollContent(Direction.UP)

            device.clickByText("Episodes")

            device.scrollContent(Direction.DOWN)
            device.scrollContent(Direction.UP)

            device.clickByText("Locations")

            device.clickByText("Characters")
        }
    }

    private fun UiDevice.clickByText(text: String) {
        waitForIdle()
        wait(Until.hasObject(By.text(text)), TIMEOUT_MS)
        try {
            findObject(By.text(text))?.click()
        } catch (_: StaleObjectException) {
            findObject(By.text(text))?.click()
        }
        waitForIdle()
    }

    private fun UiDevice.scrollContent(direction: Direction) {
        try {
            val scrollable = findObject(By.scrollable(true)) ?: return
            scrollable.setGestureMargin(displayWidth / 5)
            scrollable.scroll(direction, 2f)
        } catch (_: StaleObjectException) {
            // view hierarchy changed mid-scroll, continue
        }
        waitForIdle()
    }

    companion object {
        private const val TARGET_PACKAGE = "com.datadog.sample.benchmark"
        private const val LAUNCH_ACTIVITY =
            "com.datadog.benchmark.sample.activities.LaunchActivity"
        private const val EXTRA_SCENARIO = "synthetics.benchmark.scenario"
        private const val EXTRA_RUN = "synthetics.benchmark.run"
        private const val SCENARIO_RUM_AUTO = "rum_auto"
        private const val RUN_INSTRUMENTED = "instrumented"
        private const val TIMEOUT_MS = 15_000L
        private const val INTERACTION_CYCLES = 3
    }
}
