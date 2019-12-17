package com.datadog.android.sdk.integrationtests.utils

import org.assertj.core.api.Assertions.assertThat
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

internal class MemoryProfilingRule :
    TestRule {

    companion object {
        const val DEFAULT_MEMORY_ALLOWED_THRESHOLD_IN_KB = 100L // 50 KB
    }

    private fun remainingRamInKb(): Long {
        return Runtime.getRuntime().freeMemory() / 1024
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

    fun profileForMemoryConsumption(
        action: () -> Unit,
        memoryAllowedThresholdInKb: Long = DEFAULT_MEMORY_ALLOWED_THRESHOLD_IN_KB
    ) {
        val before = remainingRamInKb()
        action()
        val after = remainingRamInKb()
        val memoryDifference = before - after
        assertThat(memoryDifference)
            .withFailMessage(
                "We were expecting a difference in memory consumption " +
                        "less than or equal to ${memoryAllowedThresholdInKb}KB." +
                        " Instead we had ${memoryDifference}KB"
            )
            .isLessThanOrEqualTo(memoryAllowedThresholdInKb)
    }
}
