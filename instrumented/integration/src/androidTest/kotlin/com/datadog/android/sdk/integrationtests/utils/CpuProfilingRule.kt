package com.datadog.android.sdk.integrationtests.utils

import android.annotation.TargetApi
import android.os.Build
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

    override fun doAfterWarmUp() {
        super.doAfterWarmUp()
        reset()
        startMeasurementThread()
        Thread.sleep(DEFAULT_CPU_MEASUREMENT_FREQUENCY * 5) // at least 5 measurements
    }

    override fun measureBeforeAction(): Double {
        return resolveCpuTime()
    }

    override fun doBeforeAction() {
        super.doBeforeAction()
        reset()
        startMeasurementThread()
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
        return resolveCpuTime()
    }

    private fun resolveCpuTime(): Double {
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
        val sdkInt = Build.VERSION.SDK_INT
        return if (sdkInt >= Build.VERSION_CODES.O) {
            processorUsageInPercentOreo()
        } else if (sdkInt >= Build.VERSION_CODES.N) {
            processorUsageInPercentNougat()
        } else {
            processorUsageInPercentLollipop()
        }
    }

    @TargetApi(Build.VERSION_CODES.O)
    private fun processorUsageInPercentOreo(): Double {
        val topResult = execShell(
            "sh",
            "-c",
            "top -m 1000 -d 1 -n 1 -o \"PID,%CPU\" | grep \"${android.os.Process.myPid()}\""
        )
        val formatted = topResult.trim().split(Regex(" +"))

        return formatted[1].toDouble()
    }

    @TargetApi(Build.VERSION_CODES.N)
    private fun processorUsageInPercentNougat(): Double {
        val topResult = execShell(
            "sh",
            "-c",
            "top -m 1000 -d 1 -n 1 | grep \"${android.os.Process.myPid()}\""
        )
        val formatted = topResult.trim().split(Regex(" +"))
        // Default display on API 25:
        // PID USER     PR  NI CPU% S  #THR     VSS     RSS PCY Name
        return if (formatted.size < 4) {
            // Just to make sure we have something to display
            0.0
        } else {
            formatted[3].substringBefore('%').toDouble()
        }
    }

    private fun processorUsageInPercentLollipop(): Double {
        val topResult = execShell(
            "sh",
            "-c",
            "top -m 1000 -d 1 -n 1 | grep \"${android.os.Process.myPid()}\""
        )
        val formatted = topResult.trim().split(Regex(" +"))
        // Default display on API 21 to 24:
        // PID PR CPU% S  #THR     VSS     RSS PCY UID      Name
        return if (formatted.size < 3) {
            // Just to make sure we have something to display
            0.0
        } else {
            formatted[2].substringBefore('%').toDouble()
        }
    }

    companion object {
        const val DEFAULT_CPU_MEASUREMENT_FREQUENCY = 1000L // 1 second
    }
}
