/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.obfuscator

import android.os.Build
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.tools.unit.annotations.TestTargetApi
import com.datadog.tools.unit.extensions.ApiLevelExtension
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import java.util.LinkedList
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class),
    ExtendWith(ApiLevelExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(ForgeConfigurator::class)
internal class DefaultStringObfuscatorTest {

    lateinit var testedObfuscator: DefaultStringObfuscator

    @BeforeEach
    fun `set up`() {
        testedObfuscator = DefaultStringObfuscator()
    }

    // region Android N and above

    @Test
    @TestTargetApi(Build.VERSION_CODES.N)
    fun `M mask String W obfuscate(){string with newline, Android N}`(
        forge: Forge
    ) {
        // Given
        val fakeExpectedChunk1 = forge.aString(size = forge.anInt(1, max = 10)) { '\n' }
        val fakeExpectedChunk2 = forge.aString { 'x' }
        val fakeExpectedChunk3 = forge.aString(size = forge.anInt(1, max = 10)) { '\n' }
        val fakeExpectedChunk4 = forge.aString { 'x' }
        val fakeExpectedChunk5 = forge.aString(size = forge.anInt(1, max = 10)) { '\n' }
        val fakeExpectedText = (
            fakeExpectedChunk1 +
                fakeExpectedChunk2 +
                fakeExpectedChunk3 +
                fakeExpectedChunk4 +
                fakeExpectedChunk5
            )
        val fakeText = (
            fakeExpectedChunk1 +
                forge.aString(fakeExpectedChunk2.length) { forge.anAlphaNumericalChar() } +
                fakeExpectedChunk3 +
                forge.aString(fakeExpectedChunk4.length) { forge.anAlphaNumericalChar() } +
                fakeExpectedChunk5
            )

        // When
        val obfuscatedText = testedObfuscator.obfuscate(fakeText)

        // Then
        assertThat(obfuscatedText).isEqualTo(fakeExpectedText)
    }

    @Test
    @TestTargetApi(Build.VERSION_CODES.N)
    fun `M mask String W obfuscate(){string with carriage return character, Android N}`(
        forge: Forge
    ) {
        // Given
        val fakeExpectedChunk1 = forge.aString(size = forge.anInt(1, max = 10)) { '\r' }
        val fakeExpectedChunk2 = forge.aString { 'x' }
        val fakeExpectedChunk3 = forge.aString(size = forge.anInt(1, max = 10)) { '\r' }
        val fakeExpectedChunk4 = forge.aString { 'x' }
        val fakeExpectedChunk5 = forge.aString(size = forge.anInt(1, max = 10)) { '\r' }
        val fakeExpectedText = (
            fakeExpectedChunk1 +
                fakeExpectedChunk2 +
                fakeExpectedChunk3 +
                fakeExpectedChunk4 +
                fakeExpectedChunk5
            )
        val fakeText = (
            fakeExpectedChunk1 +
                forge.aString(fakeExpectedChunk2.length) { forge.anAlphaNumericalChar() } +
                fakeExpectedChunk3 +
                forge.aString(fakeExpectedChunk4.length) { forge.anAlphaNumericalChar() } +
                fakeExpectedChunk5
            )

        // When
        val obfuscatedText = testedObfuscator.obfuscate(fakeText)

        // Then
        assertThat(obfuscatedText).isEqualTo(fakeExpectedText)
    }

    @Test
    @TestTargetApi(Build.VERSION_CODES.N)
    fun `M mask String W obfuscate(){string with whitespace character, Android N}`(
        forge: Forge
    ) {
        // Given
        val fakeExpectedChunk1 = forge.aWhitespaceString()
        val fakeExpectedChunk2 = forge.aString { 'x' }
        val fakeExpectedChunk3 = forge.aWhitespaceString()
        val fakeExpectedChunk4 = forge.aString { 'x' }
        val fakeExpectedChunk5 = forge.aWhitespaceString()
        val fakeExpectedText = (
            fakeExpectedChunk1 +
                fakeExpectedChunk2 +
                fakeExpectedChunk3 +
                fakeExpectedChunk4 +
                fakeExpectedChunk5
            )
        val fakeText = (
            fakeExpectedChunk1 +
                forge.aString(fakeExpectedChunk2.length) { forge.anAlphaNumericalChar() } +
                fakeExpectedChunk3 +
                forge.aString(fakeExpectedChunk4.length) { forge.anAlphaNumericalChar() } +
                fakeExpectedChunk5
            )

        // When
        val obfuscatedText = testedObfuscator.obfuscate(fakeText)

        // Then
        assertThat(obfuscatedText).isEqualTo(fakeExpectedText)
    }

    @Test
    @TestTargetApi(Build.VERSION_CODES.N)
    fun `M mask String W obfuscate(){string with whitespace character and emoji, Android N}`(
        forge: Forge
    ) {
        // Given
        val fakeChunk1 = forge.aStringWithEmoji()
        val fakeChunk2 = forge.aWhitespaceString()
        val fakeChunk3 = forge.aStringWithEmoji()
        val fakeChunk4 = forge.aWhitespaceString()
        val fakeText = fakeChunk1 + fakeChunk2 + fakeChunk3 + fakeChunk4

        // the real size of an emoji chunk is chunk.length/2 as one emoji contains 2 chars.
        // In our current code for >= Android N we are treating this case correctly so expected
        // obfuscated string length is chunk.length/2
        val fakeExpectedChunk1 = String(CharArray(fakeChunk1.length / 2) { 'x' })
        val fakeExpectedChunk3 = String(CharArray(fakeChunk3.length / 2) { 'x' })
        val fakeExpectedText = fakeExpectedChunk1 + fakeChunk2 + fakeExpectedChunk3 + fakeChunk4

        // When
        val obfuscatedText = testedObfuscator.obfuscate(fakeText)

        // Then
        assertThat(obfuscatedText).isEqualTo(fakeExpectedText)
    }

    // endregion

    // region below Android N

    @Test
    fun `M mask String W obfuscate(){string with newline}`(
        forge: Forge
    ) {
        // Given
        val fakeExpectedChunk1 = forge.aString(size = forge.anInt(1, max = 10)) { '\n' }
        val fakeExpectedChunk2 = forge.aString { 'x' }
        val fakeExpectedChunk3 = forge.aString(size = forge.anInt(1, max = 10)) { '\n' }
        val fakeExpectedChunk4 = forge.aString { 'x' }
        val fakeExpectedChunk5 = forge.aString(size = forge.anInt(1, max = 10)) { '\n' }
        val fakeExpectedText = (
            fakeExpectedChunk1 +
                fakeExpectedChunk2 +
                fakeExpectedChunk3 +
                fakeExpectedChunk4 +
                fakeExpectedChunk5
            )
        val fakeText = (
            fakeExpectedChunk1 +
                forge.aString(fakeExpectedChunk2.length) { forge.anAlphaNumericalChar() } +
                fakeExpectedChunk3 +
                forge.aString(fakeExpectedChunk4.length) { forge.anAlphaNumericalChar() } +
                fakeExpectedChunk5
            )

        // When
        val obfuscatedText = testedObfuscator.obfuscate(fakeText)

        // Then
        assertThat(obfuscatedText).isEqualTo(fakeExpectedText)
    }

    @Test
    fun `M mask String W obfuscate(){string with carriage return character}`(
        forge: Forge
    ) {
        // Given
        val fakeExpectedChunk1 = forge.aString(size = forge.anInt(1, max = 10)) { '\r' }
        val fakeExpectedChunk2 = forge.aString { 'x' }
        val fakeExpectedChunk3 = forge.aString(size = forge.anInt(1, max = 10)) { '\r' }
        val fakeExpectedChunk4 = forge.aString { 'x' }
        val fakeExpectedChunk5 = forge.aString(size = forge.anInt(1, max = 10)) { '\r' }
        val fakeExpectedText = (
            fakeExpectedChunk1 +
                fakeExpectedChunk2 +
                fakeExpectedChunk3 +
                fakeExpectedChunk4 +
                fakeExpectedChunk5
            )
        val fakeText = (
            fakeExpectedChunk1 +
                forge.aString(fakeExpectedChunk2.length) { forge.anAlphaNumericalChar() } +
                fakeExpectedChunk3 +
                forge.aString(fakeExpectedChunk4.length) { forge.anAlphaNumericalChar() } +
                fakeExpectedChunk5
            )

        // When
        val obfuscatedText = testedObfuscator.obfuscate(fakeText)

        // Then
        assertThat(obfuscatedText).isEqualTo(fakeExpectedText)
    }

    @Test
    fun `M mask String W obfuscate(){string with whitespace character}`(
        forge: Forge
    ) {
        // Given
        val fakeExpectedChunk1 = forge.aWhitespaceString()
        val fakeExpectedChunk2 = forge.aString { 'x' }
        val fakeExpectedChunk3 = forge.aWhitespaceString()
        val fakeExpectedChunk4 = forge.aString { 'x' }
        val fakeExpectedChunk5 = forge.aWhitespaceString()
        val fakeExpectedText = (
            fakeExpectedChunk1 +
                fakeExpectedChunk2 +
                fakeExpectedChunk3 +
                fakeExpectedChunk4 +
                fakeExpectedChunk5
            )
        val fakeText = (
            fakeExpectedChunk1 +
                forge.aString(fakeExpectedChunk2.length) { forge.anAlphaNumericalChar() } +
                fakeExpectedChunk3 +
                forge.aString(fakeExpectedChunk4.length) { forge.anAlphaNumericalChar() } +
                fakeExpectedChunk5
            )

        // When
        val obfuscatedText = testedObfuscator.obfuscate(fakeText)

        // Then
        assertThat(obfuscatedText).isEqualTo(fakeExpectedText)
    }

    @Test
    fun `M mask String W obfuscate(){string with whitespace character and emoji}`(
        forge: Forge
    ) {
        // Given
        val fakeChunk1 = forge.aStringWithEmoji()
        val fakeChunk2 = forge.aWhitespaceString()
        val fakeChunk3 = forge.aStringWithEmoji()
        val fakeChunk4 = forge.aWhitespaceString()
        val fakeText = fakeChunk1 + fakeChunk2 + fakeChunk3 + fakeChunk4

        // the real size of an emoji chunk is chunk.length/2 as one emoji contains 2 chars.
        // In our current code for < Android N we do not treat this case correctly so the
        // expected obfuscated string size will be chunk.length
        val fakeExpectedChunk1 = String(CharArray(fakeChunk1.length) { 'x' })
        val fakeExpectedChunk3 = String(CharArray(fakeChunk3.length) { 'x' })
        val fakeExpectedText = fakeExpectedChunk1 + fakeChunk2 + fakeExpectedChunk3 + fakeChunk4

        // When
        val obfuscatedText = testedObfuscator.obfuscate(fakeText)

        // Then
        assertThat(obfuscatedText).isEqualTo(fakeExpectedText)
    }

    // endregion

    // region Internal

    private fun Forge.aStringWithEmoji(): String {
        val charsList = LinkedList<Char>()
        val stringSize = anInt(min = 10, max = 100)
        repeat(stringSize) {
            val emojiCodePoint = anInt(min = 0x1f600, max = 0x1f60A)
            val chars = Character.toChars(emojiCodePoint).toList()
            charsList.addAll(chars)
        }
        return String(charsList.toCharArray())
    }

    // endregion
}
