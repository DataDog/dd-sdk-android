package com.datadog.android.sdk.integrationtests.utils

import java.text.DecimalFormat
import kotlin.math.abs
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.fail

internal class CpuProfilingRule
    (private val cpuMeasurementFrequencyInMs: Long = DEFAULT_CPU_MEASUREMENT_FREQUENCY) :
    AbstractProfilingRule<Double>() {

    private var totalCpuMeasurements = 0
    private var totalCpuLoad = 0.0
    private var runMeasurement = true
    private var measurementThread: Thread? = null
    private val locker = Object()

    override fun before() {
        reset()
        runMeasurement = true
    }

    override fun after() {
        reset()
        runMeasurement = false
    }

    private fun reset() {
        totalCpuLoad = 0.0
        totalCpuMeasurements = 0
    }

    override fun measureBeforeAction(): Double {
        val beforePercent = processorUsageInPercent()
        startMeasurementThread()

        return beforePercent
    }

    private fun startMeasurementThread() {
        runMeasurement = true
        measurementThread = Thread {
            while (runMeasurement) {
                synchronized(locker) {
                    totalCpuMeasurements++
                    totalCpuLoad += processorUsageInPercent()
                }
                Thread.sleep(cpuMeasurementFrequencyInMs)
            }
        }.apply { start() }
    }

    override fun measureAfterAction(): Double {
        // stop the measurement thread
        runMeasurement = false
        synchronized(locker) {
            if (totalCpuMeasurements == 0) {
                fail(
                    "We were not able to take any CPU measurements" +
                            " while the action was performed." +
                            "Maybe you need to increase the duration of your action."
                )
            }
            return abs(totalCpuLoad / totalCpuMeasurements)
        }
    }

    override fun compareWithThreshold(before: Double, after: Double, threshold: Double) {
        val difference = after - before
        val decimalFormat = DecimalFormat("#.##")
        assertThat(difference)
            .withFailMessage(
                "We were expecting a difference in cpu consumption " +
                        "less than or equal to $threshold." +
                        " Instead we had ${decimalFormat.format(difference)}"
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
