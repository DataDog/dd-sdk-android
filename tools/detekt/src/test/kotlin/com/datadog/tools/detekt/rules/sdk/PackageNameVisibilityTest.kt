/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.tools.detekt.rules.sdk

import com.datadog.tools.detekt.rules.test.FakeAnnotation
import fr.xgouchet.elmyr.annotation.BoolForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.annotation.StringForgeryType
import fr.xgouchet.elmyr.junit5.ForgeExtension
import io.github.detekt.test.utils.KotlinCoreEnvironmentWrapper
import io.github.detekt.test.utils.createEnvironment
import io.gitlab.arturbosch.detekt.test.TestConfig
import io.gitlab.arturbosch.detekt.test.assertThat
import io.gitlab.arturbosch.detekt.test.lint
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

@ExtendWith(ForgeExtension::class)
class PackageNameVisibilityTest {

    lateinit var kotlinEnv: KotlinCoreEnvironmentWrapper

    @BoolForgery
    var fakeWithBreakingChanges: Boolean = false

    @StringForgery(StringForgeryType.ALPHABETICAL)
    lateinit var fakeIgnoredAnnotation: String

    @BeforeEach
    fun setup() {
        kotlinEnv = createEnvironment()
    }

    @AfterEach
    fun tearDown() {
        kotlinEnv.dispose()
    }

    @ParameterizedTest
    @MethodSource("types")
    fun `detekt internal top level type in public package`(type: String) {
        val config = TestConfig("withBreakingChanges" to fakeWithBreakingChanges)
        val code =
            """
            package com.datadog.android.tools.detekt
            
            internal $type Foo {
            }
            """.trimIndent()

        val findings = PackageNameVisibility(config).lint(code)
        assertThat(findings).hasSize(1)
    }

    @Test
    fun `detekt internal top level data class in public package`() {
        val config = TestConfig("withBreakingChanges" to fakeWithBreakingChanges)
        val code =
            """
            package com.datadog.android.tools.detekt
            
            internal data class Foo(val i :Int) {
            }
            """.trimIndent()

        val findings = PackageNameVisibility(config).lint(code)
        assertThat(findings).hasSize(1)
    }

    @Test
    fun `detekt internal top level fun in public package`() {
        val config = TestConfig("withBreakingChanges" to fakeWithBreakingChanges)
        val code =
            """
            package com.datadog.android.tools.detekt
            
            fun foo() {
                bar()
            }
            
            internal fun bar() {
            }
            """.trimIndent()

        val findings = PackageNameVisibility(config).lint(code)
        assertThat(findings).hasSize(1)
    }

    @ParameterizedTest
    @MethodSource("fields")
    fun `ignores internal top level field in public package`(field: String) {
        val config = TestConfig("withBreakingChanges" to fakeWithBreakingChanges)
        val code =
            """
            package com.datadog.android.tools.detekt
            
            internal $field foo: String = ""
            """.trimIndent()

        val findings = PackageNameVisibility(config).lint(code)
        assertThat(findings).isEmpty()
    }

    @ParameterizedTest
    @MethodSource("types")
    fun `detekt public top level type in internal package {withBreakingChanges=true}`(type: String) {
        val config = TestConfig("withBreakingChanges" to true)
        val code =
            """
            package com.datadog.android.tools.detekt.internal.data
            
            $type Foo {
            }
            """.trimIndent()

        val findings = PackageNameVisibility(config).lint(code)
        assertThat(findings).hasSize(1)
    }

    @Test
    fun `detekt public top level data class in internal package {withBreakingChanges=true}`() {
        val config = TestConfig("withBreakingChanges" to true)
        val code =
            """
            package com.datadog.android.tools.detekt.internal.data
            
            data class Foo(val i: Int) {
            }
            """.trimIndent()

        val findings = PackageNameVisibility(config).lint(code)
        assertThat(findings).hasSize(1)
    }

    @Test
    fun `detekt public top level fun in internal package {withBreakingChanges=true}`() {
        val config = TestConfig("withBreakingChanges" to true)
        val code =
            """
            package com.datadog.android.tools.detekt.internal.data
            
            fun foo() {
            }
            """.trimIndent()

        val findings = PackageNameVisibility(config).lint(code)
        assertThat(findings).hasSize(1)
    }

