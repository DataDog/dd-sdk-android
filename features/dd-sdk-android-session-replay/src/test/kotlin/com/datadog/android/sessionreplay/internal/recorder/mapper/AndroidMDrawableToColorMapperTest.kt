/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.mapper

//noinspection SuspiciousImport
import android.R
import android.content.res.ColorStateList
import android.graphics.ColorFilter
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.RippleDrawable
import android.graphics.drawable.StateListDrawable
import com.datadog.android.api.InternalLogger
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.android.sessionreplay.utils.DrawableToColorMapper
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
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
internal open class AndroidMDrawableToColorMapperTest {

    lateinit var testedMapper: DrawableToColorMapper

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @Mock
    lateinit var mockGradientDrawable: GradientDrawable

    @Mock
    lateinit var mockColorStateList: ColorStateList

    @Mock
    lateinit var mockColorFilter: ColorFilter

    @BeforeEach
    fun `set up`() {
        testedMapper = createTestedMapper()
        whenever(mockGradientDrawable.color) doReturn mockColorStateList
        whenever(mockGradientDrawable.state) doReturn intArrayOf(R.attr.state_enabled)
        whenever(mockGradientDrawable.alpha) doReturn 255
        whenever(mockGradientDrawable.colorFilter) doReturn mockColorFilter
    }

    @Test
    fun `M map unknown Drawable to null W mapDrawableToColor()`() {
        // Given
        val colorDrawable = mock<Drawable>()

        // When
        val result = testedMapper.mapDrawableToColor(colorDrawable, mockInternalLogger)

        // Then
        assertThat(result).isNull()
    }

    @Test
    fun `M map ColorDrawable to color with alpha W mapDrawableToColor()`(
        @IntForgery drawableColor: Int
    ) {
        // Given
        val baseColor = drawableColor and 0xFFFFFF
        val baseAlpha = (drawableColor.toLong() and 0xFF000000) shr 24
        val colorDrawable = mock<ColorDrawable>().apply {
            whenever(this.color) doReturn baseColor
            whenever(this.alpha) doReturn baseAlpha.toInt()
        }

        // When
        val result = testedMapper.mapDrawableToColor(colorDrawable, mockInternalLogger)

        // Then
        assertThat(result).isEqualTo(drawableColor)
    }

    @Test
    fun `M map RippleDrawable to first opaque layer's color W mapDrawableToColor()`(
        @IntForgery drawableColor: Int,
        @IntForgery secondDrawableColor: Int
    ) {
        // Given
        val unsupportedLayer = mock<Drawable>()
        val baseColor = drawableColor and 0xFFFFFF
        val baseAlpha = (drawableColor.toLong() and 0xFF000000) shr 24
        val opaqueLayer = mock<ColorDrawable>().apply {
            whenever(this.color) doReturn baseColor
            whenever(this.alpha) doReturn baseAlpha.toInt()
        }
        val opaqueLayer2 = mock<ColorDrawable>().apply {
            whenever(this.color) doReturn (secondDrawableColor and 0xFFFFFF)
            whenever(this.alpha) doReturn baseAlpha.toInt()
        }
        val rippleDrawable = mock<RippleDrawable>().apply {
            whenever(this.numberOfLayers) doReturn 4
            whenever(this.getDrawable(0)) doReturn unsupportedLayer
            whenever(this.getDrawable(1)) doReturn opaqueLayer
            whenever(this.getDrawable(2)) doReturn opaqueLayer2
        }

        // When
        val result = testedMapper.mapDrawableToColor(rippleDrawable, mockInternalLogger)

        // Then
        assertThat(result).isEqualTo(drawableColor)
    }

