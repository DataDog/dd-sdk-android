/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.tools.detekt.rules.sdk

import fr.xgouchet.elmyr.junit5.ForgeExtension
import io.github.detekt.test.utils.KotlinCoreEnvironmentWrapper
import io.github.detekt.test.utils.createEnvironment
import io.gitlab.arturbosch.detekt.test.assertThat
import io.gitlab.arturbosch.detekt.test.compileAndLintWithContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(ForgeExtension::class)
internal class InvalidStringFormatTest {

    lateinit var kotlinEnv: KotlinCoreEnvironmentWrapper

    @BeforeEach
    fun setup() {
        kotlinEnv = createEnvironment()
    }

    @AfterEach
    fun tearDown() {
        kotlinEnv.dispose()
    }

    // region Test unresolved format string

    @Test
    fun `Warns unresolved String format {variable, ext}`() {
        // Given
        val code =
            """ 
                fun test(s: String): String {
                    var pattern = "Called %s with %s"
                    return pattern.format(s)
                }
            """.trimIndent()

        // When
        val findings = InvalidStringFormat()
            .compileAndLintWithContext(kotlinEnv.env, code)

        // Then
        assertThat(findings).hasSize(1)
    }

    @Test
    fun `Warns unresolved String format {variable, static}`() {
        // Given
        val code =
            """ 
                fun test(s: String): String {
                    var pattern = "Called %s with %s"
                    return String.format(pattern, s)
                }
            """.trimIndent()

        // When
        val findings = InvalidStringFormat()
            .compileAndLintWithContext(kotlinEnv.env, code)

        // Then
        assertThat(findings).hasSize(1)
    }

    @Test
    fun `Warns unresolved String format {argument, ext}`() {
        // Given
        val code =
            """ 
                fun test(pattern: String, s: String): String {
                    return pattern.format(s)
                }
            """.trimIndent()

        // When
        val findings = InvalidStringFormat()
            .compileAndLintWithContext(kotlinEnv.env, code)

        // Then
        assertThat(findings).hasSize(1)
    }

    @Test
    fun `Warns unresolved String format {argument, static}`() {
        // Given
        val code =
            """ 
                fun test(pattern: String, s: String): String {
                    return String.format(pattern, s)
                }
            """.trimIndent()

        // When
        val findings = InvalidStringFormat()
            .compileAndLintWithContext(kotlinEnv.env, code)

        // Then
        assertThat(findings).hasSize(1)
    }

    @Test
    fun `Warns unresolved String format {argument with defaults, ext}`() {
        // Given
        val code =
            """ 
                fun test(pattern: String = "%s", s: String): String {
                    return pattern.format(s)
                }
            """.trimIndent()

        // When
        val findings = InvalidStringFormat()
            .compileAndLintWithContext(kotlinEnv.env, code)

        // Then
        assertThat(findings).hasSize(1)
    }

    @Test
    fun `Warns unresolved String format {argument with defaults, static}`() {
        // Given
        val code =
            """ 
                fun test(pattern: String = "%s", s: String): String {
                    return String.format(pattern, s)
                }
            """.trimIndent()

        // When
        val findings = InvalidStringFormat()
            .compileAndLintWithContext(kotlinEnv.env, code)

        // Then
        assertThat(findings).hasSize(1)
    }

    // endregion

    // region Test invalid number of args

    @Test
    fun `Warns invalid argument count {ext, magic string, no locale}`() {
        // Given
        val code =
            """ 
                fun test(s: String): String {
                    return "Called %s with %s".format(s)
                }
            """.trimIndent()

        // When
        val findings = InvalidStringFormat()
            .compileAndLintWithContext(kotlinEnv.env, code)

        // Then
        assertThat(findings).hasSize(1)
    }

    @Test
    fun `Warns invalid argument count {ext, magic string, with locale}`() {
        // Given
        val code =
            """ 
                import java.util.Locale
                fun test(s: String): String {
                    return "Called %s with %s".format(Locale.US, s)
                }
            """.trimIndent()

        // When
        val findings = InvalidStringFormat()
            .compileAndLintWithContext(kotlinEnv.env, code)

        // Then
        assertThat(findings).hasSize(1)
    }

    @Test
    fun `Warns invalid argument count {ext, local val, no locale}`() {
        // Given
        val code =
            """ 
                fun test(s: String): String {
                    val pattern = "Called %s with %s"
                    return pattern.format(s)
                }
            """.trimIndent()

        // When
        val findings = InvalidStringFormat()
            .compileAndLintWithContext(kotlinEnv.env, code)

        // Then
        assertThat(findings).hasSize(1)
    }

