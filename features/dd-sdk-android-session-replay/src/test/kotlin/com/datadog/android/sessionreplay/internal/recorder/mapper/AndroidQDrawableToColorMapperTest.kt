/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.mapper

//noinspection SuspiciousImport
import android.graphics.BlendModeColorFilter
import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import android.os.Build
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.android.sessionreplay.internal.recorder.mapper.AndroidMDrawableToColorMapper.Companion.fillPaintField
import com.datadog.android.sessionreplay.utils.DrawableToColorMapper
import com.datadog.tools.unit.annotations.TestTargetApi
import com.datadog.tools.unit.extensions.ApiLevelExtension
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class),
    ExtendWith(ApiLevelExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(value = ForgeConfigurator::class)
class AndroidQDrawableToColorMapperTest : AndroidMDrawableToColorMapperTest() {

    override fun createTestedMapper(): DrawableToColorMapper {
        return AndroidQDrawableToColorMapper()
    }

    @Test
    @TestTargetApi(Build.VERSION_CODES.Q)
    fun `M map GradientDrawable to fill paint's color blend color W mapDrawableToColor()`(
        @IntForgery fillPaintColor: Int,
        @IntForgery blendFilterColor: Int,
        forge: Forge
    ) {
        // Given
        val blendColor = blendFilterColor and 0xFFFFFF
        val expectedBlendColor = ((fillPaintColor.toLong() and 0xFF000000) or blendColor.toLong()).toInt()
        val blendMode = forge.anElementFrom(AndroidQDrawableToColorMapper.blendModesReturningBlendColor)
        val mockColorFilter = mock<BlendModeColorFilter>().apply {
            whenever(this.color) doReturn blendColor
            whenever(this.mode) doReturn blendMode
        }
        val baseColor = fillPaintColor and 0xFFFFFF
        val baseAlpha = (fillPaintColor.toLong() and 0xFF000000) shr 24
        assumeTrue(baseAlpha != 0L)
        val mockFillPaint = mock<Paint>().apply {
            whenever(this.color) doReturn baseColor
            whenever(this.alpha) doReturn baseAlpha.toInt()
            whenever(this.colorFilter) doReturn mockColorFilter
        }
        val gradientDrawable = GradientDrawable().apply {
            fillPaintField?.set(this, mockFillPaint)
        }

        // When
        val result = testedMapper.mapDrawableToColor(gradientDrawable, mockInternalLogger)

        // Then
        assertThat(result).isEqualTo(expectedBlendColor)
    }

    @Test
    @TestTargetApi(Build.VERSION_CODES.Q)
    fun `M map GradientDrawable to fill paint's color blend color W mapDrawableToColor() {fully transparent}`(
        @IntForgery fillPaintColor: Int,
        @IntForgery blendFilterColor: Int,
        forge: Forge
    ) {
        // Given
        val blendColor = blendFilterColor and 0xFFFFFF
        val blendMode = forge.anElementFrom(AndroidQDrawableToColorMapper.blendModesReturningBlendColor)
        val mockColorFilter = mock<BlendModeColorFilter>().apply {
            whenever(this.color) doReturn blendColor
            whenever(this.mode) doReturn blendMode
        }
        val baseColor = fillPaintColor and 0xFFFFFF
        val baseAlpha = 0L
        val mockFillPaint = mock<Paint>().apply {
            whenever(this.color) doReturn baseColor
            whenever(this.alpha) doReturn baseAlpha.toInt()
            whenever(this.colorFilter) doReturn mockColorFilter
        }
        val gradientDrawable = GradientDrawable().apply {
            fillPaintField?.set(this, mockFillPaint)
        }

        // When
        val result = testedMapper.mapDrawableToColor(gradientDrawable, mockInternalLogger)

        // Then
        assertThat(result).isNull()
    }

    @Test
    @TestTargetApi(Build.VERSION_CODES.Q)
    fun `M map GradientDrawable to fill paint's color W mapDrawableToColor()`(
        @IntForgery fillPaintColor: Int,
        @IntForgery blendFilterColor: Int,
        forge: Forge
    ) {
        // Given
        val blendColor = blendFilterColor and 0xFFFFFF
        val blendMode = forge.anElementFrom(AndroidQDrawableToColorMapper.blendModesReturningOriginalColor)
        val mockColorFilter = mock<BlendModeColorFilter>().apply {
            whenever(this.color) doReturn blendColor
            whenever(this.mode) doReturn blendMode
        }
        val baseColor = fillPaintColor and 0xFFFFFF
        val baseAlpha = (fillPaintColor.toLong() and 0xFF000000) shr 24
        assumeTrue(baseAlpha != 0L)
        val mockFillPaint = mock<Paint>().apply {
            whenever(this.color) doReturn baseColor
            whenever(this.alpha) doReturn baseAlpha.toInt()
            whenever(this.colorFilter) doReturn mockColorFilter
        }
        val gradientDrawable = GradientDrawable().apply {
            fillPaintField?.set(this, mockFillPaint)
        }

        // When
        val result = testedMapper.mapDrawableToColor(gradientDrawable, mockInternalLogger)

        // Then
        assertThat(result).isEqualTo(fillPaintColor)
    }

    @Test
    @TestTargetApi(Build.VERSION_CODES.Q)
    fun `M map GradientDrawable to fill paint's color W mapDrawableToColor() {fully transparent}`(
        @IntForgery fillPaintColor: Int,
        @IntForgery blendFilterColor: Int,
        forge: Forge
    ) {
        // Given
        val blendColor = blendFilterColor and 0xFFFFFF
        val blendMode = forge.anElementFrom(AndroidQDrawableToColorMapper.blendModesReturningOriginalColor)
        val mockColorFilter = mock<BlendModeColorFilter>().apply {
            whenever(this.color) doReturn blendColor
            whenever(this.mode) doReturn blendMode
        }
        val baseColor = fillPaintColor and 0xFFFFFF
        val baseAlpha = 0L
        val mockFillPaint = mock<Paint>().apply {
            whenever(this.color) doReturn baseColor
            whenever(this.alpha) doReturn baseAlpha.toInt()
            whenever(this.colorFilter) doReturn mockColorFilter
        }
        val gradientDrawable = GradientDrawable().apply {
            fillPaintField?.set(this, mockFillPaint)
        }

        // When
        val result = testedMapper.mapDrawableToColor(gradientDrawable, mockInternalLogger)

        // Then
        assertThat(result).isNull()
    }
}
