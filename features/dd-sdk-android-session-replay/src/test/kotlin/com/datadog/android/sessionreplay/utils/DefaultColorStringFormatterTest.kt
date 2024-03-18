/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.utils

import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions

@Extensions(ExtendWith(ForgeExtension::class))
@ForgeConfiguration(ForgeConfigurator::class)
internal class DefaultColorStringFormatterTest {

    @Test
    fun `M return hex formatted color W formatColorAndAlphaAsHexString`(
        @StringForgery(regex = "#[0-9a-f]{8}") fakeHexColor: String
    ) {
        // Given
        val color = fakeHexColor.substring(1 until 7).toInt(16)
        val colorAlpha = fakeHexColor.substring(7..8).toInt(16)

        // When
        val hexString = DefaultColorStringFormatter.formatColorAndAlphaAsHexString(color, colorAlpha)

        // Then
        assertThat(hexString).isEqualTo(fakeHexColor)
    }

    @Test
    fun `M return hex formatted color with original alpha W formatColorAsHexString`(
        @StringForgery(regex = "#[0-9a-f]{8}") fakeHexColor: String
    ) {
        // Given
        val color = fakeHexColor.substring(1 until 7).toInt(16)
        val colorAlpha = fakeHexColor.substring(7..8).toInt(16)
        val colorWithAlpha = color or (colorAlpha shl 24)

        // When
        val hexString = DefaultColorStringFormatter.formatColorAsHexString(colorWithAlpha)

        // Then
        assertThat(hexString).isEqualTo(fakeHexColor)
    }
}
