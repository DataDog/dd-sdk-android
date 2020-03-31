/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sdk.rules

import android.annotation.TargetApi
import android.os.Build
import android.os.Process
import com.datadog.android.sdk.utils.execShell

internal class CPUProfilingRule :
    AbstractProfilingRule(name = "CPU usage in %") {

    // region AbstractProfilingRule

    override fun measure(): Double {
        return processorUsageInPercent()
    }

    // endregion

    // region Internal

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
            "top -m 1000 -d 1 -n 1 -o \"PID,%CPU\" | grep \"${Process.myPid()}\""
        )
        val formatted = topResult.first().trim().split(Regex(" +"))

        return formatted[1].toDouble()
    }

    @TargetApi(Build.VERSION_CODES.N)
    private fun processorUsageInPercentNougat(): Double {
        val topResult = execShell(
            "sh",
            "-c",
            "top -m 1000 -d 1 -n 1 | grep \"${Process.myPid()}\""
        )
        val formatted = topResult.first().trim().split(Regex(" +"))
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
            "top -m 1000 -d 1 -n 1 | grep \"${Process.myPid()}\""
        )
        val formatted = topResult.first().trim().split(Regex(" +"))
        // Default display on API 21 to 24:
        // PID PR CPU% S  #THR     VSS     RSS PCY UID      Name
        return if (formatted.size < 3) {
            // Just to make sure we have something to display
            0.0
        } else {
            formatted[2].substringBefore('%').toDouble()
        }
    }

    // endregion
}
