/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.utils

import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.android.sessionreplay.model.MobileSegment
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions

@Extensions(ExtendWith(ForgeExtension::class))
@ForgeConfiguration(ForgeConfigurator::class)
internal class ShapeStyleExtTest {

    // region hasNonTranslucentColor

    @Test
    fun `M return true W hasNonTranslucentColor { backgroundColor not translucent }`(forge: Forge) {
        // Given
        val fakeNonTranslucentColor = forge.aStringMatching("#[0-9A-Fa-f]{6}[fF]{2}")
        val fakeShapeStyle: MobileSegment.ShapeStyle = forge.getForgery<MobileSegment.ShapeStyle>()
            .copy(backgroundColor = fakeNonTranslucentColor)

        // When
        assertThat(fakeShapeStyle.hasNonTranslucentColor()).isTrue
    }

    @Test
    fun `M return false W hasNonTranslucentColor { backgroundColor is translucent }`(forge: Forge) {
        // Given
        val fakeTranslucentColor = forge.aStringMatching("#[0-9A-Fa-f]{6}[0-9A-Ea-e]{2}")
        val fakeShapeStyle: MobileSegment.ShapeStyle = forge.getForgery<MobileSegment.ShapeStyle>()
            .copy(backgroundColor = fakeTranslucentColor)

        // When
        assertThat(fakeShapeStyle.hasNonTranslucentColor()).isFalse
    }

    @Test
    fun `M return false W hasNonTranslucentColor { backgroundColor is null }`(forge: Forge) {
        // Given
        val fakeShapeStyle: MobileSegment.ShapeStyle = forge.getForgery<MobileSegment.ShapeStyle>()
            .copy(backgroundColor = null)

        // When
        assertThat(fakeShapeStyle.hasNonTranslucentColor()).isFalse
    }

    // region isFullyOpaque

    @Test
    fun `M return true W isFullyOpaque { opacity is null }`(forge: Forge) {
        // Given
        val fakeShapeStyle = forge.getForgery<MobileSegment.ShapeStyle>().copy(opacity = null)

        // Then
        assertThat(fakeShapeStyle.isFullyOpaque()).isTrue
    }

    @Test
    fun `M return true W isFullyOpaque { opacity is grater than 1 }`(forge: Forge) {
        // Given
        val fakeOpacity = forge.aFloat(min = 1f)
        val fakeShapeStyle = forge.getForgery<MobileSegment.ShapeStyle>()
            .copy(opacity = fakeOpacity)

        // Then
        assertThat(fakeShapeStyle.isFullyOpaque()).isTrue
    }

    @Test
    fun `M return false W isFullyOpaque { opacity is smaller than 1 }`(forge: Forge) {
        // Given
        val fakeOpacity = forge.aFloat(max = 1f)
        val fakeShapeStyle = forge.getForgery<MobileSegment.ShapeStyle>()
            .copy(opacity = fakeOpacity)

        // Then
        assertThat(fakeShapeStyle.isFullyOpaque()).isFalse
    }

    // endregion
}
