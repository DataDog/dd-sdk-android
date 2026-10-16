/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.tools.detekt.rules.sdk

import dev.detekt.api.Config
import dev.detekt.test.TestConfig
import dev.detekt.test.lintWithContext
import dev.detekt.test.utils.KotlinEnvironmentContainer
import dev.detekt.test.utils.createEnvironment
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(ForgeExtension::class)
class PreferTimeProviderTest {

    lateinit var config: Config

    lateinit var kotlinEnv: KotlinEnvironmentContainer

    @BeforeEach
    fun `set up`() {
        config = TestConfig()
        kotlinEnv = createEnvironment()
    }

    // region prohibited methods

    @Test
    fun `detects prohibited time method usage`(forge: Forge) {
        val method = forge.anElementFrom(PreferTimeProvider.PROHIBITED_TIME_METHODS.toList())
        val code =
            """
            import ${method.substringBeforeLast('.')}

            class Foo {
                fun bar(): Any {
                    return ${method.split('.').takeLast(2).joinToString(".")}()
                }
            }
            """.trimIndent()

        val findings = PreferTimeProvider(config).lintWithContext(kotlinEnv, code, ANDROID_STUBS)
        assertThat(findings).hasSize(1)
    }

    @Test
    fun `detects fully qualified prohibited time method usage`(forge: Forge) {
        val method = forge.anElementFrom(PreferTimeProvider.PROHIBITED_TIME_METHODS.toList())
        val code =
            """
            class Foo {
                fun bar(): Any {
                    return $method()
                }
            }
            """.trimIndent()

        val findings = PreferTimeProvider(config).lintWithContext(kotlinEnv, code, ANDROID_STUBS)
        assertThat(findings).hasSize(1)
    }

    @Test
    fun `detects statically imported prohibited time method usage`() {
        val code =
            """
            import java.lang.System.currentTimeMillis

            class Foo {
                fun bar(): Long {
                    return currentTimeMillis()
                }
            }
            """.trimIndent()

        val findings = PreferTimeProvider(config).lintWithContext(kotlinEnv, code)
        assertThat(findings).hasSize(1)
    }

    @Test
    fun `detects kotlin time function usage`(forge: Forge) {
        val function = forge.anElementFrom(PreferTimeProvider.KOTLIN_TIME_FUNCTIONS.toList())
        val code =
            """
            import $function

            class Foo {
                fun bar(): Long {
                    return ${function.substringAfterLast('.')} {
                        doSomething()
                    }
                }

                fun doSomething() {}
            }
            """.trimIndent()

        val findings = PreferTimeProvider(config).lintWithContext(kotlinEnv, code)
        assertThat(findings).hasSize(1)
    }

    @Test
    fun `detects multiple usages of same method`(forge: Forge) {
        val method = forge.anElementFrom(PreferTimeProvider.PROHIBITED_TIME_METHODS.filter { it.endsWith("Millis") })
        val code =
            """
            class Foo {
                fun bar() {
                    val startTime = $method()
                    doSomething()
                    val elapsed = $method() - startTime
                }

                fun doSomething() {}
            }
            """.trimIndent()

        val findings = PreferTimeProvider(config).lintWithContext(kotlinEnv, code, ANDROID_STUBS)
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

        val findings = PreferTimeProvider(config).lintWithContext(kotlinEnv, code)
        assertThat(findings).hasSize(0)
    }

    @Test
    fun `no findings for TimeProvider usage`() {
        val code =
            """
            interface TimeProvider {
                fun getDeviceTimestampMillis(): Long
            }

            class Foo(private val timeProvider: TimeProvider) {
                fun bar(): Long {
                    return timeProvider.getDeviceTimestampMillis()
                }
            }
            """.trimIndent()

        val findings = PreferTimeProvider(config).lintWithContext(kotlinEnv, code)
        assertThat(findings).hasSize(0)
    }

    @Test
    fun `no findings for time method with an injected clock`() {
        val code =
            """
            import java.time.Clock
            import java.time.Instant

            class Foo(private val clock: Clock) {
                fun bar(): Instant {
                    return Instant.now(clock)
                }
            }
            """.trimIndent()

        val findings = PreferTimeProvider(config).lintWithContext(kotlinEnv, code)
        assertThat(findings).hasSize(0)
    }

    @Test
    fun `no findings for own function with a time function name`() {
        val code =
            """
            class Foo {
                fun bar(): Long {
                    return measureTimeMillis { }
                }

                private fun measureTimeMillis(block: () -> Unit): Long {
                    block()
                    return 0L
                }
            }
            """.trimIndent()

        val findings = PreferTimeProvider(config).lintWithContext(kotlinEnv, code)
        assertThat(findings).hasSize(0)
    }

    // endregion

    companion object {
        // The Analysis API test engine only provides the JDK and the Kotlin stdlib, stub the Android symbols we need
        @Language("kotlin")
        private val ANDROID_STUBS =
            """
            package android.os

            object SystemClock {
                @JvmStatic fun elapsedRealtime(): Long = 0L
                @JvmStatic fun elapsedRealtimeNanos(): Long = 0L
                @JvmStatic fun uptimeMillis(): Long = 0L
            }
            """.trimIndent()
    }
}
