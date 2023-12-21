/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.base64

import com.datadog.android.api.InternalLogger
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness

@Extensions(ExtendWith(ForgeExtension::class))
@MockitoSettings(strictness = Strictness.LENIENT)
internal class MD5HashGeneratorTest {

    @Mock
    private lateinit var mockLogger: InternalLogger

    private lateinit var testedGenerator: HashGenerator

    @BeforeEach
    fun setup() {
        testedGenerator = MD5HashGenerator(mockLogger)
    }

    @Test
    fun `𝕄 generate hash of expected format 𝕎 generate()`(
        @StringForgery fakeInput: String
    ) {
        // When
        val hash = testedGenerator.generate(fakeInput.toByteArray())

        // Then
        Assertions.assertThat(hash).isNotNull()
        Assertions.assertThat(hash).matches("[0-9a-z]+")
    }

    @Test
    fun `𝕄 generate same hash 𝕎 generate() { same input }`(
        @StringForgery fakeInput: String
    ) {
        // When
        val hash1 = testedGenerator.generate(fakeInput.toByteArray())
        val hash2 = testedGenerator.generate(fakeInput.toByteArray())

        // Then
        Assertions.assertThat(hash1).isEqualTo(hash2)
    }

    @Test
    fun `𝕄 generate different hash 𝕎 generate() { different input }`(
        @StringForgery fakeInput1: String,
        @StringForgery fakeInput2: String
    ) {
        // When
        val hash1 = testedGenerator.generate(fakeInput1.toByteArray())
        val hash2 = testedGenerator.generate(fakeInput2.toByteArray())

        // Then
        Assertions.assertThat(hash1).isNotEqualTo(hash2)
    }
}