    @ParameterizedTest
    @MethodSource("fields")
    fun `detekt public top level field in internal package {withBreakingChanges=true}`(field: String) {
        val config = TestConfig("withBreakingChanges" to true)
        val code =
            """
            package com.datadog.android.tools.detekt.internal.data
            
            $field foo: String = ""
            """.trimIndent()

        val findings = PackageNameVisibility(config).lint(code)
        assertThat(findings).hasSize(1)
    }

    @ParameterizedTest
    @MethodSource("types")
    fun `ignores public top level type in internal package {withBreakingChanges=false}`(type: String) {
        val config = TestConfig("withBreakingChanges" to false)
        val code =
            """
            package com.datadog.android.tools.detekt.internal.data
            
            $type Foo {
            }
            """.trimIndent()

        val findings = PackageNameVisibility(config).lint(code)
        assertThat(findings).isEmpty()
    }

    @Test
    fun `ignores public top level data class in internal package {withBreakingChanges=false}`() {
        val config = TestConfig("withBreakingChanges" to false)
        val code =
            """
            package com.datadog.android.tools.detekt.internal.data
            
            data class Foo(val i: Int) {
            }
            """.trimIndent()

        val findings = PackageNameVisibility(config).lint(code)
        assertThat(findings).isEmpty()
    }

    @Test
    fun `ignores public top level fun in internal package {withBreakingChanges=false}`() {
        val config = TestConfig("withBreakingChanges" to false)
        val code =
            """
            package com.datadog.android.tools.detekt.internal.data
            
            fun foo() {
            }
            """.trimIndent()

        val findings = PackageNameVisibility(config).lint(code)
        assertThat(findings).isEmpty()
    }

    @ParameterizedTest
    @MethodSource("fields")
    fun `ignore public top level field in internal package {withBreakingChanges=false}`(field: String) {
        val config = TestConfig("withBreakingChanges" to false)
        val code =
            """
            package com.datadog.android.tools.detekt.internal.data
            
            internal $field foo: String = ""
            """.trimIndent()

        val findings = PackageNameVisibility(config).lint(code)
        assertThat(findings).isEmpty()
    }

    @ParameterizedTest
    @MethodSource("types")
    fun `ignores public top level type in internal package {withBreakingChanges=true, annotated}`(type: String) {
        val annotationFqName = FakeAnnotation::class.java.canonicalName
        val config = TestConfig("withBreakingChanges" to true, "ignoredAnnotations" to listOf(annotationFqName))
        val code =
            """
            package com.datadog.android.tools.detekt.internal.data
            
            import $annotationFqName
            
            @FakeAnnotation
            $type Foo {
            }
            """.trimIndent()

        val findings = PackageNameVisibility(config).lint(code)
        assertThat(findings).isEmpty()
    }

    @Test
    fun `ignores public top level data class in internal package {withBreakingChanges=true, annotated}`() {
        val annotationFqName = FakeAnnotation::class.java.canonicalName
        val config = TestConfig("withBreakingChanges" to true, "ignoredAnnotations" to listOf(annotationFqName))
        val code =
            """
            package com.datadog.android.tools.detekt.internal.data
            
            import $annotationFqName
            
            @FakeAnnotation
            data class Foo(val i: Int) {
            }
            """.trimIndent()

        val findings = PackageNameVisibility(config).lint(code)
        assertThat(findings).isEmpty()
    }

    @Test
    fun `ignores public top level fun in internal package {withBreakingChanges=true, annotated}`() {
        val annotationFqName = FakeAnnotation::class.java.canonicalName
        val config = TestConfig("withBreakingChanges" to true, "ignoredAnnotations" to listOf(annotationFqName))
        val code =
            """
            package com.datadog.android.tools.detekt.internal.data
            
            import $annotationFqName
            
            @FakeAnnotation
            fun foo() {
            }
            """.trimIndent()

        val findings = PackageNameVisibility(config).lint(code)
        assertThat(findings).isEmpty()
    }

