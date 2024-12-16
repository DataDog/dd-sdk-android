/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.utils

import com.datadog.android.utils.forge.Configurator
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import kotlin.system.measureNanoTime

private const val ITEMS_TO_JOINT = 10_000
private const val REPETITION_COUNT = 10_000

@Extensions(
    ExtendWith(ForgeExtension::class)
)
@ForgeConfiguration(Configurator::class)
internal class PerformanceTest {

    private fun List<Long>.mean() = sum().toDouble() / size

    @Test
    fun `M be faster than joinToString W buildString`(forge: Forge) {
        val itemsForJoin = forge.aList(ITEMS_TO_JOINT) { forge.aString() }
        val jointToStringResults = mutableListOf<Long>()
        val builderResults = mutableListOf<Long>()

        var jointToStringResult = ""
        var builderResult = ""

        for (i in 0..REPETITION_COUNT) {
            jointToStringResults.add(
                measureNanoTime {
                    val jointToStringContainer = mutableListOf<String>()
                    for (item in itemsForJoin) {
                        jointToStringContainer.add(item)
                    }
                    jointToStringResult = jointToStringContainer.joinToString(separator = " ") { it }
                }
            )

            builderResults.add(
                measureNanoTime {
                    builderResult = buildString {
                        itemsForJoin.forEachIndexed { i, item ->
                            if (i > 0) append(" ")
                            append(item)
                        }
                    }
                }
            )
        }

        assertThat(builderResult).isEqualTo(jointToStringResult) // same result
        assertThat(builderResults.mean()).isLessThan(jointToStringResults.mean())
    }
}
