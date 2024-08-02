/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.obfuscator

import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.annotation.StringForgeryType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

internal abstract class AbstractObfuscatorTest {

    lateinit var testedObfuscator: StringObfuscator

    @StringForgery(StringForgeryType.ALPHA_NUMERICAL)
    lateinit var fakeAlphaNumChunks: List<String>

    @IntForgery(1, 10)
    var fakeSeparatorLength: Int = 1

    @IntForgery(1, 10)
    var fakePrefixLength: Int = 1

    @IntForgery(1, 10)
    var fakePostfixLength: Int = 1

    @IntForgery(1, 10)
    var fakeEmojiRepeat: Int = 1

    @ParameterizedTest(name = "{index} (char:{0})")
    @MethodSource("whitespacesUseCases")
    fun `M mask non whitespace chars W obfuscate`(
        whitespaceSeparator: Char
    ) {
        // Given
        val input = fakeAlphaNumChunks
            .joinToString(
                separator = CharArray(fakeSeparatorLength) { whitespaceSeparator }.concatToString(),
                prefix = CharArray(fakePrefixLength) { whitespaceSeparator }.concatToString(),
                postfix = CharArray(fakePostfixLength) { whitespaceSeparator }.concatToString()
            )
        val expectedOutput = fakeAlphaNumChunks
            .joinToString(
                separator = CharArray(fakeSeparatorLength) { whitespaceSeparator }.concatToString(),
                prefix = CharArray(fakePrefixLength) { whitespaceSeparator }.concatToString(),
                postfix = CharArray(fakePostfixLength) { whitespaceSeparator }.concatToString()
            ) { CharArray(it.length) { 'x' }.concatToString() }

        // When
        val output = testedObfuscator.obfuscate(input)

        // Then
        assertThat(output).isEqualTo(expectedOutput)
    }

    companion object {

        @JvmStatic
        fun emojiUseCases(): Stream<Arguments> {
            val emojiChars = mutableListOf<String>()

            // First set of emojis
            for (emojiCodePoint in 0x1F600 until 0x1F64F) {
                emojiChars.add(
                    Character.toChars(emojiCodePoint).concatToString()
                )
            }

            for (emojiCodePoint in 0x1F680 until 0x1F6FC) {
                emojiChars.add(
                    Character.toChars(emojiCodePoint).concatToString()
                )
            }

            for (emojiCodePoint in 0x1F90C until 0x1F9FF) {
                emojiChars.add(
                    Character.toChars(emojiCodePoint).concatToString()
                )
            }

            for (emojiCodePoint in 0x1FA70 until 0x1FAD6) {
                emojiChars.add(
                    Character.toChars(emojiCodePoint).concatToString()
                )
            }

            return emojiChars.map { Arguments.of(it) }.stream()
        }

        @JvmStatic
        fun whitespacesUseCases(): Stream<Arguments> {
            val whitespaceChars = mutableListOf<Char>()

            // ASCII whitespace character
            whitespaceChars.add('\u0009') // Horizontal Tab = '\t'
            whitespaceChars.add('\u000A') // Line Feed = '\n'
            whitespaceChars.add('\u000B') // Vertical Tab
            whitespaceChars.add('\u000C') // Form Feed
            whitespaceChars.add('\u000D') // Carriage Return = '\r'
            whitespaceChars.add('\u001C') // File Separator
            whitespaceChars.add('\u001D') // Group Separator
            whitespaceChars.add('\u001E') // Record Separator
            whitespaceChars.add('\u001F') // Unit Separator
            whitespaceChars.add('\u0020') // plain old whitespace ' '

            // Non ASCII
            whitespaceChars.add('\u1680') // Ogham (Old Irish alphabet) Space Mark
            // whitespaceChars.add('\u180e') // Mongolian Vowel Separator - this works in Android, but not in the JDK

            // General Punctuation Block : spaces of varying width
            whitespaceChars.add('\u2000') // en quad = ½ em space (fixed width)
            whitespaceChars.add('\u2001') // em quad = 1 em space (fixed width)
            whitespaceChars.add('\u2002') // en space = ½ em space (width can vary depending on font)
            whitespaceChars.add('\u2003') // em space = 1 em space (width can vary depending on font)
            whitespaceChars.add('\u2004') // 3-per-em space = ⅓ em space (width can vary depending on font)
            whitespaceChars.add('\u2005') // 4-per-em space = ¼ em space (width can vary depending on font)
            whitespaceChars.add('\u2006') // 6-per-em space = ⅙ em space (width can vary depending on font)
            whitespaceChars.add('\u2008') // punctuation space (space as wide as a period '.')
            whitespaceChars.add('\u2009') // thin space (between a ⅙ and ¼ em space)
            whitespaceChars.add('\u200a') // hair space (narrower than a thin space, usually the thinnest space)
            whitespaceChars.add('\u2028') // line separator
            whitespaceChars.add('\u2029') // paragraph separator
            whitespaceChars.add('\u205f') // Medium mathematical space (four-eighteenths of an em, because why not)
            whitespaceChars.add('\u3000') // Ideographic space

            return whitespaceChars.map { Arguments.of(it) }.stream()
        }
    }
}
