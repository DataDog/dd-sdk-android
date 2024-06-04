/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.obfuscator

import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.android.sessionreplay.recorder.mapper.TextViewMapper
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.annotation.StringForgeryType
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(ForgeConfigurator::class)
internal class FixedLengthStringObfuscatorTest {
    private lateinit var testedObfuscator: FixedLengthStringObfuscator

    @BeforeEach
    fun `set up`() {
        testedObfuscator = FixedLengthStringObfuscator()
    }

    @Test
    fun `M return STATIC_MASK W obfuscate(){string}`(
        @StringForgery(
            type = StringForgeryType.ASCII_EXTENDED
        ) fakeInputString: String
    ) {
        // When
        val obfuscatedString = testedObfuscator.obfuscate(fakeInputString)

        // Then
        assertThat(obfuscatedString).isEqualTo(TextViewMapper.FIXED_INPUT_MASK)
    }

    @Test
    fun `M return STATIC_MASK W obfuscate(){empty string}`() {
        // When
        val obfuscatedString = testedObfuscator.obfuscate("")

        // Then
        assertThat(obfuscatedString).isEqualTo(TextViewMapper.FIXED_INPUT_MASK)
    }
}
