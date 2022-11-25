/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.recorder.mapper

import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import com.datadog.android.sessionreplay.model.MobileSegment
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import fr.xgouchet.elmyr.annotation.FloatForgery
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
internal class MaskAllWireframeMapperTest {

    @FloatForgery
    var fakePixelDensity: Float = 1f

    @Mock
    lateinit var mockViewWireframeMapper: ViewWireframeMapper

    @Mock
    lateinit var mockImageWireframeMapper: ViewScreenshotWireframeMapper

    @Mock
    lateinit var mockMaskAllTextWireframeMapper: MaskAllTextWireframeMapper

    @Mock
    lateinit var mockButtonWireframeMapper: ButtonWireframeMapper

    @Mock
    lateinit var mockShapeWireframe: MobileSegment.Wireframe.ShapeWireframe

    @Mock
    lateinit var mockImageWireframe: MobileSegment.Wireframe.ShapeWireframe

    @Mock
    lateinit var mockMaskedTextWireframe: MobileSegment.Wireframe.TextWireframe

    @Mock
    lateinit var mockButtonWireframe: MobileSegment.Wireframe.TextWireframe

    lateinit var testedMaskAllWireframeMapper: MaskAllWireframeMapper

    @BeforeEach
    fun `set up`() {
        testedMaskAllWireframeMapper = MaskAllWireframeMapper(
            mockViewWireframeMapper,
            mockImageWireframeMapper,
            mockMaskAllTextWireframeMapper,
            mockButtonWireframeMapper
        )
    }

    @Test
    fun `M resolve a ShapeWireframe W map { View }`() {
        // Given
        val mockView: View = mock()
        whenever(mockViewWireframeMapper.map(mockView, fakePixelDensity))
            .thenReturn(mockShapeWireframe)

        // When
        val wireframe = testedMaskAllWireframeMapper.map(mockView, fakePixelDensity)

        // Then
        assertThat(wireframe).isEqualTo(mockShapeWireframe)
    }

    @Test
    fun `M resolve a ShapeWireframe W map { ImageView }`() {
        // Given
        val mockView: ImageView = mock()
        whenever(mockImageWireframeMapper.map(mockView, fakePixelDensity))
            .thenReturn(mockImageWireframe)

        // When
        val wireframe = testedMaskAllWireframeMapper.map(mockView, fakePixelDensity)

        // Then
        assertThat(wireframe).isEqualTo(mockImageWireframe)
    }

    @Test
    fun `M resolve a masked TextWireframe W map { TextView }`() {
        // Given
        val mockView: TextView = mock()
        whenever(mockMaskAllTextWireframeMapper.map(mockView, fakePixelDensity))
            .thenReturn(mockMaskedTextWireframe)

        // When
        val wireframe = testedMaskAllWireframeMapper.map(mockView, fakePixelDensity)

        // Then
        assertThat(wireframe).isEqualTo(mockMaskedTextWireframe)
    }

    @Test
    fun `M resolve a ButtonWireframe W map { Button }`() {
        // Given
        val mockView: Button = mock()
        whenever(mockButtonWireframeMapper.map(mockView, fakePixelDensity))
            .thenReturn(mockButtonWireframe)

        // When
        val wireframe = testedMaskAllWireframeMapper.map(mockView, fakePixelDensity)

        // Then
        assertThat(wireframe).isEqualTo(mockButtonWireframe)
    }

    @Test
    fun `M return the ImageMapper W getImageMapper`() {
        assertThat(testedMaskAllWireframeMapper.imageMapper).isEqualTo(mockImageWireframeMapper)
    }
}