    @Test
    fun `M map LayerDrawable to first opaque layer's color W mapDrawableToColor()`(
        @IntForgery drawableColor: Int,
        @IntForgery secondDrawableColor: Int
    ) {
        // Given
        val unsupportedLayer = mock<Drawable>()
        val baseColor = drawableColor and 0xFFFFFF
        val baseAlpha = (drawableColor.toLong() and 0xFF000000) shr 24
        val opaqueLayer = mock<ColorDrawable>().apply {
            whenever(this.color) doReturn baseColor
            whenever(this.alpha) doReturn baseAlpha.toInt()
        }
        val opaqueLayer2 = mock<ColorDrawable>().apply {
            whenever(this.color) doReturn (secondDrawableColor and 0xFFFFFF)
            whenever(this.alpha) doReturn baseAlpha.toInt()
        }
        val layerDrawable = mock<LayerDrawable>().apply {
            whenever(this.numberOfLayers) doReturn 3
            whenever(this.getDrawable(0)) doReturn unsupportedLayer
            whenever(this.getDrawable(1)) doReturn opaqueLayer
            whenever(this.getDrawable(2)) doReturn opaqueLayer2
        }

        // When
        val result = testedMapper.mapDrawableToColor(layerDrawable, mockInternalLogger)

        // Then
        assertThat(result).isEqualTo(drawableColor)
    }

    @Test
    fun `M map GradientDrawable to state fill color with combined alpha W mapDrawableToColor()`(
        @IntForgery fakeColor: Int,
        @IntForgery(min = 16, max = 255) fakeColorAlpha: Int,
        @IntForgery(min = 16, max = 255) fakeDrawableAlpha: Int,
        @IntForgery fakeDefaultColor: Int
    ) {
        // Given
        val fakeFillColor = (fakeColor and 0x00FFFFFF) or (fakeColorAlpha shl 24)
        whenever(mockColorStateList.defaultColor) doReturn fakeDefaultColor
        whenever(mockColorStateList.getColorForState(mockGradientDrawable.state, fakeDefaultColor))
            .doReturn(fakeFillColor)
        whenever(mockGradientDrawable.alpha) doReturn fakeDrawableAlpha
        val expectedAlpha = fakeColorAlpha * fakeDrawableAlpha / 255
        val expectedColor = (fakeColor and 0x00FFFFFF) or (expectedAlpha shl 24)

        // When
        val result = testedMapper.mapDrawableToColor(mockGradientDrawable, mockInternalLogger)

        // Then
        assertThat(result).isEqualTo(expectedColor)
    }

    @Test
    fun `M map GradientDrawable to null W mapDrawableToColor() {transparent fill}`(
        @IntForgery fakeColor: Int
    ) {
        // Given
        whenever(mockColorStateList.getColorForState(any(), any())) doReturn (fakeColor and 0x00FFFFFF)

        // When
        val result = testedMapper.mapDrawableToColor(mockGradientDrawable, mockInternalLogger)

        // Then
        assertThat(result).isNull()
    }

    @Test
    fun `M map GradientDrawable to null W mapDrawableToColor() {transparent drawable}`(
        @IntForgery fakeColor: Int
    ) {
        // Given
        whenever(mockColorStateList.getColorForState(any(), any())) doReturn (fakeColor or 0xFF000000.toInt())
        whenever(mockGradientDrawable.alpha) doReturn 0

        // When
        val result = testedMapper.mapDrawableToColor(mockGradientDrawable, mockInternalLogger)

        // Then
        assertThat(result).isNull()
    }

    @Test
    fun `M map GradientDrawable to null W mapDrawableToColor() {combined alpha rounds to zero}`(
        @IntForgery fakeColor: Int
    ) {
        // Given
        val fakeFillColor = (fakeColor and 0x00FFFFFF) or (1 shl 24)
        whenever(mockColorStateList.getColorForState(any(), any())) doReturn fakeFillColor
        whenever(mockGradientDrawable.alpha) doReturn 1

        // When
        val result = testedMapper.mapDrawableToColor(mockGradientDrawable, mockInternalLogger)

        // Then
        assertThat(result).isNull()
    }