    @ParameterizedTest
    @MethodSource("fields")
    fun `ignore public top level field in internal package {withBreakingChanges=true, annotated}`(field: String) {
        val annotationFqName = FakeAnnotation::class.java.canonicalName
        val config = TestConfig("withBreakingChanges" to true, "ignoredAnnotations" to listOf(annotationFqName))
        val code =
            """
            package com.datadog.android.tools.detekt.internal.data
            
            import $annotationFqName
            
            @FakeAnnotation
            internal $field foo: String = ""
            """.trimIndent()

        val findings = PackageNameVisibility(config).lint(code)
        assertThat(findings).isEmpty()
    }

    @ParameterizedTest
    @MethodSource("types")
    fun `ignore internal nested type in public package`(type: String) {
        val config = TestConfig("withBreakingChanges" to fakeWithBreakingChanges)
        val code =
            """
            package com.datadog.android.tools.detekt
            
            class Foo {
                internal $type Bar {}
            }
            """.trimIndent()

        val findings = PackageNameVisibility(config).lint(code)
        assertThat(findings).isEmpty()
    }

    @Test
    fun `ignore internal nested data class in public package`() {
        val config = TestConfig("withBreakingChanges" to fakeWithBreakingChanges)
        val code =
            """
            package com.datadog.android.tools.detekt
            
            class Foo {
                internal data class Bar(val i: Int)
            }
            """.trimIndent()

        val findings = PackageNameVisibility(config).lint(code)
        assertThat(findings).isEmpty()
    }

    @Test
    fun `ignore internal nested fun in public package`() {
        val config = TestConfig("withBreakingChanges" to fakeWithBreakingChanges)
        val code =
            """
            package com.datadog.android.tools.detekt
            
            class Foo {
                internal fun bar() {}
            }
            """.trimIndent()

        val findings = PackageNameVisibility(config).lint(code)
        assertThat(findings).isEmpty()
    }

    @ParameterizedTest
    @MethodSource("fields")
    fun `ignore internal nested field in public package`(field: String) {
        val config = TestConfig("withBreakingChanges" to false)
        val code =
            """
            package com.datadog.android.tools.detekt
            
            class Foo {
                internal $field foo: String = ""
            }
            """.trimIndent()

        val findings = PackageNameVisibility(config).lint(code)
        assertThat(findings).isEmpty()
    }

    @ParameterizedTest
    @MethodSource("types")
    fun `ignore public nested type in internal package`(type: String) {
        val config = TestConfig("withBreakingChanges" to fakeWithBreakingChanges)
        val code =
            """
            package com.datadog.android.tools.detekt.internal.data
            
            internal class Foo {
                $type Bar {}
            }
            """.trimIndent()

        val findings = PackageNameVisibility(config).lint(code)
        assertThat(findings).isEmpty()
    }

    @Test
    fun `ignore public nested data class in internal package`() {
        val config = TestConfig("withBreakingChanges" to fakeWithBreakingChanges)
        val code =
            """
            package com.datadog.android.tools.detekt.internal.data
            
            internal class Foo {
                data class Bar(val i: Int)
            }
            """.trimIndent()

        val findings = PackageNameVisibility(config).lint(code)
        assertThat(findings).isEmpty()
    }

    @Test
    fun `ignore public nested fun in internal package`() {
        val config = TestConfig("withBreakingChanges" to fakeWithBreakingChanges)
        val code =
            """
            package com.datadog.android.tools.detekt.internal.data
            
            internal class Foo {
                fun bar() {}
            }
            """.trimIndent()

        val findings = PackageNameVisibility(config).lint(code)
        assertThat(findings).isEmpty()
    }

    @ParameterizedTest
    @MethodSource("fields")
    fun `ignore public nested field in internal package`(field: String) {
        val config = TestConfig("withBreakingChanges" to false)
        val code =
            """
            package com.datadog.android.tools.detekt.internal.data
            
            internal class Foo {
                $field foo: String = ""
            }
            """.trimIndent()

        val findings = PackageNameVisibility(config).lint(code)
        assertThat(findings).isEmpty()
    }

    companion object {

        @JvmStatic
        fun types(): Stream<Arguments> {
            return Stream.of(
                Arguments.of("interface"),
                Arguments.of("class"),
                Arguments.of("enum class"),
                Arguments.of("sealed class"),
                Arguments.of("object")
            )
        }

        @JvmStatic
        fun fields(): Stream<Arguments> {
            return Stream.of(
                Arguments.of("var"),
                Arguments.of("val")
            )
        }
    }
}
