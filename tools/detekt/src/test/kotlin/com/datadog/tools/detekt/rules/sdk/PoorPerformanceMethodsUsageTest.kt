/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.tools.detekt.rules.sdk

import io.gitlab.arturbosch.detekt.test.assertThat
import io.gitlab.arturbosch.detekt.test.lint
import org.junit.Test

class PoorPerformanceMethodsUsageTest {

    @Test
    fun `detekt joinToString method call`() {
        val code =
            """
            fun test() {
                listOf(1).joinToString()
            }
            """.trimIndent()

        val findings = PoorPerformanceMethodsUsage().lint(code)

        assertThat(findings).hasSize(1)
    }

    @Test
    fun `detekt ignores joinToString method call with Suppress annotation on parent function`() {
        val code =
            """
            @Suppress("AnotherSuppressedRule", "PotentiallyPoorPerformanceMethodUsage")
            fun test() {
                listOf(1).joinToString()
            }
            """.trimIndent()

        val findings = PoorPerformanceMethodsUsage().lint(code)

        assertThat(findings).hasSize(0)
    }

    @Test
    fun `detekt ignores joinToString method call with Suppress annotation on expression`() {
        val code =
            """
            fun test() {
                @Suppress("PotentiallyPoorPerformanceMethodUsage")
                listOf(1).joinToString()
            }
            """.trimIndent()

        val findings = PoorPerformanceMethodsUsage().lint(code)

        assertThat(findings).hasSize(0)
    }
}
