/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.compose.internal.utils

import com.datadog.android.sessionreplay.compose.test.elmyr.SessionReplayComposeForgeConfigurator
import fr.xgouchet.elmyr.Case
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
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
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(SessionReplayComposeForgeConfigurator::class)
internal class ColorUtilsTest {

    private lateinit var testedUtils: ColorUtils

    @BeforeEach
    fun `set up`() {
        testedUtils = ColorUtils()
    }

    @Test
    fun `M return input value W convertRgbaToArgb() { length lt 2 }`(
        forge: Forge
    ) {
        // Given
        val input = forge.anAsciiString(1)

        // Then
        assertThat(testedUtils.convertRgbaToArgb(input)).isEqualTo(input)
    }

    @Test
    fun `M move alpha value W convertRgbaToArgb()`(
        forge: Forge
    ) {
        // Given
        val fakeColorHexString = forge.anHexadecimalString(Case.UPPER, 8)
        val expectedResult = "#" + fakeColorHexString.substring(6) + fakeColorHexString.substring(0, 6)

        // Then
        assertThat(testedUtils.convertRgbaToArgb("#$fakeColorHexString")).isEqualTo(expectedResult)
    }
}
