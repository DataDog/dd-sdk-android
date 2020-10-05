/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.tools.detekt.rules

import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.junit5.ForgeExtension
import io.gitlab.arturbosch.detekt.test.assertThat
import io.gitlab.arturbosch.detekt.test.lint
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(ForgeExtension::class)
class TodoWithoutTaskTest {

    @Test
    fun `detekt todo without task number`(forge: Forge) {
        val comment = forge.aStringMatching("\\w+( \\w+)*")
        val code =
            """
            class Foo {
                fun bar() {
                    // TODO $comment
                }
            }
            """.trimIndent()

        val findings = TodoWithoutTask().lint(code)
        assertThat(findings).hasSize(1)
    }

    @Test
    fun `detekt todo without task number in multiline comment`(forge: Forge) {
        val comment = forge.aStringMatching("\\w+( \\w+)*")
        val code =
            """
            class Foo {
                fun bar() {
                    /*
                     * TODO $comment
                     */
                }
            }
            """.trimIndent()

        val findings = TodoWithoutTask().lint(code)
        assertThat(findings).hasSize(1)
    }

    @Test
    fun `detekt todo without task number in property doc comment`(forge: Forge) {
        val comment = forge.aStringMatching("\\w+( \\w+)*")
        val code =
            """
            class Foo {
            
                /**
                 * Do Something.
                 * TODO $comment
                 */
                lateinit var property : String 
            }
            """.trimIndent()

        val findings = TodoWithoutTask().lint(code)
        assertThat(findings).hasSize(1)
    }

    @Test
    fun `detekt todo without task number in method doc comment`(forge: Forge) {
        val comment = forge.aStringMatching("\\w+( \\w+)*")
        val code =
            """
            class Foo {
            
                /**
                 * Do Something.
                 * TODO $comment
                 */
                fun bar() {
                }
            }
            """.trimIndent()

        val findings = TodoWithoutTask().lint(code)
        assertThat(findings).hasSize(1)
    }

    @Test
    fun `detekt todo without task number in class doc comment`(forge: Forge) {
        val comment = forge.aStringMatching("\\w+( \\w+)*")
        val code =
            """
            /**
             * TODO $comment
             */
            class Foo {
                fun bar() {
                }
            }
            """.trimIndent()

        val findings = TodoWithoutTask().lint(code)
        assertThat(findings).hasSize(1)
    }

    @Test
    fun `detekt todo without task number in object doc comment`(forge: Forge) {
        val comment = forge.aStringMatching("\\w+( \\w+)*")
        val code =
            """
            /**
             * TODO $comment
             */
            object Foo {
                fun bar() {
                }
            }
            """.trimIndent()

        val findings = TodoWithoutTask().lint(code)
        assertThat(findings).hasSize(1)
    }
}
