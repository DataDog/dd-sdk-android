/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.mapper

import android.graphics.BlendModeColorFilter
import com.datadog.android.sessionreplay.utils.DrawableToColorMapper
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.IntForgery
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.whenever

internal class AndroidQDrawableToColorMapperTest : AndroidMDrawableToColorMapperTest() {

    @Mock
    lateinit var mockBlendModeColorFilter: BlendModeColorFilter

    @Test
    fun `M map GradientDrawable to blend color W mapDrawableToColor()`(
        @IntForgery fakeFillColor: Int,
        @IntForgery fakeBlendColor: Int,
        forge: Forge
    ) {
        // Given
        val fakeOpaqueFillColor = fakeFillColor or 0xFF000000.toInt()
        val fakeBlendRgb = fakeBlendColor and 0x00FFFFFF
        whenever(mockColorStateList.getColorForState(any(), any())) doReturn fakeOpaqueFillColor
        whenever(mockBlendModeColorFilter.color) doReturn fakeBlendRgb
        whenever(mockBlendModeColorFilter.mode)
            .doReturn(forge.anElementFrom(AndroidQDrawableToColorMapper.blendModesReturningBlendColor))
        whenever(mockGradientDrawable.colorFilter) doReturn mockBlendModeColorFilter

        // When
        val result = testedMapper.mapDrawableToColor(mockGradientDrawable, mockInternalLogger)

        // Then
        assertThat(result).isEqualTo(fakeBlendRgb or 0xFF000000.toInt())
    }

    @Test
    fun `M map GradientDrawable to original fill color W mapDrawableToColor()`(
        @IntForgery fakeFillColor: Int,
        @IntForgery fakeBlendColor: Int,
        forge: Forge
    ) {
        // Given
        val fakeOpaqueFillColor = fakeFillColor or 0xFF000000.toInt()
        whenever(mockColorStateList.getColorForState(any(), any())) doReturn fakeOpaqueFillColor
        whenever(mockBlendModeColorFilter.color) doReturn fakeBlendColor
        whenever(mockBlendModeColorFilter.mode)
            .doReturn(forge.anElementFrom(AndroidQDrawableToColorMapper.blendModesReturningOriginalColor))
        whenever(mockGradientDrawable.colorFilter) doReturn mockBlendModeColorFilter

        // When
        val result = testedMapper.mapDrawableToColor(mockGradientDrawable, mockInternalLogger)

        // Then
        assertThat(result).isEqualTo(fakeOpaqueFillColor)
    }

    @Test
    fun `M map GradientDrawable to null W mapDrawableToColor() {transparent fill with blend filter}`(
        @IntForgery fakeFillColor: Int,
        @IntForgery fakeBlendColor: Int,
        forge: Forge
    ) {
        // Given
        whenever(mockColorStateList.getColorForState(any(), any())) doReturn (fakeFillColor and 0x00FFFFFF)
        whenever(mockBlendModeColorFilter.color) doReturn fakeBlendColor
        whenever(mockBlendModeColorFilter.mode).doReturn(
            forge.anElementFrom(
                AndroidQDrawableToColorMapper.blendModesReturningBlendColor +
                    AndroidQDrawableToColorMapper.blendModesReturningOriginalColor
            )
        )
        whenever(mockGradientDrawable.colorFilter) doReturn mockBlendModeColorFilter

        // When
        val result = testedMapper.mapDrawableToColor(mockGradientDrawable, mockInternalLogger)

        // Then
        assertThat(result).isNull()
    }

    override fun createTestedMapper(): DrawableToColorMapper = AndroidQDrawableToColorMapper()
}
