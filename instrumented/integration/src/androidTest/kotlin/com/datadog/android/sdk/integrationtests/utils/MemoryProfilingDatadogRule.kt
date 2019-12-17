package com.datadog.android.sdk.integrationtests.utils

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import org.assertj.core.api.Assertions.assertThat
import kotlin.math.abs

class MemoryProfilingDatadogRule<T : Activity>(activityClass: Class<T>) :
    MockDatadogServerRule<T>(activityClass) {

    companion object {
        const val DEFAULT_MEMORY_ALLOWED_THRESHOLD_IN_KB = 50L // 50 KB
    }

    val remainingRamInKb: Long
        get() {
            return Runtime.getRuntime().freeMemory() / 1024
        }

    fun profileForMemoryConsumption(
        action: () -> Unit,
        memoryAllowedThresholdInKb: Long = DEFAULT_MEMORY_ALLOWED_THRESHOLD_IN_KB
    ) {
        val before = remainingRamInKb
        action()
        val after = remainingRamInKb
        val memoryDifference = before - after
        println("Before $before, after $after and difference $memoryDifference")
        assertThat(memoryDifference)
            .withFailMessage(
                "We were expecting a difference in memory consumption " +
                        "less than or equal to ${memoryAllowedThresholdInKb}KB." +
                        " Instead we had ${memoryDifference}KB"
            )
            .isLessThanOrEqualTo(memoryAllowedThresholdInKb)
    }

}