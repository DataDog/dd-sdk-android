package com.datadog.android.sessionreplay.utils

//noinspection SuspiciousImport
import android.graphics.Paint
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.RippleDrawable
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
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
open class LegacyDrawableToColorMapperTest {

    lateinit var testedMapper: DrawableToColorMapper

    @BeforeEach
    fun `set up`() {
        testedMapper = createTestedMapper()
    }

    open fun createTestedMapper(): DrawableToColorMapper {
        return LegacyDrawableToColorMapper()
    }

    @Test
    fun `M map unknown Drawable to null W mapDrawableToColor()`() {
        // Given
        val colorDrawable = mock<Drawable>()

        // When
        val result = testedMapper.mapDrawableToColor(colorDrawable)

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
        val result = testedMapper.mapDrawableToColor(colorDrawable)

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
        val result = testedMapper.mapDrawableToColor(rippleDrawable)

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
        val result = testedMapper.mapDrawableToColor(layerDrawable)

        // Then
        assertThat(result).isEqualTo(drawableColor)
    }

    @Test
    fun `M map GradientDrawable to fill paint's color W mapDrawableToColor()`(
        @IntForgery drawableColor: Int
    ) {
        // Given
        val baseColor = drawableColor and 0xFFFFFF
        val baseAlpha = (drawableColor.toLong() and 0xFF000000) shr 24
        val mockFillPaint = mock<Paint>().apply {
            whenever(this.color) doReturn baseColor
            whenever(this.alpha) doReturn baseAlpha.toInt()
        }
        val gradientDrawable = GradientDrawable().apply {
            LegacyDrawableToColorMapper.fillPaintField?.set(this, mockFillPaint)
        }

        // When
        val result = testedMapper.mapDrawableToColor(gradientDrawable)

        // Then
        assertThat(result).isEqualTo(drawableColor)
    }
}
