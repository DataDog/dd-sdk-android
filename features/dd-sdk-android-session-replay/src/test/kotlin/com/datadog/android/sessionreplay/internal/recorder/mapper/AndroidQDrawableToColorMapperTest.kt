/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.mapper

//noinspection SuspiciousImport
import android.graphics.BlendModeColorFilter
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.android.sessionreplay.utils.DrawableToColorMapper
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
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(value = ForgeConfigurator::class)
class AndroidQDrawableToColorMapperTest : AndroidMDrawableToColorMapperTest() {

    override fun createTestedMapper(): DrawableToColorMapper {
        return TestableQMapper()
    }

    private val testableMapper get() = testedMapper as TestableQMapper

    @Test
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
        val baseAlpha = (fillPaintColor.toLong() and 0xFF000000) shr 24
        assumeTrue(baseAlpha != 0L)
        testableMapper.fakeResolvedColor = fillPaintColor
        testableMapper.fakeColorFilter = mockColorFilter
        val gradientDrawable = GradientDrawable()

        // When
        val result = testedMapper.mapDrawableToColor(gradientDrawable, mockInternalLogger)

        // Then
        assertThat(result).isEqualTo(expectedBlendColor)
    }

    @Test
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
        testableMapper.fakeResolvedColor = fillPaintColor and 0x00FFFFFF // alpha = 0
        testableMapper.fakeColorFilter = mockColorFilter
        val gradientDrawable = GradientDrawable()

        // When
        val result = testedMapper.mapDrawableToColor(gradientDrawable, mockInternalLogger)

        // Then
        assertThat(result).isNull()
    }

    @Test
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
        val baseAlpha = (fillPaintColor.toLong() and 0xFF000000) shr 24
        assumeTrue(baseAlpha != 0L)
        testableMapper.fakeResolvedColor = fillPaintColor
        testableMapper.fakeColorFilter = mockColorFilter
        val gradientDrawable = GradientDrawable()

        // When
        val result = testedMapper.mapDrawableToColor(gradientDrawable, mockInternalLogger)

        // Then
        assertThat(result).isEqualTo(fillPaintColor)
    }

    @Test
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
        testableMapper.fakeResolvedColor = fillPaintColor and 0x00FFFFFF // alpha = 0
        testableMapper.fakeColorFilter = mockColorFilter
        val gradientDrawable = GradientDrawable()

        // When
        val result = testedMapper.mapDrawableToColor(gradientDrawable, mockInternalLogger)

        // Then
        assertThat(result).isNull()
    }

    @Test
    fun `M return the resolved color W mapDrawableToColor {no accessible color filter}`(
        @IntForgery fillColor: Int
    ) {
        // When drawable.colorFilter is null (no tint was applied at all — the common case for a
        // plain untinted <shape> drawable), the mapper returns the drawable's own resolved fill
        // color as-is rather than discarding it: colorFilter only ever refines a color, it
        // shouldn't gate whether one is returned in the first place.
        val baseAlpha = (fillColor.toLong() and 0xFF000000) shr 24
        assumeTrue(baseAlpha != 0L)
        testableMapper.fakeResolvedColor = fillColor
        testableMapper.fakeColorFilter = null
        val gradientDrawable = GradientDrawable() // alpha defaults to fully opaque

        val result = testedMapper.mapDrawableToColor(gradientDrawable, mockInternalLogger)

        assertThat(result).isEqualTo(fillColor)
    }

    @Test
    override fun `M map GradientDrawable to fill paint's color W mapDrawableToColor()`(
        @IntForgery drawableColor: Int
    ) {
        // On Q+, when no color filter is accessible (drawable.colorFilter is null), the mapper
        // still returns the resolved fill color as-is — colorFilter is only used to refine it
        // further when one is present.
        val baseAlpha = (drawableColor.toLong() and 0xFF000000) shr 24
        assumeTrue(baseAlpha != 0L)
        val mockFillPaint = mock<Paint>().apply {
            whenever(this.color) doReturn (drawableColor and 0xFFFFFF)
            whenever(this.alpha) doReturn baseAlpha.toInt()
        }
        val gradientDrawable = GradientDrawable().apply {
            AndroidMDrawableToColorMapper.fillPaintField?.set(this, mockFillPaint)
        }

        val result = testedMapper.mapDrawableToColor(gradientDrawable, mockInternalLogger)

        assertThat(result).isEqualTo(drawableColor)
    }

    // Seam to avoid calling API 24+ methods on the test JVM; falls back to parent-injected fillPaintField when fakeResolvedColor is null.
    private class TestableQMapper : AndroidQDrawableToColorMapper() {
        var fakeResolvedColor: Int? = null
        var fakeColorFilter: ColorFilter? = null

        override fun resolveGradientFillColor(drawable: GradientDrawable): Int? {
            val fillPaint = fillPaintField?.get(drawable) as? Paint
            return fakeResolvedColor ?: fillPaint?.let { (it.alpha shl 24) or (it.color and 0xFFFFFF) }
        }

        override fun resolveGradientColorFilter(drawable: GradientDrawable): ColorFilter? = fakeColorFilter
    }
}
