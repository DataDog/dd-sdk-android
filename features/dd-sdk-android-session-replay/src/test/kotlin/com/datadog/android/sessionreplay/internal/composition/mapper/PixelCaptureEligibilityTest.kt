/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition.mapper

import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ClipDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.RotateDrawable
import android.graphics.drawable.ScaleDrawable
import android.graphics.drawable.StateListDrawable
import com.datadog.android.sessionreplay.ImagePrivacy
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.android.sessionreplay.utils.GlobalBounds
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(ForgeConfigurator::class)
internal class PixelCaptureEligibilityTest {

    @Test
    fun `M keep the size W rasterSize() { within the budget }`(
        @IntForgery(min = 1, max = 1600) fakeWidth: Int,
        @IntForgery(min = 1, max = 1600) fakeHeight: Int
    ) {
        // When
        val result = PixelCaptureEligibility.rasterSize(fakeWidth, fakeHeight)

        // Then
        assertThat(result).isEqualTo(fakeWidth to fakeHeight)
    }

    @Test
    fun `M fit the budget and keep the aspect ratio W rasterSize() { over the budget }`(
        @IntForgery(min = 1700, max = 20000) fakeWidth: Int,
        @IntForgery(min = 1700, max = 20000) fakeHeight: Int
    ) {
        // When
        val (width, height) = PixelCaptureEligibility.rasterSize(fakeWidth, fakeHeight)

        // Then
        val fakeRatio = fakeWidth.toDouble() / fakeHeight
        assertThat(PixelCaptureEligibility.exceedsRasterBudget(width, height)).isFalse()
        assertThat(width.toDouble() / height).isCloseTo(fakeRatio, within(fakeRatio * 0.01))
    }

    @Test
    fun `M exceed the budget only above 10 MiB W exceedsRasterBudget()`() {
        // When / Then
        assertThat(PixelCaptureEligibility.exceedsRasterBudget(2_621_440, 1)).isFalse()
        assertThat(PixelCaptureEligibility.exceedsRasterBudget(2_621_441, 1)).isTrue()
    }

    @Test
    fun `M placeholder a large image displayed small W placeholderLabelFor() { MASK_LARGE_ONLY }`(
        @IntForgery(min = 100, max = 2000) fakeLargeSide: Int
    ) {
        // Given
        val mockDrawable: Drawable = mock()
        whenever(mockDrawable.intrinsicWidth).thenReturn(fakeLargeSide)
        whenever(mockDrawable.intrinsicHeight).thenReturn(fakeLargeSide)

        // When
        val result = PixelCaptureEligibility.placeholderLabelFor(
            ImagePrivacy.MASK_LARGE_ONLY,
            GlobalBounds(x = 0, y = 0, width = 24, height = 24),
            mockDrawable,
            1f
        )

        // Then
        assertThat(result).isEqualTo("Content Image")
    }

    @Test
    fun `M keep a small image W placeholderLabelFor() { MASK_LARGE_ONLY, small intrinsic size and bounds }`(
        @IntForgery(min = 1, max = 99) fakeSmallSide: Int
    ) {
        // Given
        val mockDrawable: Drawable = mock()
        whenever(mockDrawable.intrinsicWidth).thenReturn(fakeSmallSide)
        whenever(mockDrawable.intrinsicHeight).thenReturn(fakeSmallSide)

        // When
        val result = PixelCaptureEligibility.placeholderLabelFor(
            ImagePrivacy.MASK_LARGE_ONLY,
            GlobalBounds(x = 0, y = 0, width = 24, height = 24),
            mockDrawable,
            1f
        )

        // Then
        assertThat(result).isNull()
    }

    @Test
    fun `M keep a large gradient displayed small W placeholderLabelFor() { MASK_LARGE_ONLY }`(
        @IntForgery(min = 100, max = 2000) fakeLargeSide: Int
    ) {
        // Given
        val mockGradientDrawable: GradientDrawable = mock()
        whenever(mockGradientDrawable.intrinsicWidth).thenReturn(fakeLargeSide)
        whenever(mockGradientDrawable.intrinsicHeight).thenReturn(fakeLargeSide)

        // When
        val result = PixelCaptureEligibility.placeholderLabelFor(
            ImagePrivacy.MASK_LARGE_ONLY,
            GlobalBounds(x = 0, y = 0, width = 24, height = 24),
            mockGradientDrawable,
            1f
        )

        // Then
        assertThat(result).isNull()
    }

    @Test
    fun `M keep a large image displayed small W placeholderLabelFor() { MASK_NONE }`(
        @IntForgery(min = 100, max = 2000) fakeLargeSide: Int
    ) {
        // Given
        val mockDrawable: Drawable = mock()
        whenever(mockDrawable.intrinsicWidth).thenReturn(fakeLargeSide)
        whenever(mockDrawable.intrinsicHeight).thenReturn(fakeLargeSide)

        // When
        val result = PixelCaptureEligibility.placeholderLabelFor(
            ImagePrivacy.MASK_NONE,
            GlobalBounds(x = 0, y = 0, width = 24, height = 24),
            mockDrawable,
            1f
        )

        // Then
        assertThat(result).isNull()
    }

