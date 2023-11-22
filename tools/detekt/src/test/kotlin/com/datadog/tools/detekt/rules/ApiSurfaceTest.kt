/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.tools.detekt.rules

import fr.xgouchet.elmyr.junit5.ForgeExtension
import io.github.detekt.test.utils.KotlinCoreEnvironmentWrapper
import io.github.detekt.test.utils.createEnvironment
import io.gitlab.arturbosch.detekt.test.TestConfig
import io.gitlab.arturbosch.detekt.test.assertThat
import io.gitlab.arturbosch.detekt.test.compileAndLintWithContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.io.File

@ExtendWith(ForgeExtension::class)
internal class ApiSurfaceTest {

    lateinit var kotlinEnv: KotlinCoreEnvironmentWrapper

    val fileName = "apiSurfaceOutput.log"

    @BeforeEach
    fun setup() {
        kotlinEnv = createEnvironment()
    }

    @AfterEach
    fun tearDown() {
        kotlinEnv.dispose()
        File(fileName).delete()
    }

    @Test
    fun `generateApiSurface`() {
        // Given
        val config = TestConfig(
            mapOf("outputFileName" to fileName)
        )
        val code =
            """
                package com.example.foo
                
                class Foo {
                    
                    fun bar(s: String): Int {
                        return s.length
                    }
                }
            """.trimIndent()

        // When
        val findings = ApiSurface(config)
            .compileAndLintWithContext(kotlinEnv.env, code)

        // Then
        assertThat(findings).hasSize(0)
        val output = File(fileName).readText()
        assertThat(output).isEqualTo("com.example.foo.Foo.bar(kotlin.String)\n")
    }

    @Test
    fun `generateApiSurface {ignore internal fun}`() {
        // Given
        val config = TestConfig(
            mapOf("outputFileName" to fileName)
        )
        val code =
            """
                package com.example.foo
                
                class Foo {
                    
                    fun bar(s: String): Int {
                        return s.length
                    }
                    
                    internal fun baz(s: String): Int {
                        return s.length
                    }
                }
            """.trimIndent()

        // When
        val findings = ApiSurface(config)
            .compileAndLintWithContext(kotlinEnv.env, code)

        // Then
        assertThat(findings).hasSize(0)
        val output = File(fileName).readText()
        assertThat(output).isEqualTo("com.example.foo.Foo.bar(kotlin.String)\n")
    }

    @Test
    fun `generateApiSurface {ignore private fun}`() {
        // Given
        val config = TestConfig(
            mapOf("outputFileName" to fileName)
        )
        val code =
            """
                package com.example.foo
                
                class Foo {
                    
                    fun bar(s: String): Int {
                        return s.length
                    }
                    
                    private fun baz(s: String): Int {
                        return s.length
                    }
                }
            """.trimIndent()

        // When
        val findings = ApiSurface(config)
            .compileAndLintWithContext(kotlinEnv.env, code)

        // Then
        assertThat(findings).hasSize(0)
        val output = File(fileName).readText()
        assertThat(output).isEqualTo("com.example.foo.Foo.bar(kotlin.String)\n")
    }
}
