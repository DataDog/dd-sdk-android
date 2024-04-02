/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.tools.detekt.rules.sdk

import fr.xgouchet.elmyr.junit5.ForgeExtension
import io.gitlab.arturbosch.detekt.test.assertThat
import io.gitlab.arturbosch.detekt.test.lint
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(ForgeExtension::class)
internal class UnsafeCallOnNullableTypeTest {

    @Test
    fun `detekt unsafe call on nullable type`() {
        val code =
            """
                fun test(str: String?) {
                    println(str!!.length)
                }
            """.trimIndent()

        val findings = UnsafeCallOnNullableType().lint(code)
        assertThat(findings).hasSize(1)
    }

    @Test
    fun `does not detekt unsafe call on comments`() {
        val code =
            """
                fun test(str: String) {
                    // was println(str!!.length)
                    println(str.length)
                }
            """.trimIndent()

        val findings = UnsafeCallOnNullableType().lint(code)
        assertThat(findings).hasSize(0)
    }

    @Test
    fun `does not detekt safe call on nullable type`() {
        val code =
            """
                fun test(str: String?) {
                    println(str?.length)
                }
            """.trimIndent()

        val findings = UnsafeCallOnNullableType().lint(code)
        assertThat(findings).hasSize(0)
    }

    @Test
    fun `does not detekt safe call in combination with the elvis operator`() {
        val code =
            """
                fun test(str: String?) {
                    println(str?.length ?: 0)
                }
            """.trimIndent()

        val findings = UnsafeCallOnNullableType().lint(code)
        assertThat(findings).hasSize(0)
    }
}
