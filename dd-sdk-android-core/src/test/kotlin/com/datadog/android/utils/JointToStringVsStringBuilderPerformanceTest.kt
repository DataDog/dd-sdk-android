/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.utils

import com.datadog.android.core.appendIfNotEmpty
import com.datadog.android.utils.forge.Configurator
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.sqrt
import kotlin.system.measureNanoTime

private const val ITEMS_TO_JOINT = 10_000
private const val REPETITION_COUNT = 10_000

@Extensions(
    ExtendWith(ForgeExtension::class)
)
@ForgeConfiguration(value = Configurator::class, seed = 0x50c72968d123L)
internal class JointToStringVsStringBuilderPerformanceTest {

    @Test
    fun `M be faster than joinToString W buildString`(forge: Forge) {
        val itemsForJoin = forge.aList(ITEMS_TO_JOINT) { forge.aString() }
        val jointToStringExecutionTime = mutableListOf<Long>()
        val builderResultsExecutionTime = mutableListOf<Long>()

        var jointToStringResult: String
        var builderResult: String

        for (i in 0..REPETITION_COUNT) {
            jointToStringExecutionTime.add(
                measureNanoTime {
                    val jointToStringContainer = mutableListOf<String>()
                    for (item in itemsForJoin) {
                        jointToStringContainer.add(item)
                    }
                    jointToStringResult = jointToStringContainer.joinToString(separator = " ") { it }
                }
            )

            builderResultsExecutionTime.add(
                measureNanoTime {
                    builderResult = buildString {
                        itemsForJoin.forEach { item -> appendIfNotEmpty(' ').append(item) }
                    }
                }
            )

            assertThat(builderResult).isEqualTo(jointToStringResult) // same result
        }

        val statisticsReport = (
            "buildString:\n" +
                " mean = ${builderResultsExecutionTime.mean}\n" +
                " std = ${builderResultsExecutionTime.std}\n" +
                " cv = ${"%.2f".format(builderResultsExecutionTime.cv)}%\n" +
                " p50 = ${builderResultsExecutionTime.percentile(50)}\n" +
                " p90 = ${builderResultsExecutionTime.percentile(90)}\n" +
                " p95 = ${builderResultsExecutionTime.percentile(95)}\n" +
                " p99 = ${builderResultsExecutionTime.percentile(99)}\n" +
                "\n" +
                "joinToString:\n" +
                " mean = ${jointToStringExecutionTime.mean}\n" +
                " std = ${jointToStringExecutionTime.std}\n" +
                " cv = ${"%.2f".format(jointToStringExecutionTime.cv)}%\n" +
                " p50 = ${jointToStringExecutionTime.percentile(50)},\n" +
                " p90 = ${jointToStringExecutionTime.percentile(90)},\n" +
                " p95 = ${jointToStringExecutionTime.percentile(95)},\n" +
                " p99 = ${jointToStringExecutionTime.percentile(99)}\n"
            )

        println(statisticsReport)

        assertThat(
            builderResultsExecutionTime.percentile(95)
        ).isLessThan(
            jointToStringExecutionTime.percentile(95)
        )
    }

    companion object {
        private val List<Long>.mean
            get() = (sum().toDouble() / size)

        private val List<Long>.std: Double
            get() {
                val m = mean
                return sqrt(
                    sumOf { (it - m).pow(2.0) } / size
                )
            }

        private val List<Long>.cv: Double
            get() = std / mean * 100.0

        private fun List<Long>.percentile(k: Int): Long {
            val p = (k / 100.0) * (size + 1)
            return sorted()[round(p).toInt()]
        }
    }
}
