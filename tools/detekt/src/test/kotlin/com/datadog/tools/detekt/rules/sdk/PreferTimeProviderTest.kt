/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.tools.detekt.rules.sdk

import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.test.TestConfig
import io.gitlab.arturbosch.detekt.test.assertThat
import io.gitlab.arturbosch.detekt.test.lint
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class PreferTimeProviderTest {

    lateinit var config: Config

    @BeforeEach
    fun `set up`() {
        config = TestConfig()
    }

    // region System.nanoTime

    @Test
    fun `detects System nanoTime usage`() {
        val code =
            """
            class Foo {
                fun bar(): Long {
                    return System.nanoTime()
                }
            }
            """.trimIndent()

        val findings = PreferTimeProvider(config).lint(code)
        assertThat(findings).hasSize(1)
    }

    @Test
    fun `detects System nanoTime usage in variable`() {
        val code =
            """
            class Foo {
                fun bar() {
                    val startTime = System.nanoTime()
                    doSomething()
                    val elapsed = System.nanoTime() - startTime
                }

                fun doSomething() {}
            }
            """.trimIndent()

        val findings = PreferTimeProvider(config).lint(code)
        assertThat(findings).hasSize(2)
    }

    // endregion

    // region System.currentTimeMillis

    @Test
    fun `detects System currentTimeMillis usage`() {
        val code =
            """
            class Foo {
                fun bar(): Long {
                    return System.currentTimeMillis()
                }
            }
            """.trimIndent()

        val findings = PreferTimeProvider(config).lint(code)
        assertThat(findings).hasSize(1)
    }

    @Test
    fun `detects System currentTimeMillis usage in variable`() {
        val code =
            """
            class Foo {
                fun bar() {
                    val startTime = System.currentTimeMillis()
                    doSomething()
                    val elapsed = System.currentTimeMillis() - startTime
                }

                fun doSomething() {}
            }
            """.trimIndent()

        val findings = PreferTimeProvider(config).lint(code)
        assertThat(findings).hasSize(2)
    }

    // endregion

    // region measureTimeMillis

    @Test
    fun `detects measureTimeMillis usage`() {
        val code =
            """
            import kotlin.system.measureTimeMillis

            class Foo {
                fun bar(): Long {
                    return measureTimeMillis {
                        doSomething()
                    }
                }

                fun doSomething() {}
            }
            """.trimIndent()

        val findings = PreferTimeProvider(config).lint(code)
        assertThat(findings).hasSize(1)
    }

    // endregion

    // region measureNanoTime

    @Test
    fun `detects measureNanoTime usage`() {
        val code =
            """
            import kotlin.system.measureNanoTime

            class Foo {
                fun bar(): Long {
                    return measureNanoTime {
                        doSomething()
                    }
                }

                fun doSomething() {}
            }
            """.trimIndent()

        val findings = PreferTimeProvider(config).lint(code)
        assertThat(findings).hasSize(1)
    }

    // endregion

    // region Clock and Instant

    @Test
    fun `detects Clock systemUTC usage`() {
        val code =
            """
            import java.time.Clock

            class Foo {
                fun bar(): Clock {
                    return Clock.systemUTC()
                }
            }
            """.trimIndent()

        val findings = PreferTimeProvider(config).lint(code)
        assertThat(findings).hasSize(1)
    }

    @Test
    fun `detects Clock systemDefaultZone usage`() {
        val code =
            """
            import java.time.Clock

            class Foo {
                fun bar(): Clock {
                    return Clock.systemDefaultZone()
                }
            }
            """.trimIndent()

        val findings = PreferTimeProvider(config).lint(code)
        assertThat(findings).hasSize(1)
    }

    @Test
    fun `detects Instant now usage`() {
        val code =
            """
            import java.time.Instant

            class Foo {
                fun bar(): Instant {
                    return Instant.now()
                }
            }
            """.trimIndent()

        val findings = PreferTimeProvider(config).lint(code)
        assertThat(findings).hasSize(1)
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
