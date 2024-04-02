/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.tools.detekt.rules.sdk

import fr.xgouchet.elmyr.junit5.ForgeExtension
import io.github.detekt.test.utils.KotlinCoreEnvironmentWrapper
import io.github.detekt.test.utils.createEnvironment
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.test.TestConfig
import io.gitlab.arturbosch.detekt.test.assertThat
import io.gitlab.arturbosch.detekt.test.compileAndLintWithContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(ForgeExtension::class)
internal class UnsafeThirdPartyFunctionCallTest {

    lateinit var kotlinEnv: KotlinCoreEnvironmentWrapper

    @BeforeEach
    fun setup() {
        kotlinEnv = createEnvironment()
    }

    @AfterEach
    fun tearDown() {
        kotlinEnv.dispose()
    }

    @Test
    fun `ignore call on internal type`() {
        // Given
        val config = TestConfig(
            mapOf("internalPackagePrefix" to "java.io")
        )
        val code =
            """
                import java.io.File
                
                fun test(f: File): Any {
                    return f.inputStream()
                }
            """.trimIndent()

        // When
        val findings = UnsafeThirdPartyFunctionCall(config)
            .compileAndLintWithContext(kotlinEnv.env, code)

        // Then
        assertThat(findings).hasSize(0)
    }

    @Test
    fun `detekt unsafe call on known third party function`() {
        // Given
        val knownThrowingCalls = listOf(
            "java.io.File.inputStream():java.io.FileNotFoundException"
        )
        val config = TestConfig(
            mapOf("knownThrowingCalls" to knownThrowingCalls)
        )
        val code =
            """
                import java.io.File
                
                fun test(f: File): Any {
                    return f.inputStream()
                }
            """.trimIndent()

        // When
        val findings = UnsafeThirdPartyFunctionCall(config)
            .compileAndLintWithContext(kotlinEnv.env, code)

        // Then
        assertThat(findings).hasSize(1)
    }

    @Test
    fun `detekt unsafe call on unknown third party function`() {
        // Given
        val code =
            """
                import java.io.File
                
                fun test(f: File): Any {
                    return f.inputStream()
                }
            """.trimIndent()

        // When
        val findings = UnsafeThirdPartyFunctionCall(TestConfig())
            .compileAndLintWithContext(kotlinEnv.env, code)

        // Then
        assertThat(findings).hasSize(1)
    }

    @Test
    fun `detekt unsafe call on unknown third party function { implicit receiver }`() {
        // Given
        val code =
            """
                import java.io.File
                
                fun test(f: File): Any {
                    return with(f) { inputStream() }
                }
            """.trimIndent()

        // When
        val findings = UnsafeThirdPartyFunctionCall(TestConfig())
            .compileAndLintWithContext(kotlinEnv.env, code)

        // Then
        assertThat(findings).hasSize(1)
    }

    @Test
    fun `detekt unsafe call on unknown third party function { explicit it receiver }`() {
        // Given
        @Suppress("SimpleRedundantLet")
        val code =
            """
                import java.io.File
                
                fun test(f: File): Any {
                    return f.let { it.inputStream() }
                }
            """.trimIndent()

        // When
        val findings = UnsafeThirdPartyFunctionCall(TestConfig())
            .compileAndLintWithContext(kotlinEnv.env, code)

        // Then
        assertThat(findings).hasSize(1)
    }

    @Test
    fun `detekt unsafe call on known third party function {disambiguate signatures}`() {
        // Given
        val knownThrowingCalls = listOf(
            "java.io.File.listFiles(java.io.FileFilter?):java.io.FileNotFoundException"
        )
        val config = TestConfig(
            mapOf(
                "knownThrowingCalls" to knownThrowingCalls,
                "treatUnknownFunctionAsThrowing" to false
            )
        )
        val code =
            """
                import java.io.File
                import java.io.FilenameFilter
                import java.io.FileFilter
                
                fun test(f: File, ff: FileFilter, fnf: FilenameFilter) {
                    f.listFiles(ff)
                    f.listFiles(fnf)
                }
            """.trimIndent()

        // When
        val findings = UnsafeThirdPartyFunctionCall(config)
            .compileAndLintWithContext(kotlinEnv.env, code)

        // Then
        assertThat(findings).hasSize(1)
    }

