/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.recorder.mapper

import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.view.View
import androidx.annotation.RequiresApi
import com.datadog.android.sessionreplay.model.MobileSegment
import com.datadog.android.sessionreplay.recorder.aMockView
import com.datadog.android.sessionreplay.utils.ForgeConfigurator
import com.datadog.tools.unit.annotations.TestTargetApi
import com.datadog.tools.unit.extensions.ApiLevelExtension
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class),
    ExtendWith(ApiLevelExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(ForgeConfigurator::class)
internal class ViewWireframeMapperTest : BaseWireframeMapperTest() {

    lateinit var testedWireframeMapper: ViewWireframeMapper

    @BeforeEach
    fun `set up`() {
        testedWireframeMapper = ViewWireframeMapper()
    }

    @Test
    fun `M resolve a ShapeWireframe W map()`(forge: Forge) {
        // Given
        val mockView: View = forge.aMockView()
        val expectedWireframe = mockView.toShapeWireframe()

        // When
        val shapeWireframe = testedWireframeMapper.map(mockView, fakePixelDensity)

        // Then
        assertThat(shapeWireframe).isEqualTo(expectedWireframe)
    }

    @Test
    fun `M use the View hashcode for Wireframe id W produce()`(forge: Forge) {
        // Given
        val mockViews = forge.aList(size = forge.anInt(min = 10, max = 20)) {
            aMockView<View>()
        }

        // When
        val shapeWireframes = mockViews.map {
            testedWireframeMapper.map(
                it,
                fakePixelDensity
            )
        }

        // Then
        val idsSet: MutableSet<Long> = mutableSetOf()
        shapeWireframes.forEach {
            assertThat(idsSet).doesNotContain(it.id)
            idsSet.add(it.id)
        }
    }

    @Test
    fun `M resolve a ShapeWireframe with shapeStyle W map { ColorDrawable }`(
        forge: Forge
    ) {
        // Given
        val fakeStyleColor = forge.aStringMatching("#[0-9A-F]{8}")
        val fakeDrawableColor = fakeStyleColor
            .substring(1)
            .toLong(16)
            .shr(8)
            .toInt()
        val fakeDrawableAlpha = fakeStyleColor
            .substring(1)
            .toLong(16)
            .and(ALPHA_MASK)
            .toInt()
        val mockDrawable = mock<ColorDrawable> {
            whenever(it.color).thenReturn(fakeDrawableColor)
            whenever(it.alpha).thenReturn(fakeDrawableAlpha)
        }
        val mockView = forge.aMockView<View>().apply {
            whenever(this.background).thenReturn(mockDrawable)
        }

        // When
        val shapeWireframe = testedWireframeMapper.map(mockView, fakePixelDensity)

        // Then
        val expectedWireframe = mockView.toShapeWireframe().copy(
            shapeStyle = MobileSegment
                .ShapeStyle(
                    backgroundColor = fakeStyleColor,
                    opacity = fakeDrawableAlpha,
                    cornerRadius = null
                )
        )
        assertThat(shapeWireframe).isEqualTo(expectedWireframe)
    }

    @RequiresApi(Build.VERSION_CODES.M)
    @TestTargetApi(Build.VERSION_CODES.M)
    @Test
    fun `M resolve a ShapeWireframe with shapeStyle W map {InsetDrawable, M}`(
        forge: Forge
    ) {
        // Given
        val fakeStyleColor = forge.aStringMatching("#[0-9A-F]{8}")
        val fakeDrawableColor = fakeStyleColor
            .substring(1)
            .toLong(16)
            .shr(8)
            .toInt()
        val fakeDrawableAlpha = fakeStyleColor
            .substring(1)
            .toLong(16)
            .and(ALPHA_MASK)
            .toInt()
        val mockDrawable = mock<ColorDrawable> {
            whenever(it.color).thenReturn(fakeDrawableColor)
            whenever(it.alpha).thenReturn(fakeDrawableAlpha)
        }
        val mockInsetDrawable: InsetDrawable = mock<InsetDrawable> {
            whenever(it.drawable).thenReturn(mockDrawable)
        }
        val mockView = forge.aMockView<View>().apply {
            whenever(this.background).thenReturn(mockInsetDrawable)
        }

        // When
        val shapeWireframe = testedWireframeMapper.map(mockView, fakePixelDensity)

        // Then
        val expectedWireframe = mockView.toShapeWireframe().copy(
            shapeStyle = MobileSegment
                .ShapeStyle(
                    backgroundColor = fakeStyleColor,
                    opacity = fakeDrawableAlpha,
                    cornerRadius = null
                )
        )
        assertThat(shapeWireframe).isEqualTo(expectedWireframe)
    }

    @Test
    fun `M resolve a ShapeWireframe no shapeStyle W map { InsetDrawable, lower than M }`(
        forge: Forge
    ) {
        // Given
        val fakeStyleColor = forge.aStringMatching("#[0-9A-F]{8}")
        val fakeDrawableColor = fakeStyleColor
            .substring(1)
            .toLong(16)
            .shr(8)
            .toInt()
        val fakeDrawableAlpha = fakeStyleColor
            .substring(1)
            .toLong(16)
            .and(ALPHA_MASK)
            .toInt()
        val mockDrawable = mock<ColorDrawable> {
            whenever(it.color).thenReturn(fakeDrawableColor)
            whenever(it.alpha).thenReturn(fakeDrawableAlpha)
        }
        val mockInsetDrawable: InsetDrawable = mock<InsetDrawable> {
            whenever(it.drawable).thenReturn(mockDrawable)
        }
        val mockView = forge.aMockView<View>().apply {
            whenever(this.background).thenReturn(mockInsetDrawable)
        }

        // When
        val shapeWireframe = testedWireframeMapper.map(mockView, fakePixelDensity)

        // Then
        val expectedWireframe = mockView.toShapeWireframe()
            .copy(border = MobileSegment.ShapeBorder("#000000FF", 1))
        assertThat(shapeWireframe).isEqualTo(expectedWireframe)
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    @TestTargetApi(Build.VERSION_CODES.LOLLIPOP)
    @Test
    fun `M resolve a ShapeWireframe with shapeStyle W map {RippleDrawable, ColorDrawable, L}`(
        forge: Forge
    ) {
        // Given
        val fakeStyleColor = forge.aStringMatching("#[0-9A-F]{8}")
        val fakeDrawableColor = fakeStyleColor
            .substring(1)
            .toLong(16)
            .shr(8)
            .toInt()
        val fakeDrawableAlpha = fakeStyleColor
            .substring(1)
            .toLong(16)
            .and(ALPHA_MASK)
            .toInt()
        val mockDrawable = mock<ColorDrawable> {
            whenever(it.color).thenReturn(fakeDrawableColor)
            whenever(it.alpha).thenReturn(fakeDrawableAlpha)
        }
        val mockRipple: RippleDrawable = mock<RippleDrawable> {
            whenever(it.numberOfLayers).thenReturn(forge.anInt(min = 1))
            whenever(it.getDrawable(0)).thenReturn(mockDrawable)
        }
        val mockView = forge.aMockView<View>().apply {
            whenever(this.background).thenReturn(mockRipple)
        }

        // When
        val shapeWireframe = testedWireframeMapper.map(mockView, fakePixelDensity)

        // Then
        val expectedWireframe = mockView.toShapeWireframe().copy(
            shapeStyle = MobileSegment
                .ShapeStyle(
                    backgroundColor = fakeStyleColor,
                    opacity = fakeDrawableAlpha,
                    cornerRadius = null
                )
        )
        assertThat(shapeWireframe).isEqualTo(expectedWireframe)
    }

    @Test
    fun `M resolve a ShapeWireframe no shapeStyle W map {RippleDrawable, ColorDrawable, lowL}`(
        forge: Forge
    ) {
        // Given
        val fakeStyleColor = forge.aStringMatching("#[0-9A-F]{8}")
        val fakeDrawableColor = fakeStyleColor
            .substring(1)
            .toLong(16)
            .shr(8)
            .toInt()
        val fakeDrawableAlpha = fakeStyleColor
            .substring(1)
            .toLong(16)
            .and(ALPHA_MASK)
            .toInt()
        val mockDrawable = mock<ColorDrawable> {
            whenever(it.color).thenReturn(fakeDrawableColor)
            whenever(it.alpha).thenReturn(fakeDrawableAlpha)
        }
        val mockRipple: RippleDrawable = mock<RippleDrawable> {
            whenever(it.numberOfLayers).thenReturn(forge.anInt(min = 1))
            whenever(it.getDrawable(0)).thenReturn(mockDrawable)
        }
        val mockView = forge.aMockView<View>().apply {
            whenever(this.background).thenReturn(mockRipple)
        }

        // When
        val shapeWireframe = testedWireframeMapper.map(mockView, fakePixelDensity)

        // Then
        val expectedWireframe = mockView.toShapeWireframe().copy(
            border = MobileSegment.ShapeBorder
            ("#000000FF", 1)
        )
        assertThat(shapeWireframe).isEqualTo(expectedWireframe)
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    @TestTargetApi(Build.VERSION_CODES.LOLLIPOP)
    @Test
    fun `M resolve a ShapeWireframe no shapeStyle W produce {RippleDrawable, NonColorDrawable, L}`(
        forge: Forge
    ) {
        // Given
        val mockDrawable = mock<Drawable>()
        val mockRipple: RippleDrawable = mock {
            whenever(it.numberOfLayers).thenReturn(forge.anInt(min = 1))
            whenever(it.getDrawable(0)).thenReturn(mockDrawable)
        }
        val mockView = forge.aMockView<View>().apply {
            whenever(this.background).thenReturn(mockRipple)
        }

        // When
        val shapeWireframe = testedWireframeMapper.map(mockView, fakePixelDensity)

        // Then
        val expectedWireframe = mockView.toShapeWireframe()
            .copy(border = MobileSegment.ShapeBorder("#000000FF", 1))
        assertThat(shapeWireframe).isEqualTo(expectedWireframe)
    }
}
