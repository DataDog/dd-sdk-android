package com.datadog.android.sdk.integrationtests.utils

import kotlin.math.abs
import org.assertj.core.api.Assertions.assertThat

internal class CpuProfilingRule
    (private val cpuMeasurementFrequencyInMs: Long = DEFAULT_CPU_MEASUREMENT_FREQUENCY) :
    AbstractProfilingRule<Double>() {

    private var totalCpuMeasurements = 0
    private var totalCpuLoad = 0.0

    override fun before() {
        totalCpuLoad = 0.0
        totalCpuMeasurements = 0
    }

    override fun after() {
        totalCpuLoad = 0.0
        totalCpuMeasurements = 0
    }

    override fun measureBeforeAction(): Double {
        val beforePercent = processorUsageInPercent()
        Thread {
            while (true) {
                Thread.sleep(cpuMeasurementFrequencyInMs)
                totalCpuMeasurements++
                totalCpuLoad += processorUsageInPercent()
            }
        }.start()

        return beforePercent
    }

    override fun measureAfterAction(): Double {
        return abs(totalCpuLoad / totalCpuMeasurements)
    }

    override fun compareWithThreshold(before: Double, after: Double, threshold: Double) {
        val difference = after - before
        assertThat(difference)
            .withFailMessage(
                "We were expecting a difference in cpu consumption " +
                        "less than or equal to $threshold." +
                        " Instead we had $difference"
            )
            .isLessThanOrEqualTo(threshold)
    }

    private fun processorUsageInPercent(): Double {
        val topResult = execShell(
            "sh",
            "-c",
            "top -m 1000 -d 1 -n 1 -o \"PID,%CPU\" | grep \"${android.os.Process.myPid()}\""
        )
        val formatted = topResult.trim().split(Regex(" +"))

        return formatted[1].toDouble()
    }

    companion object {
        const val DEFAULT_CPU_MEASUREMENT_FREQUENCY = 1000L // 1 second
    }
}
