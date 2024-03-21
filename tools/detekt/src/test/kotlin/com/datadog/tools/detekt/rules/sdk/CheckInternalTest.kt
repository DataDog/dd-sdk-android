/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.tools.detekt.rules.sdk

import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.junit5.ForgeExtension
import io.gitlab.arturbosch.detekt.test.assertThat
import io.gitlab.arturbosch.detekt.test.lint
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(ForgeExtension::class)
class CheckInternalTest {

    @Test
    fun `detekt check in internal class`(forge: Forge) {
        val visibility = forge.anElementFrom("internal", "private")
        val code =
            """
            $visibility class Foo {
                fun bar() {
                    check(1 < 0) { "Oups" }
                }
            }
            """.trimIndent()

        val findings = CheckInternal().lint(code)
        assertThat(findings).hasSize(1)
    }

    @Test
    fun `detekt check in internal class in public class`(forge: Forge) {
        val visibility = forge.anElementFrom("internal", "private")
        val code =
            """
            class Top {
                $visibility class Foo {
                    fun bar() {
                        check(1 < 0) { "Oups" }
                    }
                }
            }
            """.trimIndent()

        val findings = CheckInternal().lint(code)
        assertThat(findings).hasSize(1)
    }

    @Test
    fun `detekt check in internal method of public class`(forge: Forge) {
        val visibility = forge.anElementFrom("internal", "private", "protected")
        val code =
            """
            class Foo {
                $visibility fun bar() {
                    check(1 < 0) { "Oups" }
                }
            }
            """.trimIndent()

        val findings = CheckInternal().lint(code)
        assertThat(findings).hasSize(1)
    }

    @Test
    fun `ignore check in public method of public class`() {
        val code =
            """
            class Foo {
                fun bar() {
                    check(1 < 0) { "Oups" }
                }
            }
            """.trimIndent()

        val findings = CheckInternal().lint(code)
        assertThat(findings).hasSize(0)
    }

    @Test
    fun `detekt checkNotNull in internal class`(forge: Forge) {
        val visibility = forge.anElementFrom("internal", "private")
        val code =
            """
            $visibility class Foo {
                fun bar() {
                    checkNotNull(1 < 0) { "Oups" }
                }
            }
            """.trimIndent()

        val findings = CheckInternal().lint(code)
        assertThat(findings).hasSize(1)
    }

    @Test
    fun `detekt checkNotNull in internal class in public class`(forge: Forge) {
        val visibility = forge.anElementFrom("internal", "private")
        val code =
            """
            class Top {
                $visibility class Foo {
                    fun bar() {
                        checkNotNull(1 < 0) { "Oups" }
                    }
                }
            }
            """.trimIndent()

        val findings = CheckInternal().lint(code)
        assertThat(findings).hasSize(1)
    }

    @Test
    fun `detekt checkNotNull in internal method of public class`(forge: Forge) {
        val visibility = forge.anElementFrom("internal", "private", "protected")
        val code =
            """
            class Foo {
                $visibility fun bar() {
                    checkNotNull(1 < 0) { "Oups" }
                }
            }
            """.trimIndent()

        val findings = CheckInternal().lint(code)
        assertThat(findings).hasSize(1)
    }

    @Test
    fun `ignore checkNotNull in public method of public class`() {
        val code =
            """
            class Foo {
                fun bar() {
                    checkNotNull(1 < 0) { "Oups" }
                }
            }
            """.trimIndent()

        val findings = CheckInternal().lint(code)
        assertThat(findings).hasSize(0)
    }
}