    @Test
    fun `M map StateListDrawable to the color of its current Drawable W mapDrawableToColor()`(
        @IntForgery drawableColor: Int
    ) {
        // Given
        val currentStateDrawable = mock<ColorDrawable>().apply {
            whenever(this.color) doReturn drawableColor
            whenever(this.alpha) doReturn (drawableColor ushr 24)
        }

        val stateListDrawable = mock<StateListDrawable>().apply {
            whenever(this.current) doReturn currentStateDrawable
        }

        // When
        val result = testedMapper.mapDrawableToColor(stateListDrawable, mockInternalLogger)

        // Then
        assertThat(result).isEqualTo(drawableColor)
    }

    @Test
    fun `M map StateListDrawable to null W mapDrawableToColor() { Drawable getCurrent returns null }`() {
        val stateListDrawable = mock<StateListDrawable>().apply {
            whenever<Drawable?>(this.current) doReturn null
        }

        // When
        val result = testedMapper.mapDrawableToColor(stateListDrawable, mockInternalLogger)

        // Then
        assertThat(result).isNull()
    }

    @Test
    fun `M map RippleDrawable to first opaque layer's color ignoring mask W mapDrawableToColor()`(
        @IntForgery drawableColor: Int,
        @IntForgery maskDrawableColor: Int
    ) {
        // Given
        val baseColor = drawableColor and 0xFFFFFF
        val baseAlpha = (drawableColor.toLong() and 0xFF000000) shr 24
        val maskLayer = mock<ColorDrawable>().apply {
            whenever(this.color) doReturn (maskDrawableColor and 0xFFFFFF)
            whenever(this.alpha) doReturn baseAlpha.toInt()
        }
        val opaqueLayer = mock<ColorDrawable>().apply {
            whenever(this.color) doReturn baseColor
            whenever(this.alpha) doReturn baseAlpha.toInt()
        }
        val rippleDrawable = mock<RippleDrawable>().apply {
            whenever(this.numberOfLayers) doReturn 2
            whenever(this.findIndexByLayerId(R.id.mask)) doReturn 0
            whenever(this.getDrawable(0)) doReturn maskLayer
            whenever(this.getDrawable(1)) doReturn opaqueLayer
        }

        // When
        val result = testedMapper.mapDrawableToColor(rippleDrawable, mockInternalLogger)

        // Then
        assertThat(result).isEqualTo(drawableColor)
    }

    @Test
    fun `M map InsetDrawable to inner drawable's color W mapDrawableToColor()`(
        @IntForgery drawableColor: Int
    ) {
        // Given
        val baseColor = drawableColor and 0xFFFFFF
        val baseAlpha = (drawableColor.toLong() and 0xFF000000) shr 24
        val innerDrawable = mock<ColorDrawable>().apply {
            whenever(this.color) doReturn baseColor
            whenever(this.alpha) doReturn baseAlpha.toInt()
        }
        val insetDrawable = mock<InsetDrawable>().apply {
            whenever(this.drawable) doReturn innerDrawable
        }

        // When
        val result = testedMapper.mapDrawableToColor(insetDrawable, mockInternalLogger)

        // Then
        assertThat(result).isEqualTo(drawableColor)
    }

    @Test
    fun `M map GradientDrawable to null W mapDrawableToColor() {no color filter}`(
        @IntForgery fakeColor: Int
    ) {
        // Given
        whenever(mockColorStateList.getColorForState(any(), any())) doReturn (fakeColor or 0xFF000000.toInt())
        whenever(mockGradientDrawable.colorFilter) doReturn null

        // When
        val result = testedMapper.mapDrawableToColor(mockGradientDrawable, mockInternalLogger)

        // Then
        assertThat(result).isNull()
    }

    @Test
    fun `M map GradientDrawable to null W mapDrawableToColor() {no fill color}`() {
        // Given
        whenever(mockGradientDrawable.color) doReturn null

        // When
        val result = testedMapper.mapDrawableToColor(mockGradientDrawable, mockInternalLogger)

        // Then
        assertThat(result).isNull()
    }

    protected open fun createTestedMapper(): DrawableToColorMapper = AndroidMDrawableToColorMapper()
}
