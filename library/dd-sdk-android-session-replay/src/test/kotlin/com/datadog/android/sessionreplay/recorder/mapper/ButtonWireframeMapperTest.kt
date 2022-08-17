/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.recorder.mapper

import android.graphics.Typeface
import android.widget.Button
import android.widget.TextView
import com.datadog.android.sessionreplay.model.MobileSegment
import com.datadog.android.sessionreplay.recorder.aMockView
import com.datadog.android.sessionreplay.recorder.densityNormalized
import com.datadog.android.sessionreplay.utils.ForgeConfigurator
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
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(ForgeConfigurator::class)
internal class ButtonWireframeMapperTest : BaseWireframeMapperTest() {

    lateinit var testedButtonWireframeMapper: ButtonWireframeMapper

    @BeforeEach
    fun `set up`() {
        testedButtonWireframeMapper = ButtonWireframeMapper()
    }

    @ParameterizedTest
    @MethodSource("textTypefaces")
    fun `M resolve a TextWireframe W map() { Button with fontStyle }`(
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
        val mockButton: Button = forge.aMockView<Button>().apply {
            whenever(this.typeface).thenReturn(typeface)
            whenever(this.textSize).thenReturn(fakeFontSize)
            whenever(this.currentTextColor).thenReturn(fakeFontColor)
            whenever(this.text).thenReturn(fakeText)
        }

        // When
        val buttonWireframe = testedButtonWireframeMapper.map(mockButton, fakePixelDensity)

        // Then
        val expectedWireframe = mockButton.toTextWireframe()
            .copy(
                textStyle = MobileSegment.TextStyle(
                    expectedFontFamily,
                    fakeFontSize.toLong().densityNormalized(fakePixelDensity),
                    fakeStyleColor
                )
            )
        assertThat(buttonWireframe).isEqualTo(expectedWireframe)
    }

    @Test
    fun `M resolve a TextWireframe W map() { Button with text }`(forge: Forge) {
        // Given
        val fakeText = forge.aString()
        val mockButton: Button = forge.aMockView<Button>().apply {
            whenever(this.text).thenReturn(fakeText)
            whenever(this.typeface).thenReturn(mock())
        }

        // When
        val buttonWireframe = testedButtonWireframeMapper.map(mockButton, fakePixelDensity)

        // Then
        val expectedWireframe = mockButton.toTextWireframe().copy(text = fakeText)
        assertThat(buttonWireframe).isEqualTo(expectedWireframe)
    }

    @ParameterizedTest
    @MethodSource("textAlignments")
    fun `M resolve a TextWireframe W map() { Button with textAlignment }`(
        textAlignment: Int,
        expectedTextAlignment: MobileSegment.Alignment,
        forge: Forge
    ) {
        // Given
        val mockButton: Button = forge.aMockView<Button>().apply {
            whenever(this.text).thenReturn(forge.aString())
            whenever(this.typeface).thenReturn(mock())
            whenever(this.textAlignment).thenReturn(textAlignment)
        }

        // When
        val buttonWireframe = testedButtonWireframeMapper.map(mockButton, fakePixelDensity)

        // Then
        val expectedWireframe = mockButton.toTextWireframe().copy(
            textPosition = MobileSegment
                .TextPosition(
                    padding = MobileSegment.Padding(0, 0, 0, 0),
                    alignment = expectedTextAlignment
                )
        )
        assertThat(buttonWireframe).isEqualTo(expectedWireframe)
    }

    @ParameterizedTest
    @MethodSource("textAlignmentsFromGravity")
    fun `M resolve a TextWireframe W map() { Button with textAlignment from gravity }`(
        gravity: Int,
        expectedTextAlignment: MobileSegment.Alignment,
        forge: Forge
    ) {
        // Given
        val fakeText = forge.aString()
        val mockButton: Button = forge.aMockView<Button>().apply {
            whenever(this.text).thenReturn(fakeText)
            whenever(this.typeface).thenReturn(mock())
            whenever(this.textAlignment).thenReturn(TextView.TEXT_ALIGNMENT_GRAVITY)
            whenever(this.gravity).thenReturn(gravity)
        }

        // When
        val buttonWireframe = testedButtonWireframeMapper.map(mockButton, fakePixelDensity)

        // Then
        val expectedWireframe = mockButton.toTextWireframe().copy(
            textPosition = MobileSegment
                .TextPosition(
                    padding = MobileSegment.Padding(0, 0, 0, 0),
                    alignment = expectedTextAlignment
                )
        )
        assertThat(buttonWireframe).isEqualTo(expectedWireframe)
    }

    @Test
    fun `M resolve a TextWireframe W map() { Button with textPadding }`(forge: Forge) {
        // Given
        val fakeText = forge.aString()
        val mockButton: Button = forge.aMockView<Button>().apply {
            whenever(this.text).thenReturn(fakeText)
            whenever(this.typeface).thenReturn(mock())
        }

        // When
        val buttonWireframe = testedButtonWireframeMapper.map(mockButton, fakePixelDensity)

        // Then
        val expectedWireframe = mockButton.toTextWireframe().copy(text = fakeText)
        assertThat(buttonWireframe).isEqualTo(expectedWireframe)
    }
}
