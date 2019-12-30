package com.datadog.android.sdk.integrationtests.utils

import org.assertj.core.api.Assertions.assertThat

internal class MemoryProfilingRule :
    AbstractProfilingRule<Long>() {

    override fun before() {
    }

    override fun after() {
    }

    override fun measureBeforeAction(): Long {
        return remainingRamInKb()
    }

    override fun measureAfterAction(): Long {
        return remainingRamInKb()
    }

    override fun compareWithThreshold(before: Long, after: Long, threshold: Long) {
        val memoryDifference = before - after
        assertThat(memoryDifference)
            .withFailMessage(
                "We were expecting a difference in memory consumption " +
                        "less than or equal to ${threshold}KB." +
                        " Instead we had ${memoryDifference}KB"
            )
            .isLessThanOrEqualTo(threshold)
    }

    private fun remainingRamInKb(): Long {
        return Runtime.getRuntime().freeMemory() / 1024
    }
}
