/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.obfuscator

import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.tools.unit.extensions.ApiLevelExtension
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
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
internal class LegacyStringObfuscatorTest : AbstractObfuscatorTest() {

    @BeforeEach
    fun `set up`() {
        testedObfuscator = LegacyStringObfuscator()
    }

    @ParameterizedTest(name = "{index} (emojis:{0})")
    @MethodSource("emojiUseCases")
    fun `M mask emoji chars W obfuscate`(
        emojiChars: String
    ) {
        // Given
        val emojiRepeat = List(fakeEmojiRepeat) { emojiChars }
        val input = emojiRepeat.joinToString(" ")
        val expectedOutput = emojiRepeat.joinToString(" ") { CharArray(it.length) { 'x' }.concatToString() }

        // When
        val output = testedObfuscator.obfuscate(input)

        // Then
        assertThat(output).isEqualTo(expectedOutput)
    }
}
