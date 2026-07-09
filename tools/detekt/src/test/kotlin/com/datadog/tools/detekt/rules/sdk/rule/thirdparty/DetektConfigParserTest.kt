/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.tools.detekt.rules.sdk.rule.thirdparty

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

internal class DetektConfigParserTest {

    // region No wildcard — exact match

    @Test
    fun `M match exact call W signaturePatternOf() {no wildcard}`() {
        val pattern = "java.io.File.inputStream()".toRegex()

        assertThat(pattern.matches("java.io.File.inputStream()")).isTrue()
    }

    @Test
    fun `M not match different call W signaturePatternOf() {no wildcard}`() {
        val pattern = "java.io.File.inputStream()".toRegex()

        assertThat(pattern.matches("java.io.File.outputStream()")).isFalse()
    }

    @Test
    fun `M not match partial call W signaturePatternOf() {dots not treated as regex wildcards}`() {
        // dots in package names must be escaped so they don't act as regex "any char"
        val pattern = "java.io.File.inputStream()".toRegex()

        assertThat(pattern.matches("javaXioXFileXinputStream()")).isFalse()
    }

    @Test
    fun `M match call with params W signaturePatternOf() {no wildcard, exact params}`() {
        val pattern = "java.io.File.listFiles(java.io.FileFilter?)".toRegex()

        assertThat(pattern.matches("java.io.File.listFiles(java.io.FileFilter?)")).isTrue()
        assertThat(pattern.matches("java.io.File.listFiles(java.io.FileFilter)")).isTrue()
        assertThat(pattern.matches("java.io.File.listFiles(java.io.FilenameFilter?)")).isFalse()
    }

    @Test
    fun `M not match nullable call W signaturePatternOf() {non-nullable concrete param}`() {
        val pattern = "java.io.File.listFiles(java.io.FileFilter)".toRegex()

        assertThat(pattern.matches("java.io.File.listFiles(java.io.FileFilter?)")).isFalse()
    }

    @Test
    fun `M not match non-nullable call W signaturePatternOf() {throwing nullable concrete param}`() {
        val pattern = DetektConfigParser()
            .parseThrowingCalls(
                listOf("java.util.concurrent.ConcurrentHashMap.remove(kotlin.String?):java.lang.NullPointerException")
            )
            .first()
            .regex

        assertThat(pattern.matches("java.util.concurrent.ConcurrentHashMap.remove(kotlin.String)")).isFalse()
    }

    // endregion

    // region NON_NULL_WILDCARD — non-nullable types only

    @ParameterizedTest
    @MethodSource("provideNonNullableWildcardMatches")
    fun `M match call W signaturePatternOf() {NON_NULL_WILDCARD matches non-nullable param type}`(
        pattern: String,
        call: String
    ) {
        assertThat(pattern.toRegex().matches(call)).isTrue()
    }

    @ParameterizedTest
    @MethodSource("provideNonNullableWildcardNonMatches")
    fun `M not match call W signaturePatternOf() {NON_NULL_WILDCARD does not match nullable or wrong arity}`(
        pattern: String,
        call: String
    ) {
        assertThat(pattern.toRegex().matches(call)).isFalse()
    }

    // endregion

    // region NULLABLE_WILDCARD — any types (nullable and non-nullable)

    @ParameterizedTest
    @MethodSource("provideNullableWildcardMatches")
    fun `M match call W signaturePatternOf() {NULLABLE_WILDCARD matches any param type}`(
        pattern: String,
        call: String
    ) {
        assertThat(pattern.toRegex().matches(call)).isTrue()
    }

    @ParameterizedTest
    @MethodSource("provideNullableWildcardNonMatches")
    fun `M not match call W signaturePatternOf() {NULLABLE_WILDCARD does not match wrong arity}`(
        pattern: String,
        call: String
    ) {
        assertThat(pattern.toRegex().matches(call)).isFalse()
    }

    // endregion

    // region Multiple wildcards

    @Test
    fun `M match call W signaturePatternOf() {two NON_NULL_WILDCARDs match two non-nullable types}`() {
        val pattern = "kotlin.collections.MutableList.add(*, *)".toRegex()

        assertThat(pattern.matches("kotlin.collections.MutableList.add(kotlin.Int, kotlin.String)")).isTrue()
        assertThat(pattern.matches("kotlin.collections.MutableList.add(kotlin.Int, java.io.File)")).isTrue()
    }

    @Test
    fun `M match call W signaturePatternOf() {NON_NULL_WILDCARD and NULLABLE_WILDCARD}`() {
        val pattern = "kotlin.collections.MutableMap.put(*, ?)".toRegex()

        assertThat(pattern.matches("kotlin.collections.MutableMap.put(kotlin.String, kotlin.Any?)")).isTrue()
        assertThat(pattern.matches("kotlin.collections.MutableMap.put(kotlin.String, kotlin.String?)")).isTrue()
    }

    @Test
    fun `M not match call W signaturePatternOf() {NON_NULL_WILDCARD rejects nullable first param}`() {
        val pattern = "kotlin.collections.MutableMap.put(*, ?)".toRegex()

        // first param is nullable, rejected by `*`
        assertThat(pattern.matches("kotlin.collections.MutableMap.put(kotlin.String?, kotlin.Any)")).isFalse()
    }

    @Test
    fun `M not match call W signaturePatternOf() {two-wildcard pattern does not match single-param call}`() {
        val pattern = "kotlin.collections.MutableList.add(*, *)".toRegex()

        assertThat(pattern.matches("kotlin.collections.MutableList.add(kotlin.String)")).isFalse()
    }

