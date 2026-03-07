/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.mapper

import android.graphics.Paint
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.StateListDrawable
import com.datadog.android.api.InternalLogger
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.android.sessionreplay.utils.DrawableToColorMapper
import fr.xgouchet.elmyr.annotation.FloatForgery
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(value = ForgeConfigurator::class)
internal class DrawableStyleExtractorTest {

    private lateinit var testedExtractor: DrawableStyleExtractor

    @Mock
    lateinit var mockDrawableToColorMapper: DrawableToColorMapper

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @IntForgery
    var fakeFillColor: Int = 0

    @BeforeEach
    fun `set up`() {
        testedExtractor = DrawableStyleExtractor(mockDrawableToColorMapper)
    }

    // region GradientDrawable stroke extraction

    @Test
    fun `M extract stroke info W extractStyleInfo { GradientDrawable with stroke }`(
        @IntForgery fakeStrokeColor: Int,
        @FloatForgery(min = 1f, max = 20f) fakeStrokeWidth: Float
    ) {
        // Given
        assumeTrue(DrawableStyleExtractor.strokePaintField != null)
        val gradientDrawable = GradientDrawable()
        // Mock the Paint since Robolectric's Paint shadow may not properly
        // store/return color via getColor() in CI
        val strokePaint = mock<Paint>()
        whenever(strokePaint.color).thenReturn(fakeStrokeColor)
        whenever(strokePaint.strokeWidth).thenReturn(fakeStrokeWidth)
        DrawableStyleExtractor.strokePaintField!!.set(gradientDrawable, strokePaint)
        whenever(mockDrawableToColorMapper.mapDrawableToColor(eq(gradientDrawable), any()))
            .thenReturn(fakeFillColor)

        // When
        val result = testedExtractor.extractStyleInfo(gradientDrawable, mockInternalLogger)

        // Then
        assertThat(result.color).isEqualTo(fakeFillColor)
        assertThat(result.borderColor).isEqualTo(fakeStrokeColor)
        assertThat(result.borderWidth).isEqualTo(fakeStrokeWidth)
    }

    @Test
    fun `M return null border W extractStyleInfo { GradientDrawable without stroke }`() {
        // Given
        val gradientDrawable = GradientDrawable()
        whenever(mockDrawableToColorMapper.mapDrawableToColor(eq(gradientDrawable), any()))
            .thenReturn(fakeFillColor)

        // When
        val result = testedExtractor.extractStyleInfo(gradientDrawable, mockInternalLogger)

        // Then
        assertThat(result.color).isEqualTo(fakeFillColor)
        assertThat(result.borderColor).isNull()
        assertThat(result.borderWidth).isEqualTo(0f)
    }

    // endregion

    // region GradientDrawable corner radius extraction

    @Test
    fun `M extract corner radius W extractStyleInfo { GradientDrawable with corner radius }`(
        @FloatForgery(min = 1f, max = 50f) fakeRadius: Float
    ) {
        // Given
        val gradientDrawable = GradientDrawable()
        gradientDrawable.cornerRadius = fakeRadius
        whenever(mockDrawableToColorMapper.mapDrawableToColor(eq(gradientDrawable), any()))
            .thenReturn(fakeFillColor)

        // When
        val result = testedExtractor.extractStyleInfo(gradientDrawable, mockInternalLogger)

        // Then
        assertThat(result.cornerRadius).isEqualTo(fakeRadius)
    }

    @Test
    fun `M return zero corner radius W extractStyleInfo { GradientDrawable without corner radius }`() {
        // Given
        val gradientDrawable = GradientDrawable()
        whenever(mockDrawableToColorMapper.mapDrawableToColor(eq(gradientDrawable), any()))
            .thenReturn(fakeFillColor)

        // When
        val result = testedExtractor.extractStyleInfo(gradientDrawable, mockInternalLogger)

        // Then
        assertThat(result.cornerRadius).isEqualTo(0f)
    }

    // endregion

    // region Drawable unwrapping

    @Test
    fun `M extract style from inner GradientDrawable W extractStyleInfo { StateListDrawable }`(
        @FloatForgery(min = 1f, max = 50f) fakeRadius: Float
    ) {
        // Given
        val gradientDrawable = GradientDrawable()
        gradientDrawable.cornerRadius = fakeRadius
        val stateListDrawable = mock<StateListDrawable>()
        whenever(stateListDrawable.current).thenReturn(gradientDrawable)
        whenever(mockDrawableToColorMapper.mapDrawableToColor(eq(stateListDrawable), any()))
            .thenReturn(fakeFillColor)

        // When
        val result = testedExtractor.extractStyleInfo(stateListDrawable, mockInternalLogger)

        // Then
        assertThat(result.cornerRadius).isEqualTo(fakeRadius)
    }

    @Test
    fun `M extract style from inner GradientDrawable W extractStyleInfo { InsetDrawable }`(
        @FloatForgery(min = 1f, max = 50f) fakeRadius: Float
    ) {
        // Given
        val gradientDrawable = GradientDrawable()
        gradientDrawable.cornerRadius = fakeRadius
        val insetDrawable = mock<InsetDrawable>()
        whenever(insetDrawable.drawable).thenReturn(gradientDrawable)
        whenever(mockDrawableToColorMapper.mapDrawableToColor(eq(insetDrawable), any()))
            .thenReturn(fakeFillColor)

        // When
        val result = testedExtractor.extractStyleInfo(insetDrawable, mockInternalLogger)

        // Then
        assertThat(result.cornerRadius).isEqualTo(fakeRadius)
    }

    // endregion

    // region Non-GradientDrawable

    @Test
    fun `M return no border or radius W extractStyleInfo { ColorDrawable }`() {
        // Given
        val colorDrawable = ColorDrawable(fakeFillColor)
        whenever(mockDrawableToColorMapper.mapDrawableToColor(eq(colorDrawable), any()))
            .thenReturn(fakeFillColor)

        // When
        val result = testedExtractor.extractStyleInfo(colorDrawable, mockInternalLogger)

        // Then
        assertThat(result.color).isEqualTo(fakeFillColor)
        assertThat(result.cornerRadius).isEqualTo(0f)
        assertThat(result.borderColor).isNull()
        assertThat(result.borderWidth).isEqualTo(0f)
    }

    @Test
    fun `M return no border or radius W extractStyleInfo { unknown drawable }`() {
        // Given
        val unknownDrawable = mock<Drawable>()
        whenever(mockDrawableToColorMapper.mapDrawableToColor(eq(unknownDrawable), any()))
            .thenReturn(null)

        // When
        val result = testedExtractor.extractStyleInfo(unknownDrawable, mockInternalLogger)

        // Then
        assertThat(result.color).isNull()
        assertThat(result.cornerRadius).isEqualTo(0f)
        assertThat(result.borderColor).isNull()
        assertThat(result.borderWidth).isEqualTo(0f)
    }

    // endregion
}
