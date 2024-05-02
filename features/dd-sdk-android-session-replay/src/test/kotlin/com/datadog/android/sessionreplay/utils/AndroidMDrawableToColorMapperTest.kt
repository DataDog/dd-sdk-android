/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.utils

//noinspection SuspiciousImport
import android.R
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.tools.unit.annotations.TestTargetApi
import com.datadog.tools.unit.extensions.ApiLevelExtension
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
open class AndroidMDrawableToColorMapperTest : LegacyDrawableToColorMapperTest() {

    override fun createTestedMapper(): DrawableToColorMapper {
        return AndroidMDrawableToColorMapper()
    }

    @Test
    @TestTargetApi(Build.VERSION_CODES.M)
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
        val result = testedMapper.mapDrawableToColor(rippleDrawable)

        // Then
        assertThat(result).isEqualTo(drawableColor)
    }

    @Test
    @TestTargetApi(Build.VERSION_CODES.M)
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
        val result = testedMapper.mapDrawableToColor(insetDrawable)

        // Then
        assertThat(result).isEqualTo(drawableColor)
    }
}
