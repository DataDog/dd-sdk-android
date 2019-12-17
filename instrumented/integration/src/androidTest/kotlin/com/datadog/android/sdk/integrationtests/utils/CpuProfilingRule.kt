package com.datadog.android.sdk.integrationtests.utils

import kotlin.math.abs
import org.assertj.core.api.Assertions.assertThat
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

internal class CpuProfilingRule :
    TestRule {

    private fun processorUsageInPercent(): Double {
        val topResult = execShell(
            "sh",
            "-c",
            "top -m 1000 -d 1 -n 1 | grep ${android.os.Process.myPid()}"
        )
        return topResult.split(Regex(" +"))[8].toDouble()
    }

    override fun apply(base: Statement?, description: Description?): Statement {
        return object : Statement() {
            override fun evaluate() {
                System.gc()
                base?.evaluate()
                System.gc()
            }
        }
    }

    fun profileForCpuConsumption(
        action: () -> Unit,
        cpuAllowedThreshold: Double = DEFAULT_CPU_ALLOWED_THRESHOLD_IN_PERCENTAGE,
        cpuMeasurementFrequencyInMs: Long = DEFAULT_CPU_MEASUREMENT_FREQUENCY
    ) {
        val before = processorUsageInPercent()
        var total = 0.0
        var counter = 0
        Thread {
            while (true) {
                counter++
                total += processorUsageInPercent()
                Thread.sleep(cpuMeasurementFrequencyInMs)
            }
        }.start()

        action()

        val cpuUsageMean = abs(total / counter)
        val difference = cpuUsageMean - before

        assertThat(cpuUsageMean)
            .withFailMessage(
                "We were expecting a difference in cpu consumption " +
                        "less than or equal to $cpuAllowedThreshold." +
                        " Instead we had $difference"
            )
            .isLessThanOrEqualTo(difference)
    }

    companion object {
        const val DEFAULT_CPU_ALLOWED_THRESHOLD_IN_PERCENTAGE = 1.0 // 1%
        const val DEFAULT_CPU_MEASUREMENT_FREQUENCY = 1000L // 1 second
    }
}
