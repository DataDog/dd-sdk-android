/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.internal.utils

import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(ForgeExtension::class)
internal class StringExtTest {

    @Test
    fun `M mask text and preserve whitespace W obfuscate()`(
        @StringForgery(regex = "[a-zA-Z0-9]{1,32}") fakeText: String
    ) {
        // Given
        val whitespace = "\t\n\u000B\u000C\r\u001C\u001D\u001E\u001F " +
            "\u1680\u2000\u2001\u2002\u2003\u2004\u2005\u2006\u2008\u2009\u200A" +
            "\u2028\u2029\u205F\u3000"
        val input = "$whitespace$fakeText$whitespace"
        val expectedOutput = "$whitespace${"x".repeat(fakeText.length)}$whitespace"

        // When
        val output = input.obfuscate()

        // Then
        assertThat(output).isEqualTo(expectedOutput)
    }

    @Test
    fun `M mask supplementary code points once each W obfuscate()`(
        @IntForgery(min = 0x1F600, max = 0x1FAE0) fakeCodePoint: Int,
        @IntForgery(min = 1, max = 10) fakeRepeat: Int
    ) {
        // Given
        val fakeEmoji = String(Character.toChars(fakeCodePoint))
        val input = List(fakeRepeat) { fakeEmoji }.joinToString(" ")
        val expectedOutput = List(fakeRepeat) { "x" }.joinToString(" ")

        // When
        val output = input.obfuscate()

        // Then
        assertThat(output).isEqualTo(expectedOutput)
    }

    @Test
    fun `M return empty string W obfuscate() {empty input}`() {
        // When
        val output = "".obfuscate()

        // Then
        assertThat(output).isEmpty()
    }

    @Test
    fun `M mask unpaired surrogates W obfuscate()`() {
        // Given
        val input = "\uD800a\uDC00"

        // When
        val output = input.obfuscate()

        // Then
        assertThat(output).isEqualTo("xxx")
    }
}
