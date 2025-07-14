/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.attribute

import com.datadog.android.rum.internal.domain.accessibility.Accessibility
import com.datadog.android.rum.utils.forge.Configurator
import fr.xgouchet.elmyr.annotation.BoolForgery
import fr.xgouchet.elmyr.annotation.FloatForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(ForgeExtension::class)
@ForgeConfiguration(Configurator::class)
internal class AccessibilityTest {

    @Test
    fun `M return empty map W toMap() { all values are null }`() {
        // Given
        val accessibility = Accessibility()

        // When
        val result = accessibility.toMap()

        // Then
        assertThat(result).isEmpty()
    }

    @Test
    fun `M return complete map W toMap() { all values are provided }`(
        @FloatForgery(min = 0.5f, max = 3.0f) textSize: Float,
        @BoolForgery isScreenReaderEnabled: Boolean,
        @BoolForgery isColorInversionEnabled: Boolean,
        @BoolForgery isClosedCaptioningEnabled: Boolean,
        @BoolForgery isReducedAnimationsEnabled: Boolean,
        @BoolForgery isScreenPinningEnabled: Boolean
    ) {
        // Given
        val accessibility = Accessibility(
            textSize = textSize,
            isScreenReaderEnabled = isScreenReaderEnabled,
            isColorInversionEnabled = isColorInversionEnabled,
            isClosedCaptioningEnabled = isClosedCaptioningEnabled,
            isReducedAnimationsEnabled = isReducedAnimationsEnabled,
            isScreenPinningEnabled = isScreenPinningEnabled
        )

        // When
        val result = accessibility.toMap()

        // Then
        assertThat(result).containsExactlyInAnyOrderEntriesOf(
            mapOf(
                Accessibility.TEXT_SIZE_KEY to textSize,
                Accessibility.SCREEN_READER_ENABLED_KEY to isScreenReaderEnabled,
                Accessibility.COLOR_INVERSION_ENABLED_KEY to isColorInversionEnabled,
                Accessibility.CLOSED_CAPTIONING_ENABLED_KEY to isClosedCaptioningEnabled,
                Accessibility.REDUCED_ANIMATIONS_ENABLED_KEY to isReducedAnimationsEnabled,
                Accessibility.SCREEN_PINNING_ENABLED_KEY to isScreenPinningEnabled
            )
        )
    }

    @Test
    fun `M return expected EMPTY_STATE W EMPTY_STATE constant`() {
        // When
        val result = Accessibility.EMPTY_STATE.toMap()

        // Then
        assertThat(result).isEmpty()
        assertThat(Accessibility.EMPTY_STATE.textSize).isNull()
        assertThat(Accessibility.EMPTY_STATE.isScreenReaderEnabled).isNull()
        assertThat(Accessibility.EMPTY_STATE.isColorInversionEnabled).isNull()
        assertThat(Accessibility.EMPTY_STATE.isClosedCaptioningEnabled).isNull()
        assertThat(Accessibility.EMPTY_STATE.isReducedAnimationsEnabled).isNull()
        assertThat(Accessibility.EMPTY_STATE.isScreenPinningEnabled).isNull()
    }

    @Test
    fun `M have correct constant values W key constants`() {
        // Then
        assertThat(Accessibility.TEXT_SIZE_KEY).isEqualTo("text_size")
        assertThat(Accessibility.SCREEN_READER_ENABLED_KEY).isEqualTo("screen_reader_enabled")
        assertThat(Accessibility.COLOR_INVERSION_ENABLED_KEY).isEqualTo("invert_colors_enabled")
        assertThat(Accessibility.CLOSED_CAPTIONING_ENABLED_KEY).isEqualTo("closed_captioning_enabled")
        assertThat(Accessibility.REDUCED_ANIMATIONS_ENABLED_KEY).isEqualTo("reduced_animations_enabled")
        assertThat(Accessibility.SCREEN_PINNING_ENABLED_KEY).isEqualTo("single_app_mode_enabled")
    }

    @Test
    fun `M exclude null values from map W toMap() { mixed null and non-null values }`() {
        // Given
        val accessibility = Accessibility(
            textSize = 1.5f,
            isScreenReaderEnabled = null,
            isColorInversionEnabled = true,
            isClosedCaptioningEnabled = false,
            isReducedAnimationsEnabled = null,
            isScreenPinningEnabled = true
        )

        // When
        val result = accessibility.toMap()

        // Then
        assertThat(result).containsExactlyInAnyOrderEntriesOf(
            mapOf(
                Accessibility.TEXT_SIZE_KEY to 1.5f,
                Accessibility.COLOR_INVERSION_ENABLED_KEY to true,
                Accessibility.CLOSED_CAPTIONING_ENABLED_KEY to false,
                Accessibility.SCREEN_PINNING_ENABLED_KEY to true
            )
        )
        assertThat(result).doesNotContainKeys(
            Accessibility.SCREEN_READER_ENABLED_KEY,
            Accessibility.REDUCED_ANIMATIONS_ENABLED_KEY
        )
    }
}
