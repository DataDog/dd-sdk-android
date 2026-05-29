/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.tools.detekt.rules.sdk.rule.thirdparty

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

internal class CodeParserTest {

    private val testedParser = CodeParser()

    @Test
    fun `M return checked exception W parseDeclaredCheckedExceptions() {method declares checked}`() {
        val result = testedParser.parseDeclaredCheckedExceptions(
            className = "java.io.BufferedWriter",
            memberName = "write",
            parameterCount = 1
        )

        assertThat(result).contains("java.io.IOException")
    }

    @Test
    fun `M return checked exception W parseDeclaredCheckedExceptions() {no-arg method declares checked}`() {
        val result = testedParser.parseDeclaredCheckedExceptions(
            className = "java.io.InputStream",
            memberName = "read",
            parameterCount = 0
        )

        assertThat(result).contains("java.io.IOException")
    }

    @Test
    fun `M return checked exception W parseDeclaredCheckedExceptions() {constructor declares checked}`() {
        val result = testedParser.parseDeclaredCheckedExceptions(
            className = "java.io.RandomAccessFile",
            memberName = "constructor",
            parameterCount = 2
        )

        assertThat(result).contains("java.io.FileNotFoundException")
    }

    @Test
    fun `M return empty W parseDeclaredCheckedExceptions() {only unchecked exceptions}`() {
        val result = testedParser.parseDeclaredCheckedExceptions(
            className = "java.lang.StringBuilder",
            memberName = "append",
            parameterCount = 1
        )

        assertThat(result).isEmpty()
    }

    @Test
    fun `M return empty W parseDeclaredCheckedExceptions() {overload set has a non-throwing member}`() {
        val result = testedParser.parseDeclaredCheckedExceptions(
            className = "java.io.FileInputStream",
            memberName = "constructor",
            parameterCount = 1
        )

        assertThat(result).isEmpty()
    }

    @Test
    fun `M return empty W parseDeclaredCheckedExceptions() {class not on classpath}`() {
        val result = testedParser.parseDeclaredCheckedExceptions(
            className = "com.example.unknown.Mystery",
            memberName = "doStuff",
            parameterCount = 1
        )

        assertThat(result).isEmpty()
    }

    @Test
    fun `M return empty W parseDeclaredCheckedExceptions() {no member matches arity}`() {
        val result = testedParser.parseDeclaredCheckedExceptions(
            className = "java.io.InputStream",
            memberName = "read",
            parameterCount = 9
        )

        assertThat(result).isEmpty()
    }

    @Test
    fun `M return empty W parseDeclaredCheckedExceptions() {blank class or member}`() {
        assertThat(testedParser.parseDeclaredCheckedExceptions("", "read", 0)).isEmpty()
        assertThat(testedParser.parseDeclaredCheckedExceptions("java.io.InputStream", "", 0)).isEmpty()
    }

    @Test
    fun `M throw exception W parseDeclaredCheckedExceptions() {class name nested too deeply}`() {
        val tooDeep = (0..16).joinToString(".") { "a" }

        assertThrows<IllegalStateException> {
            testedParser.parseDeclaredCheckedExceptions(
                className = tooDeep,
                memberName = "foo",
                parameterCount = 0
            )
        }
    }
}
