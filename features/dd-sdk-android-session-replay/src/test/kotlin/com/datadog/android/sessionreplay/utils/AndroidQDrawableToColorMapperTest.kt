package com.datadog.android.sessionreplay.utils

//noinspection SuspiciousImport
import android.graphics.BlendModeColorFilter
import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import android.os.Build
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.tools.unit.annotations.TestTargetApi
import com.datadog.tools.unit.extensions.ApiLevelExtension
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
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
        val mockFillPaint = mock<Paint>().apply {
            whenever(this.color) doReturn baseColor
            whenever(this.alpha) doReturn baseAlpha.toInt()
            whenever(this.colorFilter) doReturn mockColorFilter
        }
        val gradientDrawable = GradientDrawable().apply {
            LegacyDrawableToColorMapper.fillPaintField?.set(this, mockFillPaint)
        }

        // When
        val result = testedMapper.mapDrawableToColor(gradientDrawable)

        // Then
        assertThat(result).isEqualTo(expectedBlendColor)
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
        val mockFillPaint = mock<Paint>().apply {
            whenever(this.color) doReturn baseColor
            whenever(this.alpha) doReturn baseAlpha.toInt()
            whenever(this.colorFilter) doReturn mockColorFilter
        }
        val gradientDrawable = GradientDrawable().apply {
            LegacyDrawableToColorMapper.fillPaintField?.set(this, mockFillPaint)
        }

        // When
        val result = testedMapper.mapDrawableToColor(gradientDrawable)

        // Then
        assertThat(result).isEqualTo(fillPaintColor)
    }
}