    @Test
    fun `Warns invalid argument count {ext, local val, with locale}`() {
        // Given
        val code =
            """ 
                import java.util.Locale
                fun test(s: String): String {
                    val pattern = "Called %s with %s"
                    return pattern.format(Locale.US, s)
                }
            """.trimIndent()

        // When
        val findings = InvalidStringFormat()
            .compileAndLintWithContext(kotlinEnv.env, code)

        // Then
        assertThat(findings).hasSize(1)
    }

    @Test
    fun `Warns invalid argument count {ext, constant, no locale}`() {
        // Given
        val code =
            """ 
                const val PATTERN = "Called %s with %s"
                
                fun test(s: String): String {
                    return PATTERN.format(s)
                }
            """.trimIndent()

        // When
        val findings = InvalidStringFormat()
            .compileAndLintWithContext(kotlinEnv.env, code)

        // Then
        assertThat(findings).hasSize(1)
    }

    @Test
    fun `Warns invalid argument count {ext, constant, with locale}`() {
        // Given
        val code =
            """ 
                import java.util.Locale
                
                const val PATTERN = "Called %s with %s"
                
                fun test(s: String): String {
                    return PATTERN.format(Locale.US, s)
                }
            """.trimIndent()

        // When
        val findings = InvalidStringFormat()
            .compileAndLintWithContext(kotlinEnv.env, code)

        // Then
        assertThat(findings).hasSize(1)
    }

    @Test
    fun `Warns invalid argument count {static, magic string, no locale}`() {
        // Given
        val code =
            """ 
                fun test(s: String): String {
                    return String.format("Called %s with %s", s)
                }
            """.trimIndent()

        // When
        val findings = InvalidStringFormat()
            .compileAndLintWithContext(kotlinEnv.env, code)

        // Then
        assertThat(findings).hasSize(1)
    }

    @Test
    fun `Warns invalid argument count {static, magic string, with locale}`() {
        // Given
        val code =
            """ 
                import java.util.Locale
                fun test(s: String): String {
                    return String.format(Locale.US, "Called %s with %s", s)
                }
            """.trimIndent()

        // When
        val findings = InvalidStringFormat()
            .compileAndLintWithContext(kotlinEnv.env, code)

        // Then
        assertThat(findings).hasSize(1)
    }

    @Test
    fun `Warns invalid argument count {static, local val, no locale}`() {
        // Given
        val code =
            """ 
                fun test(s: String): String {
                    val pattern = "Called %s with %s"
                    return String.format(pattern, s)
                }
            """.trimIndent()

        // When
        val findings = InvalidStringFormat()
            .compileAndLintWithContext(kotlinEnv.env, code)

        // Then
        assertThat(findings).hasSize(1)
    }

    @Test
    fun `Warns invalid argument count {static, local val, with locale}`() {
        // Given
        val code =
            """ 
                import java.util.Locale
                fun test(s: String): String {
                    val pattern = "Called %s with %s"
                    return String.format(Locale.US, pattern, s)
                }
            """.trimIndent()

        // When
        val findings = InvalidStringFormat()
            .compileAndLintWithContext(kotlinEnv.env, code)

        // Then
        assertThat(findings).hasSize(1)
    }

    @Test
    fun `Warns invalid argument count {static, constant, no locale}`() {
        // Given
        val code =
            """ 
                const val PATTERN = "Called %s with %s"
                
                fun test(s: String): String {
                    return String.format(PATTERN, s)
                }
            """.trimIndent()

        // When
        val findings = InvalidStringFormat()
            .compileAndLintWithContext(kotlinEnv.env, code)

        // Then
        assertThat(findings).hasSize(1)
    }

    @Test
    fun `Warns invalid argument count {static, constant, with locale}`() {
        // Given
        val code =
            """ 
                import java.util.Locale
                
                const val PATTERN = "Called %s with %s"
                
                fun test(s: String): String {
                    return String.format(Locale.US, PATTERN, s)
                }
            """.trimIndent()

        // When
        val findings = InvalidStringFormat()
            .compileAndLintWithContext(kotlinEnv.env, code)

        // Then
        assertThat(findings).hasSize(1)
    }

    // endregion

    // region Test invalid arg types

    @Test
    fun `Warns invalid String format args type {indexed args}`() {
        // Given
        val code =
            """ 
                fun test(): String {
                    return String.format("Called %1${"$"}d / %2${"$"}f", 3.14f, 42)
                }
            """.trimIndent()

        // When
        val findings = InvalidStringFormat()
            .compileAndLintWithContext(kotlinEnv.env, code)

        // Then
        assertThat(findings).hasSize(2)
    }

