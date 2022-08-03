/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.recorder.mapper

import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.widget.Button
import android.widget.TextView
import com.datadog.android.sessionreplay.model.MobileSegment
import com.datadog.android.sessionreplay.recorder.aMockView
import com.datadog.android.sessionreplay.recorder.densityNormalized
import com.datadog.android.sessionreplay.utils.ForgeConfigurator
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
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
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
internal class TextViewWireframeMapperTest : BaseWireframeMapperTest() {

    lateinit var testedTextWireframeMapper: TextWireframeMapper

    @BeforeEach
    fun `set up`() {
        testedTextWireframeMapper = TextWireframeMapper()
    }

    @ParameterizedTest
    @MethodSource("textTypefaces")
    fun `M resolve a TextWireframe W map() { TextView with fontStyle }`(
        typeface: Typeface,
        expectedFontFamily: String,
        forge: Forge
    ) {
        // Given
        val fakeFontSize = forge.aFloat(min = 0f)
        val fakeStyleColor = forge.aStringMatching("#[0-9A-F]{6}FF")
        val fakeText = forge.aString()
        val fakeFontColor = fakeStyleColor
            .substring(1)
            .toLong(16)
            .shr(8)
            .toInt()
        val mockTextView: TextView = forge.aMockView<TextView>().apply {
            whenever(this.typeface).thenReturn(typeface)
            whenever(this.textSize).thenReturn(fakeFontSize)
            whenever(this.currentTextColor).thenReturn(fakeFontColor)
            whenever(this.text).thenReturn(fakeText)
        }

        // When
        val textWireframe = testedTextWireframeMapper.map(mockTextView, fakePixelDensity)

        // Then
        val expectedWireframe = mockTextView.toTextWireframe()
            .copy(
                textStyle = MobileSegment.TextStyle(
                    expectedFontFamily,
                    fakeFontSize.toLong().densityNormalized(fakePixelDensity),
                    fakeStyleColor
                )
            )
        assertThat(textWireframe).isEqualTo(expectedWireframe)
    }

    @Test
    fun `M resolve a TextWireframe W map() { TextView with text }`(forge: Forge) {
        // Given
        val fakeText = forge.aString()
        val mockButton: TextView = forge.aMockView<Button>().apply {
            whenever(this.text).thenReturn(fakeText)
            whenever(this.typeface).thenReturn(mock())
        }

        // When
        val textWireframe = testedTextWireframeMapper.map(mockButton, fakePixelDensity)

        // Then
        val expectedWireframe = mockButton.toTextWireframe().copy(text = fakeText)
        assertThat(textWireframe).isEqualTo(expectedWireframe)
    }

    @ParameterizedTest
    @MethodSource("textAlignments")
    fun `M resolve a TextWireframe W map() { TextView with textAlignment }`(
        textAlignment: Int,
        expectedTextAlignment: MobileSegment.Alignment,
        forge: Forge
    ) {
        // Given
        val mockTextView: TextView = forge.aMockView<TextView>().apply {
            whenever(this.text).thenReturn(forge.aString())
            whenever(this.typeface).thenReturn(mock())
            whenever(this.textAlignment).thenReturn(textAlignment)
        }

        // When
        val textWireframe = testedTextWireframeMapper.map(mockTextView, fakePixelDensity)

        // Then
        val expectedWireframe = mockTextView.toTextWireframe().copy(
            textPosition = MobileSegment
                .TextPosition(
                    padding = MobileSegment.Padding(0, 0, 0, 0),
                    alignment = expectedTextAlignment
                )
        )
        assertThat(textWireframe).isEqualTo(expectedWireframe)
    }

    @ParameterizedTest
    @MethodSource("textAlignmentsFromGravity")
    fun `M resolve a TextWireframe W map() { TextView with textAlignment from gravity }`(
        gravity: Int,
        expectedTextAlignment: MobileSegment.Alignment,
        forge: Forge
    ) {
        // Given
        val fakeText = forge.aString()
        val mockTextView: TextView = forge.aMockView<TextView>().apply {
            whenever(this.text).thenReturn(fakeText)
            whenever(this.typeface).thenReturn(mock())
            whenever(this.textAlignment).thenReturn(TextView.TEXT_ALIGNMENT_GRAVITY)
            whenever(this.gravity).thenReturn(gravity)
        }

        // When
        val textWireframe = testedTextWireframeMapper.map(mockTextView, fakePixelDensity)

        // Then
        val expectedWireframe = mockTextView.toTextWireframe().copy(
            textPosition = MobileSegment
                .TextPosition(
                    padding = MobileSegment.Padding(0, 0, 0, 0),
                    alignment = expectedTextAlignment
                )
        )
        assertThat(textWireframe).isEqualTo(expectedWireframe)
    }

    @Test
    fun `M resolve a TextWireframe W map() { TextView with textPadding }`(forge: Forge) {
        // Given
        val fakeText = forge.aString()
        val mockTextView: TextView = forge.aMockView<TextView>().apply {
            whenever(this.text).thenReturn(fakeText)
            whenever(this.typeface).thenReturn(mock())
        }

        // When
        val textWireframe = testedTextWireframeMapper.map(mockTextView, fakePixelDensity)

        // Then
        val expectedWireframe = mockTextView.toTextWireframe().copy(text = fakeText)
        assertThat(textWireframe).isEqualTo(expectedWireframe)
    }

    @Test
    fun `M resolve a TextWireframe with shapeStyle W map { TextView with ColorDrawable }`(
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
        val mockTextView = forge.aMockView<TextView>().apply {
            whenever(this.background).thenReturn(mockDrawable)
            whenever(this.text).thenReturn(forge.aString())
            whenever(this.typeface).thenReturn(mock())
        }

        // When
        val textWireframe = testedTextWireframeMapper.map(mockTextView, fakePixelDensity)

        // Then
        val expectedWireframe = mockTextView.toTextWireframe().copy(
            shapeStyle = MobileSegment
                .ShapeStyle(
                    backgroundColor = fakeStyleColor,
                    opacity = fakeDrawableAlpha,
                    cornerRadius = null
                )
        )
        assertThat(textWireframe).isEqualTo(expectedWireframe)
    }
}
