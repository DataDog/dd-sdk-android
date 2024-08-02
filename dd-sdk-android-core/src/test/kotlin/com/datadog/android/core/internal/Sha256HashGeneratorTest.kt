/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal

import com.datadog.android.utils.forge.Configurator
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class Sha256HashGeneratorTest {

    private val testedGenerator = Sha256HashGenerator()

    @Test
    fun `M generate hash of expected format W generate()`(
        @StringForgery fakeInput: String
    ) {
        // When
        val hash = testedGenerator.generate(fakeInput)

        // Then
        assertThat(hash).isNotNull()
        assertThat(hash).matches("[0-9a-z]+")
    }

    @Test
    fun `M generate same hash W generate() { same input }`(
        @StringForgery fakeInput: String
    ) {
        // When
        val hash1 = testedGenerator.generate(fakeInput)
        val hash2 = testedGenerator.generate(fakeInput)

        // Then
        assertThat(hash1).isEqualTo(hash2)
    }

    @Test
    fun `M generate different hash W generate() { different input }`(
        @StringForgery fakeInput1: String,
        @StringForgery fakeInput2: String
    ) {
        // When
        val hash1 = testedGenerator.generate(fakeInput1)
        val hash2 = testedGenerator.generate(fakeInput2)

        // Then
        assertThat(hash1).isNotEqualTo(hash2)
    }
}
