/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.tools.detekt.rules.sdk

import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.junit5.ForgeExtension
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.test.TestConfig
import io.gitlab.arturbosch.detekt.test.assertThat
import io.gitlab.arturbosch.detekt.test.lint
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(ForgeExtension::class)
class PreferTimeProviderTest {

    lateinit var config: Config

    @BeforeEach
    fun `set up`() {
        config = TestConfig()
    }

    // region prohibited methods

    @Test
    fun `detects prohibited time method usage`(forge: Forge) {
        val methodCall = forge.anElementFrom(PreferTimeProvider.PROHIBITED_TIME_METHODS)
        val code =
            """
            class Foo {
                fun bar(): Any {
                    return $methodCall
                }
            }
            """.trimIndent()

        val findings = PreferTimeProvider(config).lint(code)
        assertThat(findings).hasSize(1)
    }

    @Test
    fun `detects kotlin time function usage`(forge: Forge) {
        val functionCall = forge.anElementFrom(PreferTimeProvider.KOTLIN_TIME_FUNCTIONS)
        val code =
            """
            class Foo {
                fun bar(): Long {
                    return $functionCall {
                        doSomething()
                    }
                }

                fun doSomething() {}
            }
            """.trimIndent()

        val findings = PreferTimeProvider(config).lint(code)
        assertThat(findings).hasSize(1)
    }

    @Test
    fun `detects multiple usages of same method`(forge: Forge) {
        val methodCall = forge.anElementFrom(PreferTimeProvider.PROHIBITED_TIME_METHODS)
        val code =
            """
            class Foo {
                fun bar() {
                    val startTime = $methodCall
                    doSomething()
                    val elapsed = $methodCall - startTime
                }

                fun doSomething() {}
            }
            """.trimIndent()

        val findings = PreferTimeProvider(config).lint(code)
        assertThat(findings).hasSize(2)
    }

    // endregion

    // region no violations

    @Test
    fun `no findings for regular code without time methods`() {
        val code =
            """
            class Foo {
                fun bar(): Int {
                    return 42
                }
            }
            """.trimIndent()

        val findings = PreferTimeProvider(config).lint(code)
        assertThat(findings).hasSize(0)
    }

    @Test
    fun `no findings for TimeProvider usage`() {
        val code =
            """
            class Foo(private val timeProvider: TimeProvider) {
                fun bar(): Long {
                    return timeProvider.getDeviceTimestampMillis()
                }
            }
            """.trimIndent()

        val findings = PreferTimeProvider(config).lint(code)
        assertThat(findings).hasSize(0)
    }

    // endregion
}
