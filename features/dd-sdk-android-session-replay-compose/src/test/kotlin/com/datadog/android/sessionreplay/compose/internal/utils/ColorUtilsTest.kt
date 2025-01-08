/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.compose.internal.utils

import fr.xgouchet.elmyr.Forge
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
class ColorUtilsTest {

    private val testedColorUtils = ColorUtils()

    private val lightColors = listOf(
        PURE_WHITE_HEX,
        BRIGHT_GREEN_HEX,
        BRIGHT_YELLOW_HEX,
        LIGHT_GRAY_HEX,
        VERY_LIGHT_GRAY_HEX
    )

    private val darkColors = listOf(
        PURE_BLACK_HEX,
        BRIGHT_RED_HEX,
        BRIGHT_BLUE_HEX,
        NEUTRAL_GRAY_HEX,
        DARK_INDIGO_HEX
    )

    @Test
    fun `M return true W isDarkColor { color is dark }`(forge: Forge) {
        // Given
        val color = forge.anElementFrom(darkColors)

        // When
        val result = testedColorUtils.isDarkColor(color)

        // Then
        assertThat(result).isTrue()
    }

    @Test
    fun `M return false W isDarkColor { color is light }`(forge: Forge) {
        // Given
        val color = forge.anElementFrom(lightColors)

        // When
        val result = testedColorUtils.isDarkColor(color)

        // Then
        assertThat(result).isFalse()
    }

    companion object {
        // light colors
        private const val PURE_WHITE_HEX = "#FFFFFFFF"
        private const val BRIGHT_GREEN_HEX = "#FF00FF00"
        private const val BRIGHT_YELLOW_HEX = "#FFFFFF00"
        private const val LIGHT_GRAY_HEX = "#FFC0C0C0"
        private const val VERY_LIGHT_GRAY_HEX = "#FFF5F5F5"

        // dark colors
        private const val PURE_BLACK_HEX = "#FF000000"
        private const val BRIGHT_RED_HEX = "#FFFF0000"
        private const val BRIGHT_BLUE_HEX = "#FF0000FF"
        private const val NEUTRAL_GRAY_HEX = "#FF808080"
        private const val DARK_INDIGO_HEX = "#FF4B0082"
    }
}
