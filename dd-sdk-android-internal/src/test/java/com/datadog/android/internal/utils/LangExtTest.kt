/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.internal.utils

import com.datadog.android.internal.forge.Configurator
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class LangExtTest {

    @Test
    fun `M execute block and return null W runIfNull() {value is null}`() {
        // Given
        val nullValue: String? = null
        var blockExecuted = false

        // When
        val result = nullValue.runIfNull { blockExecuted = true }

        // Then
        assertThat(result).isNull()
        assertThat(blockExecuted).isTrue()
    }

    @Test
    fun `M not execute block and return value W runIfNull() {value is not null}`(
        @StringForgery fakeValue: String
    ) {
        // Given
        var blockExecuted = false

        // When
        val result = fakeValue.runIfNull { blockExecuted = true }

        // Then
        assertThat(result).isEqualTo(fakeValue)
        assertThat(blockExecuted).isFalse()
    }

    @Test
    fun `M return same instance W runIfNull() {value is not null}`(
        @StringForgery fakeValue: String
    ) {
        // Given
        val originalValue: String? = fakeValue

        // When
        val result = originalValue.runIfNull { }

        // Then
        assertThat(result).isSameAs(originalValue)
    }

    @Test
    fun `M allow chaining W runIfNull()`(
        @StringForgery fakeValue: String
    ) {
        // Given
        var block1Executed = false
        var block2Executed = false

        // When
        val result = fakeValue
            .runIfNull { block1Executed = true }
            ?.runIfNull { block2Executed = true }

        // Then
        assertThat(result).isEqualTo(fakeValue)
        assertThat(block1Executed).isFalse()
        assertThat(block2Executed).isFalse()
    }

    @Test
    fun `M work with nullable chain W runIfNull()`() {
        // Given
        val nullValue: String? = null
        var blockExecuted = false

        // When
        val result = nullValue
            .runIfNull { blockExecuted = true }
            ?.uppercase()

        // Then
        assertThat(result).isNull()
        assertThat(blockExecuted).isTrue()
    }
}