    @Test
    fun `detekt call with missing catch on known third party function`() {
        // Given
        val knownThrowingCalls = listOf(
            "java.io.File.inputStream():java.io.FileNotFoundException"
        )
        val config = TestConfig(
            mapOf("knownThrowingCalls" to knownThrowingCalls)
        )
        val code =
            """
                import java.io.File
                import java.lang.ArrayIndexOutOfBoundsException
                
                fun test(f: File): Any {
                    try {
                        return f.inputStream()
                    } catch (e: ArrayIndexOutOfBoundsException) {
                        return null
                    }
                }
            """.trimIndent()

        // When
        val findings = UnsafeThirdPartyFunctionCall(config)
            .compileAndLintWithContext(kotlinEnv.env, code)

        // Then
        assertThat(findings).hasSize(1)
    }

    @Test
    fun `ignores safe call on known third party function`() {
        // Given
        val knownThrowingCalls = listOf(
            "java.io.File.inputStream():java.io.FileNotFoundException"
        )
        val config = TestConfig(
            mapOf("knownThrowingCalls" to knownThrowingCalls)
        )
        val code =
            """
                import java.io.File
                import java.io.FileNotFoundException
                import java.lang.ArrayIndexOutOfBoundsException
                
                fun test(f: File): Any? {
                    try {
                        return f.inputStream()
                    } catch (e: FileNotFoundException) {
                        return null
                    } catch (e: ArrayIndexOutOfBoundsException) {
                        return null
                    }
                }
            """.trimIndent()

        // When
        val findings = UnsafeThirdPartyFunctionCall(config)
            .compileAndLintWithContext(kotlinEnv.env, code)

        // Then
        assertThat(findings).hasSize(0)
    }

    @Test
    fun `ignores super-safe call on known third party function {Catch any Exception}`() {
        // Given
        val knownThrowingCalls = listOf(
            "java.io.File.inputStream():java.io.FileNotFoundException"
        )
        val config = TestConfig(
            mapOf("knownThrowingCalls" to knownThrowingCalls)
        )
        val code =
            """
                import java.io.File
                import java.io.FileNotFoundException
                import java.lang.ArrayIndexOutOfBoundsException
                
                fun test(f: File): Any? {
                    try {
                        return f.inputStream()
                    } catch (e: Exception) {
                        return null
                    }
                }
            """.trimIndent()

        // When
        val findings = UnsafeThirdPartyFunctionCall(config)
            .compileAndLintWithContext(kotlinEnv.env, code)

        // Then
        assertThat(findings).hasSize(0)
    }

    @Test
    fun `ignores super-safe call on known third party function {Catch any Throwable}`() {
        // Given
        val knownThrowingCalls = listOf(
            "java.io.File.inputStream():java.io.FileNotFoundException"
        )
        val config = TestConfig(
            mapOf("knownThrowingCalls" to knownThrowingCalls)
        )
        val code =
            """
                import java.io.File
                import java.io.FileNotFoundException
                import java.lang.ArrayIndexOutOfBoundsException
                
                fun test(f: File): Any? {
                    try {
                        return f.inputStream()
                    } catch (e: Throwable) {
                        return null
                    }
                }
            """.trimIndent()

        // When
        val findings = UnsafeThirdPartyFunctionCall(config)
            .compileAndLintWithContext(kotlinEnv.env, code)

        // Then
        assertThat(findings).hasSize(0)
    }

    @Test
    fun `ignores safe call on known third party function {Catch exception with alias}`() {
        // Given
        val knownThrowingCalls = listOf(
            "java.io.File.inputStream():java.io.FileNotFoundException"
        )
        val config = TestConfig(
            mapOf("knownThrowingCalls" to knownThrowingCalls)
        )
        val code =
            """
                import java.io.File
                import java.io.FileNotFoundException
                import java.lang.ArrayIndexOutOfBoundsException
                
                actual typealias FNE = java.io.FileNotFoundException
                
                fun test(f: File): Any? {
                    try {
                        return f.inputStream()
                    } catch (e: FNE) {
                        return null
                    }
                }
            """.trimIndent()

        // When
        val findings = UnsafeThirdPartyFunctionCall(config)
            .compileAndLintWithContext(kotlinEnv.env, code)

        // Then
        assertThat(findings).hasSize(0)
    }

    @Test
    fun `ignores nested safe call on known third party function`() {
        // Given
        val knownThrowingCalls = listOf(
            "java.io.File.inputStream():java.io.FileNotFoundException"
        )
        val config = TestConfig(
            mapOf("knownThrowingCalls" to knownThrowingCalls)
        )
        val code =
            """
                import java.io.File
                import java.io.FileNotFoundException
                import java.io.IOException
                import java.lang.ArrayIndexOutOfBoundsException
                
                fun test(f: File): Any? {
                    try {
                        try {
                            return f.inputStream()
                        } catch (e: IOException) {
                            return null
                        } catch (e: FileNotFoundException) {
                            return null
                        }
                    } catch (e: FileNotFoundException) {
                        return null
                    } catch (e: ArrayIndexOutOfBoundsException) {
                        return null
                    }
                }
            """.trimIndent()

        // When
        val findings = UnsafeThirdPartyFunctionCall(config)
            .compileAndLintWithContext(kotlinEnv.env, code)

        // Then
        assertThat(findings).hasSize(0)
    }