    @Test
    fun `M not match call W signaturePatternOf() {two-wildcard pattern does not match three-param call}`() {
        val pattern = "kotlin.collections.MutableList.add(*, *)".toRegex()

        assertThat(
            pattern.matches("kotlin.collections.MutableList.add(kotlin.Int, kotlin.String, kotlin.Boolean)")
        ).isFalse()
    }

    // endregion

    // region Space-tolerant separator

    @Test
    fun `M match call W signaturePatternOf() {no space after comma in config entry}`() {
        val pattern = "kotlin.collections.MutableList.add(kotlin.Int,kotlin.String)".toRegex()

        assertThat(pattern.matches("kotlin.collections.MutableList.add(kotlin.Int, kotlin.String)")).isTrue()
    }

    @Test
    fun `M produce same regex W signaturePatternOf() {space after comma vs no space}`() {
        val withSpace = "kotlin.collections.MutableList.add(kotlin.Int, kotlin.String)".toRegex()
        val noSpace = "kotlin.collections.MutableList.add(kotlin.Int,kotlin.String)".toRegex()

        assertThat(noSpace.pattern).isEqualTo(withSpace.pattern)
    }

    @Test
    fun `M match wildcard call W signaturePatternOf() {no space after comma with wildcard}`() {
        val pattern = "kotlin.collections.MutableMap.put(*,?)".toRegex()

        assertThat(pattern.matches("kotlin.collections.MutableMap.put(kotlin.String, kotlin.Any?)")).isTrue()
        assertThat(pattern.matches("kotlin.collections.MutableMap.put(kotlin.String?, kotlin.Any)")).isFalse()
    }

    @Test
    fun `M trim exception names W parseThrowingCalls() {space after comma}`() {
        val exceptions = DetektConfigParser()
            .parseThrowingCalls(
                listOf("java.io.File.inputStream():java.io.FileNotFoundException, java.lang.SecurityException")
            )
            .first()
            .exceptions

        assertThat(exceptions).containsExactly(
            "java.io.FileNotFoundException",
            "java.lang.SecurityException"
        )
    }

    // endregion

    companion object {
        private fun String.toRegex(): Regex = DetektConfigParser.parseEntry(this).regex

        @JvmStatic
        fun provideNonNullableWildcardMatches(): List<Arguments> = listOf(
            Arguments.of(
                "kotlin.collections.MutableList.add(*)",
                "kotlin.collections.MutableList.add(kotlin.String)"
            ),
            Arguments.of(
                "kotlin.collections.MutableList.add(*)",
                "kotlin.collections.MutableList.add(java.io.File)"
            ),
            Arguments.of(
                "kotlin.collections.MutableList.add(kotlin.Int, *)",
                "kotlin.collections.MutableList.add(kotlin.Int, kotlin.String)"
            ),
            Arguments.of(
                "kotlin.collections.MutableList.add(kotlin.Int, *)",
                "kotlin.collections.MutableList.add(kotlin.Int, com.example.MyClass)"
            ),
            Arguments.of(
                "kotlin.collections.MutableList.add(*, kotlin.String)",
                "kotlin.collections.MutableList.add(kotlin.Int, kotlin.String)"
            )
        )

        @JvmStatic
        fun provideNonNullableWildcardNonMatches(): List<Arguments> = listOf(
            Arguments.of(
                "kotlin.collections.MutableList.add(kotlin.Int, *)",
                "kotlin.collections.MutableList.add(kotlin.Int, kotlin.String?)"
            ),
            Arguments.of(
                "kotlin.collections.MutableList.add(*)",
                "kotlin.collections.MutableList.add(kotlin.Int, kotlin.String)"
            ),
            Arguments.of(
                "kotlin.collections.MutableList.add(kotlin.Int, *)",
                "kotlin.collections.MutableList.add(kotlin.String)"
            )
        )

        @JvmStatic
        fun provideNullableWildcardMatches(): List<Arguments> = listOf(
            Arguments.of(
                "kotlin.collections.MutableList.add(?)",
                "kotlin.collections.MutableList.add(kotlin.String?)"
            ),
            Arguments.of(
                "kotlin.collections.MutableList.add(?)",
                "kotlin.collections.MutableList.add(kotlin.String)"
            ),
            Arguments.of(
                "kotlin.collections.MutableList.add(kotlin.Int, ?)",
                "kotlin.collections.MutableList.add(kotlin.Int, kotlin.String?)"
            ),
            Arguments.of(
                "kotlin.collections.MutableList.add(kotlin.Int, ?)",
                "kotlin.collections.MutableList.add(kotlin.Int, kotlin.String)"
            ),
            Arguments.of(
                "kotlin.collections.MutableList.add(kotlin.Int, ?)",
                "kotlin.collections.MutableList.add(kotlin.Int, java.io.File?)"
            ),
            Arguments.of(
                "kotlin.collections.MutableList.add(?, kotlin.String)",
                "kotlin.collections.MutableList.add(kotlin.Int?, kotlin.String)"
            ),
            Arguments.of(
                "kotlin.collections.MutableList.add(?, kotlin.String)",
                "kotlin.collections.MutableList.add(kotlin.Int, kotlin.String)"
            )
        )

        @JvmStatic
        fun provideNullableWildcardNonMatches(): List<Arguments> = listOf(
            Arguments.of(
                "kotlin.collections.MutableList.add(?)",
                "kotlin.collections.MutableList.add(kotlin.Int?, kotlin.String?)"
            ),
            Arguments.of(
                "kotlin.collections.MutableList.add(kotlin.Int, ?)",
                "kotlin.collections.MutableList.add(kotlin.String?)"
            )
        )
    }
}