    @Test
    fun `Warns invalid String format args type {ints, positional args}`() {
        // Given
        val code =
            """ 
                fun test(): String {
                    return String.format("Ints %d %o %x", "not", "an", "int")
                }
            """.trimIndent()

        // When
        val findings = InvalidStringFormat()
            .compileAndLintWithContext(kotlinEnv.env, code)

        // Then
        assertThat(findings).hasSize(3)
    }

    @Test
    fun `Warns invalid String format args type {floats, positional args}`() {
        // Given
        val code =
            """ 
                fun test(): String {
                    return String.format("Floats %e %g %f %a", "not", "a", "float", "here")
                }
            """.trimIndent()

        // When
        val findings = InvalidStringFormat()
            .compileAndLintWithContext(kotlinEnv.env, code)

        // Then
        assertThat(findings).hasSize(4)
    }

    @Test
    fun `Warns invalid String format args type {chars, positional args}`() {
        // Given
        val code =
            """ 
                fun test(): String {
                    return String.format("Chars %c", "not a char")
                }
            """.trimIndent()

        // When
        val findings = InvalidStringFormat()
            .compileAndLintWithContext(kotlinEnv.env, code)

        // Then
        assertThat(findings).hasSize(1)
    }

    // endregion

    // region Test not relevant

    @Test
    fun `Ignores valid String format args type {ints, positional args}`() {
        // Given
        val code =
            """ 
                import java.math.BigInteger
                
                fun test(): String {
                    val l: Long = 4815162342L
                    val i: Int = 1337
                    val s: Short = 42
                    val b: Byte = 7
                    val bd: BigInteger = BigInteger.ONE
                    return String.format("Ints %d %o %x %d %X", l, i, s, b, bd)
                }
            """.trimIndent()

        // When
        val findings = InvalidStringFormat()
            .compileAndLintWithContext(kotlinEnv.env, code)

        // Then
        assertThat(findings).hasSize(0)
    }

    @Test
    fun `Ignores valid String format args type {floats, positional args}`() {
        // Given
        val code =
            """ 
                import java.math.BigDecimal
                
                fun test(): String {
                    val f: Float = 3.1415f
                    val d: Double = 1.61803398874989
                    val bd: BigDecimal = BigDecimal.ONE
                    val nf: Float? = null
                    return String.format("Floats %e %g %f %a", f, d, bd, nf)
                }
            """.trimIndent()

        // When
        val findings = InvalidStringFormat()
            .compileAndLintWithContext(kotlinEnv.env, code)

        // Then
        assertThat(findings).hasSize(0)
    }

    @Test
    fun `Ignores valid String format args type {chars, positional args}`() {
        // Given
        val code =
            """ 
                fun test(): String {
                    val i: Int = 1337
                    val s: Short = 42
                    val b: Byte = 7
                    val c: Char = 'x'
                    return String.format("Chars %c %c %c %c", i, s, b, c)
                }
            """.trimIndent()

        // When
        val findings = InvalidStringFormat()
            .compileAndLintWithContext(kotlinEnv.env, code)

        // Then
        assertThat(findings).hasSize(0)
    }

    @Test
    fun `Ignores format on non String class {fun}`() {
        // Given
        val code =
            """ 
                class Foo {
                    fun format(s: String) {
                    }
                }
                
                fun test(s: String): String {
                    val f = Foo()
                    return f.format(s)
                }
            """.trimIndent()

        // When
        val findings = InvalidStringFormat()
            .compileAndLintWithContext(kotlinEnv.env, code)

        // Then
        assertThat(findings).hasSize(0)
    }

    @Test
    fun `Ignores format on non String class {static fun}`() {
        // Given
        val code =
            """ 
                class Foo {
                    companion object {
                        fun format(s: String) {
                        }
                    }
                }
                
                fun test(s: String): String {
                    return Foo.format(s)
                }
            """.trimIndent()

        // When
        val findings = InvalidStringFormat()
            .compileAndLintWithContext(kotlinEnv.env, code)

        // Then
        assertThat(findings).hasSize(0)
    }

    @Test
    fun `Ignores non format fun on String class {fun}`() {
        // Given
        val code =
            """ 
                fun test(s: String): String {
                    return s.toLowercase()
                }
            """.trimIndent()

        // When
        val findings = InvalidStringFormat()
            .compileAndLintWithContext(kotlinEnv.env, code)

        // Then
        assertThat(findings).hasSize(0)
    }

    @Test
    fun `Ignores non format fun on String class {ext}`() {
        // Given
        val code =
            """ 
                fun test(s: String): String {
                    return s.lowercase()
                }
            """.trimIndent()

        // When
        val findings = InvalidStringFormat()
            .compileAndLintWithContext(kotlinEnv.env, code)

        // Then
        assertThat(findings).hasSize(0)
    }

    // endregion
}