    @Test
    fun `ignores call on known safe third party function`() {
        // Given
        val knownSafeCalls = listOf(
            "java.io.File.inputStream()"
        )
        val config = TestConfig(
            mapOf("knownSafeCalls" to knownSafeCalls)
        )
        val code =
            """
                import java.io.File
                
                fun test(f: File): Any {
                    return f.inputStream()
                }
            """.trimIndent()

        // When
        val findings = UnsafeThirdPartyFunctionCall(config)
            .compileAndLintWithContext(kotlinEnv.env, code)

        // Then
        assertThat(findings).hasSize(0)
    }

    @Test
    fun `detekt unsafe call on unknown function {treatUnknownFunctionAsThrowing=true}`() {
        // Given
        val config = TestConfig(
            mapOf("treatUnknownFunctionAsThrowing" to true)
        )
        val code =
            """
                import java.io.File
                
                fun test(f: File): Any {
                    return f.inputStream()
                }
            """.trimIndent()

        // When
        val findings = UnsafeThirdPartyFunctionCall(config)
            .compileAndLintWithContext(kotlinEnv.env, code)

        // Then
        assertThat(findings).hasSize(1)
    }

    @Test
    fun `ignore calls on unknown function {treatUnknownFunctionAsThrowing=false}`() {
        // Given
        val config = TestConfig(
            mapOf("treatUnknownFunctionAsThrowing" to false)
        )
        val code =
            """
                import java.io.File
                
                fun test(f: File): Any {
                    return f.inputStream()
                }
            """.trimIndent()

        // When
        val findings = UnsafeThirdPartyFunctionCall(config)
            .compileAndLintWithContext(kotlinEnv.env, code)

        // Then
        assertThat(findings).hasSize(0)
    }

    @Test
    fun `detekt unsafe call on unknown constructor {treatUnknownFunctionAsThrowing=true}`() {
        // Given
        val config = TestConfig(
            mapOf("treatUnknownFunctionAsThrowing" to true)
        )
        val code =
            """
                import java.io.File
                
                fun test(): File {
                    return File("/path")
                }
            """.trimIndent()

        // When
        val findings = UnsafeThirdPartyFunctionCall(config)
            .compileAndLintWithContext(kotlinEnv.env, code)

        // Then
        assertThat(findings).hasSize(1)
    }

    @Test
    fun `ignore calls on unknown constructor {treatUnknownFunctionAsThrowing=false}`() {
        // Given
        val config = TestConfig(
            mapOf("treatUnknownFunctionAsThrowing" to false)
        )
        val code =
            """
                import java.io.File
                
                fun test(): File {
                    return File("/path")
                }
            """.trimIndent()

        // When
        val findings = UnsafeThirdPartyFunctionCall(config)
            .compileAndLintWithContext(kotlinEnv.env, code)

        // Then
        assertThat(findings).hasSize(0)
    }

    @Test
    fun `ignore kotlin helper calls { let + apply + println + toString }`() {
        // Given
        val code =
            """
                import java.io.File
                
                fun test(f: File?): Any {
                    return f?.let{
                        it.apply {
                            println(this.toString())
                        }
                    }
                }
            """.trimIndent()

        // When
        val findings = UnsafeThirdPartyFunctionCall(Config.empty)
            .compileAndLintWithContext(kotlinEnv.env, code)

        // Then
        assertThat(findings).hasSize(0)
    }

    @Test
    fun `ignore kotlin helper calls { with + run + also + println }`() {
        // Given
        val code =
            """
                import java.io.File
                
                fun test(f: File?): Any {
                    with(file) {
                        this.run {
                            this.readBytes().also { 
                                println(it)
                            }
                        }
                    } 
                }
            """.trimIndent()

        // When
        val findings = UnsafeThirdPartyFunctionCall(Config.empty)
            .compileAndLintWithContext(kotlinEnv.env, code)

        // Then
        assertThat(findings).hasSize(0)
    }

    @Test
    fun `ignore kotlin helper calls { invoke }`() {
        // Given
        val code =
            """
                import java.io.File
                
                fun test(file: File, predicate: File.() -> Boolean): Boolean {
                    return predicate(file)
                }
            """.trimIndent()

        // When
        val findings = UnsafeThirdPartyFunctionCall(Config.empty)
            .compileAndLintWithContext(kotlinEnv.env, code)

        // Then
        assertThat(findings).hasSize(0)
    }
}