    @Test
    fun `M mask any background W isBackgroundMasked() { MASK_ALL }`() {
        // Given
        val mockDrawable: Drawable = mock()

        // When
        val result = PixelCaptureEligibility.isBackgroundMasked(ImagePrivacy.MASK_ALL, mockDrawable, 1f)

        // Then
        assertThat(result).isTrue()
    }

    @Test
    fun `M mask a large image background W isBackgroundMasked() { MASK_LARGE_ONLY }`(
        @IntForgery(min = 100, max = 2000) fakeLargeSide: Int,
        forge: Forge
    ) {
        // Given
        val mockDrawable: Drawable = forge.anElementFrom(
            mock<BitmapDrawable>(),
            mock<StateListDrawable>(),
            mock<InsetDrawable>(),
            mock<LayerDrawable>()
        )
        whenever(mockDrawable.intrinsicWidth).thenReturn(fakeLargeSide)
        whenever(mockDrawable.intrinsicHeight).thenReturn(10)

        // When
        val result = PixelCaptureEligibility.isBackgroundMasked(ImagePrivacy.MASK_LARGE_ONLY, mockDrawable, 1f)

        // Then
        assertThat(result).isTrue()
    }

    @Test
    fun `M mask a large image held by a smaller wrapper W isBackgroundMasked() { MASK_LARGE_ONLY }`(
        @IntForgery(min = 100, max = 2000) fakeLargeSide: Int,
        @IntForgery(min = 1, max = 99) fakeSmallSide: Int,
        forge: Forge
    ) {
        // Given
        val mockImage = drawable(fakeLargeSide)
        val mockWrapper = wrap(mockImage, forge)
        whenever(mockWrapper.intrinsicWidth).thenReturn(fakeSmallSide)
        whenever(mockWrapper.intrinsicHeight).thenReturn(fakeSmallSide)

        // When
        val result = PixelCaptureEligibility.isBackgroundMasked(ImagePrivacy.MASK_LARGE_ONLY, mockWrapper, 1f)

        // Then
        assertThat(result).isTrue()
    }

    @Test
    fun `M mask a large image held by nested wrappers W isBackgroundMasked() { MASK_LARGE_ONLY }`(
        @IntForgery(min = 100, max = 2000) fakeLargeSide: Int,
        forge: Forge
    ) {
        // Given
        val mockWrapper = wrap(wrap(drawable(fakeLargeSide), forge), forge)

        // When
        val result = PixelCaptureEligibility.isBackgroundMasked(ImagePrivacy.MASK_LARGE_ONLY, mockWrapper, 1f)

        // Then
        assertThat(result).isTrue()
    }

    @Test
    fun `M keep a wrapper holding small images W isBackgroundMasked() { MASK_LARGE_ONLY }`(
        @IntForgery(min = 1, max = 99) fakeSmallSide: Int,
        forge: Forge
    ) {
        // Given
        val mockWrapper = wrap(drawable(fakeSmallSide), forge)

        // When
        val result = PixelCaptureEligibility.isBackgroundMasked(ImagePrivacy.MASK_LARGE_ONLY, mockWrapper, 1f)

        // Then
        assertThat(result).isFalse()
    }

    @Test
    fun `M keep a wrapper holding a large gradient W isBackgroundMasked() { MASK_LARGE_ONLY }`(
        @IntForgery(min = 100, max = 2000) fakeLargeSide: Int,
        forge: Forge
    ) {
        // Given
        val mockGradient: GradientDrawable = mock()
        whenever(mockGradient.intrinsicWidth).thenReturn(fakeLargeSide)
        whenever(mockGradient.intrinsicHeight).thenReturn(fakeLargeSide)
        val mockWrapper = wrap(mockGradient, forge)

        // When
        val result = PixelCaptureEligibility.isBackgroundMasked(ImagePrivacy.MASK_LARGE_ONLY, mockWrapper, 1f)

        // Then
        assertThat(result).isFalse()
    }

    @Test
    fun `M mask an implausibly deep wrapper W isBackgroundMasked() { MASK_LARGE_ONLY }`(
        @IntForgery(min = 1, max = 99) fakeSmallSide: Int
    ) {
        // Given
        var mockWrapper: Drawable = drawable(fakeSmallSide)
        repeat(DEEP_WRAPPER_DEPTH) {
            val mockOuter: InsetDrawable = mock()
            whenever(mockOuter.drawable).thenReturn(mockWrapper)
            mockWrapper = mockOuter
        }

        // When
        val result = PixelCaptureEligibility.isBackgroundMasked(ImagePrivacy.MASK_LARGE_ONLY, mockWrapper, 1f)

        // Then
        assertThat(result).isTrue()
    }

    @Test
    fun `M placeholder a large image held by a wrapper displayed small W placeholderLabelFor() { MASK_LARGE_ONLY }`(
        @IntForgery(min = 100, max = 2000) fakeLargeSide: Int,
        forge: Forge
    ) {
        // Given
        val mockWrapper = wrap(drawable(fakeLargeSide), forge)

        // When
        val result = PixelCaptureEligibility.placeholderLabelFor(
            ImagePrivacy.MASK_LARGE_ONLY,
            GlobalBounds(x = 0, y = 0, width = 24, height = 24),
            mockWrapper,
            1f
        )

        // Then
        assertThat(result).isEqualTo("Content Image")
    }

