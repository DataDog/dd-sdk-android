/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sdk.rules

import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.max
import org.assertj.core.api.Assertions.assertThat
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

internal abstract class AbstractProfilingRule(
    private val name: String
) : TestRule {

    private val comparator = Comparator<Double> { o1, o2 -> o1.compareTo(o2) }

    data class ProfilingConfig(
        val occurrences: Int,
        val durationMs: Long,
        val minimumSleepMs: Long
    ) {
        val periodMs = durationMs / occurrences
        val sleepMs = max(minimumSleepMs, periodMs)
        val repeatPerIteration = sleepMs / periodMs
        val iterations = durationMs / sleepMs
    }

    // region TestRule

    override fun apply(base: Statement?, description: Description?): Statement {
        return object : Statement() {
            override fun evaluate() {
                InstrumentationRegistry.getInstrumentation().waitForIdleSync()
                base?.evaluate()
            }
        }
    }

    // endregion

    // region AbstractContinuousProfilingRule

    fun profile(
        runThreshold: Double,
        runConfig: ProfilingConfig,
        warmupConfig: ProfilingConfig,
        action: () -> Unit
    ) {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()

        // Warmup
        repeatActionWithMeasures(action, warmupConfig)
        Thread.sleep(COOLDOWN_DURATION_MS)

        // Actual Run
        val beforeRun = measure()
        val runContinuous = repeatActionWithMeasures(action, runConfig)

        // Check results
        val absRunThreshold = beforeRun + runThreshold
        val runAverage = runContinuous.average()
        val peakRun = runContinuous.max()
        val diffAverage = runAverage - beforeRun
        val diffPeak = peakRun - beforeRun
        assertThat(runAverage)
            .overridingErrorMessage(
                "Expected average $name to stay below $absRunThreshold " +
                    "($beforeRun + $runThreshold), " +
                    "but it was $runAverage ($beforeRun + $diffAverage}, " +
                    "with a peak at $peakRun ($beforeRun + $diffPeak)"
            )
            .isLessThanOrEqualTo(absRunThreshold)
    }

    abstract fun measure(): Double

    // endregion

    // region Internal

    private fun repeatActionWithMeasures(
        action: () -> Unit,
        config: ProfilingConfig
    ): List<Double> {
        val list = mutableListOf<Double>()
        val countDownLatch = CountDownLatch(1)

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            repeat(config.iterations.toInt()) {
                repeat(config.repeatPerIteration.toInt()) { action() }
                list.add(measure())
                Thread.sleep(config.sleepMs)
            }
            countDownLatch.countDown()
        }

        countDownLatch.await(
            config.durationMs + TIMEOUT_EXTRA_MS,
            TimeUnit.MILLISECONDS
        )

        return list
    }

    private fun List<Double>.max(): Double {
        return maxWithOrNull(comparator) ?: 0.0
    }

    private fun List<Double>.average(): Double {
        val sum = sumOf { it }
        return sum / size.toDouble()
    }

    // endregion

    companion object {
        private val TIMEOUT_EXTRA_MS = TimeUnit.SECONDS.toMillis(3)
        private val COOLDOWN_DURATION_MS = TimeUnit.SECONDS.toMillis(5)
    }
}