    @Test
    fun `M measure a bitmap background in dp W isBackgroundMasked() { MASK_LARGE_ONLY }`() {
        // Given
        val mockBitmapDrawable: BitmapDrawable = mock()
        whenever(mockBitmapDrawable.intrinsicWidth).thenReturn(180)
        whenever(mockBitmapDrawable.intrinsicHeight).thenReturn(180)

        // When
        val result = PixelCaptureEligibility.isBackgroundMasked(ImagePrivacy.MASK_LARGE_ONLY, mockBitmapDrawable, 2f)

        // Then
        assertThat(result).isFalse()
    }

    @Test
    fun `M keep a small bitmap background W isBackgroundMasked() { MASK_LARGE_ONLY }`(
        @IntForgery(min = 1, max = 99) fakeSmallSide: Int
    ) {
        // Given
        val mockBitmapDrawable: BitmapDrawable = mock()
        whenever(mockBitmapDrawable.intrinsicWidth).thenReturn(fakeSmallSide)
        whenever(mockBitmapDrawable.intrinsicHeight).thenReturn(fakeSmallSide)

        // When
        val result = PixelCaptureEligibility.isBackgroundMasked(ImagePrivacy.MASK_LARGE_ONLY, mockBitmapDrawable, 1f)

        // Then
        assertThat(result).isFalse()
    }

    @Test
    fun `M keep a large gradient background W isBackgroundMasked() { MASK_LARGE_ONLY }`(
        @IntForgery(min = 100, max = 2000) fakeLargeSide: Int
    ) {
        // Given
        val mockGradientDrawable: GradientDrawable = mock()
        whenever(mockGradientDrawable.intrinsicWidth).thenReturn(fakeLargeSide)
        whenever(mockGradientDrawable.intrinsicHeight).thenReturn(fakeLargeSide)

        // When
        val result = PixelCaptureEligibility.isBackgroundMasked(
            ImagePrivacy.MASK_LARGE_ONLY,
            mockGradientDrawable,
            1f
        )

        // Then
        assertThat(result).isFalse()
    }

    @Test
    fun `M keep a background without an intrinsic size W isBackgroundMasked() { MASK_LARGE_ONLY }`() {
        // Given
        val mockDrawable: Drawable = mock()
        whenever(mockDrawable.intrinsicWidth).thenReturn(-1)
        whenever(mockDrawable.intrinsicHeight).thenReturn(-1)

        // When
        val result = PixelCaptureEligibility.isBackgroundMasked(ImagePrivacy.MASK_LARGE_ONLY, mockDrawable, 1f)

        // Then
        assertThat(result).isFalse()
    }

    @Test
    fun `M keep any background W isBackgroundMasked() { MASK_NONE }`(
        @IntForgery(min = 100, max = 2000) fakeLargeSide: Int
    ) {
        // Given
        val mockBitmapDrawable: BitmapDrawable = mock()
        whenever(mockBitmapDrawable.intrinsicWidth).thenReturn(fakeLargeSide)
        whenever(mockBitmapDrawable.intrinsicHeight).thenReturn(fakeLargeSide)

        // When
        val result = PixelCaptureEligibility.isBackgroundMasked(ImagePrivacy.MASK_NONE, mockBitmapDrawable, 1f)

        // Then
        assertThat(result).isFalse()
    }

    private fun drawable(sidePx: Int): BitmapDrawable {
        val mockBitmapDrawable: BitmapDrawable = mock()
        whenever(mockBitmapDrawable.intrinsicWidth).thenReturn(sidePx)
        whenever(mockBitmapDrawable.intrinsicHeight).thenReturn(sidePx)
        return mockBitmapDrawable
    }

    // A wrapper reports no size of its own unless a test says so.
    private fun wrap(inner: Drawable, forge: Forge): Drawable = when (forge.anInt(min = 0, max = WRAPPER_KINDS)) {
        0 -> mock<StateListDrawable>().also { whenever(it.current).thenReturn(inner) }
        1 -> mock<InsetDrawable>().also { whenever(it.drawable).thenReturn(inner) }
        2 -> mock<ScaleDrawable>().also { whenever(it.drawable).thenReturn(inner) }
        3 -> mock<ClipDrawable>().also { whenever(it.drawable).thenReturn(inner) }
        4 -> mock<RotateDrawable>().also { whenever(it.drawable).thenReturn(inner) }
        else -> mock<LayerDrawable>().also {
            whenever(it.numberOfLayers).thenReturn(2)
            whenever(it.getDrawable(0)).thenReturn(mock<GradientDrawable>())
            whenever(it.getDrawable(1)).thenReturn(inner)
        }
    }

    private companion object {
        const val WRAPPER_KINDS = 6
        const val DEEP_WRAPPER_DEPTH = 12
    }
}
