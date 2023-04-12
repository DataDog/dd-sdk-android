/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.mapper

import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.view.inputmethod.EditorInfo
import android.widget.TextView
import com.datadog.android.sessionreplay.internal.recorder.aMockTextView
import com.datadog.android.sessionreplay.internal.recorder.densityNormalized
import com.datadog.android.sessionreplay.internal.recorder.obfuscator.StringObfuscator
import com.datadog.android.sessionreplay.model.MobileSegment
import com.datadog.android.sessionreplay.utils.StringUtils
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.StringForgery
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.Mock

internal abstract class BaseTextViewWireframeMapperTest : BaseWireframeMapperTest() {

    lateinit var testedTextWireframeMapper: TextViewMapper

    @Mock
    lateinit var mockStringObfuscator: StringObfuscator

    @StringForgery
    lateinit var fakeText: String

    @StringForgery
    lateinit var fakeDefaultObfuscatedText: String

    @BeforeEach
    fun `set up`() {
        whenever(mockStringObfuscator.obfuscate(fakeText))
            .thenReturn(fakeDefaultObfuscatedText)
        testedTextWireframeMapper = initTestedMapper()
    }

    protected open fun initTestedMapper(): TextViewMapper {
        return TextViewMapper(mockStringObfuscator)
    }

    @ParameterizedTest
    @MethodSource("textTypefaces")
    fun `M resolve a TextWireframe W map() { TextView with fontStyle }`(
        fakeTypeface: Typeface?,
        expectedFontFamily: String,
        forge: Forge
    ) {
        // Given
        val fakeFontSize = forge.aFloat(min = 0f)
        val fakeStyleColor = forge.aStringMatching("#[0-9a-f]{6}ff")
        val fakeFontColor = fakeStyleColor
            .substring(1)
            .toLong(16)
            .shr(8)
            .toInt()
        val mockTextView: TextView = forge.aMockTextView().apply {
            whenever(this.typeface).thenReturn(fakeTypeface)
            whenever(this.textSize).thenReturn(fakeFontSize)
            whenever(this.currentTextColor).thenReturn(fakeFontColor)
            whenever(this.text).thenReturn(fakeText)
        }

        // When
        val textWireframes = testedTextWireframeMapper.map(mockTextView, fakeSystemInformation)

        // Then
        val expectedWireframes = mockTextView.toTextWireframes().map {
            it.copy(
                text = fakeDefaultObfuscatedText,
                textStyle = MobileSegment.TextStyle(
                    expectedFontFamily,
                    fakeFontSize.toLong().densityNormalized(fakeSystemInformation.screenDensity),
                    fakeStyleColor
                )
            )
        }
        assertThat(textWireframes).isEqualTo(expectedWireframes)
    }

    @ParameterizedTest
    @MethodSource("textAlignments")
    fun `M resolve a TextWireframe W map() { TextView with textAlignment }`(
        fakeTextAlignment: Int,
        expectedTextAlignment: MobileSegment.Alignment,
        forge: Forge
    ) {
        // Given
        val mockTextView: TextView = forge.aMockTextView().apply {
            whenever(this.text).thenReturn(fakeText)
            whenever(this.typeface).thenReturn(mock())
            whenever(this.textAlignment).thenReturn(fakeTextAlignment)
        }

        // When
        val textWireframes = testedTextWireframeMapper.map(mockTextView, fakeSystemInformation)

        // Then
        val expectedWireframes = mockTextView.toTextWireframes().map {
            it.copy(
                text = fakeDefaultObfuscatedText,
                textPosition = MobileSegment.TextPosition(
                    padding = MobileSegment.Padding(0, 0, 0, 0),
                    alignment = expectedTextAlignment
                )
            )
        }
        assertThat(textWireframes).isEqualTo(expectedWireframes)
    }

    @ParameterizedTest
    @MethodSource("textAlignmentsFromGravity")
    fun `M resolve a TextWireframe W map() { TextView with textAlignment from gravity }`(
        fakeGravity: Int,
        expectedTextAlignment: MobileSegment.Alignment,
        forge: Forge
    ) {
        // Given
        val mockTextView: TextView = forge.aMockTextView().apply {
            whenever(this.text).thenReturn(fakeText)
            whenever(this.typeface).thenReturn(mock())
            whenever(this.textAlignment).thenReturn(TextView.TEXT_ALIGNMENT_GRAVITY)
            whenever(this.gravity).thenReturn(fakeGravity)
        }

        // When
        val textWireframes = testedTextWireframeMapper.map(mockTextView, fakeSystemInformation)

        // Then
        val expectedWireframes = mockTextView.toTextWireframes().map {
            it.copy(
                text = fakeDefaultObfuscatedText,
                textPosition = MobileSegment.TextPosition(
                    padding = MobileSegment.Padding(0, 0, 0, 0),
                    alignment = expectedTextAlignment
                )
            )
        }
        assertThat(textWireframes).isEqualTo(expectedWireframes)
    }

    @Test
    fun `M resolve a TextWireframe W map() { TextView with textPadding }`(forge: Forge) {
        // Given
        val fakeTextPaddingTop = forge.anInt()
        val fakeTextPaddingBottom = forge.anInt()
        val fakeTextPaddingStart = forge.anInt()
        val fakeTextPaddingEnd = forge.anInt()
        val mockTextView: TextView = forge.aMockTextView().apply {
            whenever(this.text).thenReturn(fakeText)
            whenever(this.typeface).thenReturn(mock())
            whenever(this.totalPaddingTop).thenReturn(fakeTextPaddingTop)
            whenever(this.totalPaddingBottom).thenReturn(fakeTextPaddingBottom)
            whenever(this.totalPaddingStart).thenReturn(fakeTextPaddingStart)
            whenever(this.totalPaddingEnd).thenReturn(fakeTextPaddingEnd)
        }
        val expectedWireframeTextPadding = MobileSegment.Padding(
            fakeTextPaddingTop.densityNormalized(fakeSystemInformation.screenDensity).toLong(),
            fakeTextPaddingBottom.densityNormalized(fakeSystemInformation.screenDensity).toLong(),
            fakeTextPaddingStart.densityNormalized(fakeSystemInformation.screenDensity).toLong(),
            fakeTextPaddingEnd.densityNormalized(fakeSystemInformation.screenDensity).toLong()
        )

        // When
        val textWireframes = testedTextWireframeMapper.map(mockTextView, fakeSystemInformation)

        // Then
        val expectedWireframes = mockTextView.toTextWireframes().map {
            it.copy(
                text = fakeDefaultObfuscatedText,
                textPosition = MobileSegment.TextPosition(
                    padding = expectedWireframeTextPadding,
                    alignment = MobileSegment.Alignment(
                        MobileSegment.Horizontal.LEFT,
                        MobileSegment.Vertical
                            .CENTER
                    )
                )
            )
        }
        assertThat(textWireframes).isEqualTo(expectedWireframes)
    }

    @Test
    fun `M resolve a TextWireframe with shapeStyle W map { TextView with ColorDrawable }`(
        forge: Forge
    ) {
        // Given
        val fakeStyleColor = forge.aStringMatching("#[0-9a-f]{8}")
        val fakeViewAlpha = forge.aFloat(min = 0f, max = 1f)
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
        val mockTextView = forge.aMockTextView().apply {
            whenever(this.background).thenReturn(mockDrawable)
            whenever(this.text).thenReturn(fakeText)
            whenever(this.typeface).thenReturn(mock())
            whenever(this.alpha).thenReturn(fakeViewAlpha)
        }

        // When
        val textWireframes = testedTextWireframeMapper.map(mockTextView, fakeSystemInformation)

        // Then
        val expectedWireframes = mockTextView.toTextWireframes().map {
            it.copy(
                text = fakeDefaultObfuscatedText,
                shapeStyle = MobileSegment.ShapeStyle(
                    backgroundColor = fakeStyleColor,
                    opacity = fakeViewAlpha,
                    cornerRadius = null
                )
            )
        }
        assertThat(textWireframes).isEqualTo(expectedWireframes)
    }

    @Test
    fun `M resolve a TextWireframe W map() { TextView with text }`(forge: Forge) {
        // Given
        val fakeDefaultObfuscatedText = forge.aString()
        val fakeText = forge.aString()
        whenever(mockStringObfuscator.obfuscate(fakeText))
            .thenReturn(fakeDefaultObfuscatedText)
        val mockTextView: TextView = forge.aMockTextView().apply {
            whenever(this.text).thenReturn(fakeText)
            whenever(this.typeface).thenReturn(mock())
        }

        // When
        val textWireframes = testedTextWireframeMapper.map(mockTextView, fakeSystemInformation)

        // Then
        val expectedWireframes = mockTextView.toTextWireframes()
            .map { it.copy(text = fakeDefaultObfuscatedText) }
        assertThat(textWireframes).isEqualTo(expectedWireframes)
    }

    @Test
    fun `M resolve a TextWireframe W map() { TextView without text, with hint }`(forge: Forge) {
        // Given
        val fakeDefaultObfuscatedText = forge.aString()
        val fakeHintText = forge.aString()
        val fakeHintColor = forge.anInt(min = 0, max = 0xffffff)
        whenever(mockStringObfuscator.obfuscate(fakeHintText))
            .thenReturn(fakeDefaultObfuscatedText)
        val mockColorStateList: ColorStateList = mock {
            whenever(it.defaultColor).thenReturn(fakeHintColor)
        }
        val mockTextView: TextView = forge.aMockTextView().apply {
            whenever(this.text).thenReturn("")
            whenever(this.hint).thenReturn(fakeHintText)
            whenever(this.hintTextColors).thenReturn(mockColorStateList)
            whenever(this.typeface).thenReturn(mock())
        }

        // When
        val textWireframes = testedTextWireframeMapper.map(mockTextView, fakeSystemInformation)

        // Then
        val expectedWireframes = mockTextView
            .toTextWireframes()
            .map {
                it.copy(
                    text = fakeDefaultObfuscatedText,
                    textStyle = MobileSegment.TextStyle(
                        TextViewMapper.SANS_SERIF_FAMILY_NAME,
                        mockTextView.textSize.toLong()
                            .densityNormalized(fakeSystemInformation.screenDensity),
                        StringUtils.formatColorAndAlphaAsHexa(
                            fakeHintColor,
                            OPAQUE_ALPHA_VALUE
                        )
                    )
                )
            }
        assertThat(textWireframes).isEqualTo(expectedWireframes)
    }

    @Test
    fun `M resolve a TextWireframe W map() { TextView without text, with hint, no hint color }`(
        forge: Forge
    ) {
        // Given
        val fakeDefaultObfuscatedText = forge.aString()
        val fakeHintText = forge.aString()
        val fakeTextColor = forge.anInt(min = 0, max = 0xffffff)
        whenever(mockStringObfuscator.obfuscate(fakeHintText))
            .thenReturn(fakeDefaultObfuscatedText)
        val mockTextView: TextView = forge.aMockTextView().apply {
            whenever(this.text).thenReturn("")
            whenever(this.hint).thenReturn(fakeHintText)
            whenever(this.hintTextColors).thenReturn(null)
            whenever(this.typeface).thenReturn(mock())
            whenever(this.currentTextColor).thenReturn(fakeTextColor)
        }

        // When
        val textWireframes = testedTextWireframeMapper.map(mockTextView, fakeSystemInformation)

        // Then
        val expectedWireframes = mockTextView
            .toTextWireframes()
            .map {
                it.copy(
                    text = fakeDefaultObfuscatedText,
                    textStyle = MobileSegment.TextStyle(
                        TextViewMapper.SANS_SERIF_FAMILY_NAME,
                        mockTextView.textSize.toLong()
                            .densityNormalized(fakeSystemInformation.screenDensity),
                        StringUtils.formatColorAndAlphaAsHexa(
                            fakeTextColor,
                            OPAQUE_ALPHA_VALUE
                        )
                    )
                )
            }
        assertThat(textWireframes).isEqualTo(expectedWireframes)
    }

    @Test
    fun `M mask the text value W map() { inputType is password }`(forge: Forge) {
        // Given
        val fakeText = forge.aString()
        val mockTextView: TextView = forge.aMockTextView().apply {
            whenever(this.text).thenReturn(fakeText)
            whenever(this.typeface).thenReturn(mock())
            whenever(this.inputType).thenReturn(
                EditorInfo.TYPE_TEXT_VARIATION_PASSWORD
                    .or(EditorInfo.TYPE_CLASS_TEXT)
            )
        }

        // When
        val textWireframes = testedTextWireframeMapper.map(mockTextView, fakeSystemInformation)

        // Then
        val expectedWireframes = mockTextView.toTextWireframes()
            .map { it.copy(text = TextViewMapper.STATIC_MASK) }
        assertThat(textWireframes).isEqualTo(expectedWireframes)
    }

    @Test
    fun `M mask the text value W map() { inputType is visible password }`(forge: Forge) {
        // Given
        val fakeText = forge.aString()
        val mockTextView: TextView = forge.aMockTextView().apply {
            whenever(this.text).thenReturn(fakeText)
            whenever(this.typeface).thenReturn(mock())
            whenever(this.inputType).thenReturn(
                EditorInfo.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                    .or(EditorInfo.TYPE_CLASS_TEXT)
            )
        }

        // When
        val textWireframes = testedTextWireframeMapper.map(mockTextView, fakeSystemInformation)

        // Then
        val expectedWireframes = mockTextView.toTextWireframes()
            .map { it.copy(text = TextViewMapper.STATIC_MASK) }
        assertThat(textWireframes).isEqualTo(expectedWireframes)
    }

    @Test
    fun `M mask the text value W map() { inputType is number password }`(forge: Forge) {
        // Given
        val fakeText = forge.aString()
        val mockTextView: TextView = forge.aMockTextView().apply {
            whenever(this.text).thenReturn(fakeText)
            whenever(this.typeface).thenReturn(mock())
            whenever(this.inputType).thenReturn(
                EditorInfo.TYPE_NUMBER_VARIATION_PASSWORD
                    .or(EditorInfo.TYPE_CLASS_NUMBER)
            )
        }

        // When
        val textWireframes = testedTextWireframeMapper.map(mockTextView, fakeSystemInformation)

        // Then
        val expectedWireframes = mockTextView.toTextWireframes()
            .map { it.copy(text = TextViewMapper.STATIC_MASK) }
        assertThat(textWireframes).isEqualTo(expectedWireframes)
    }

    @Test
    fun `M mask the text value W map() { inputType is web password }`(forge: Forge) {
        // Given
        val fakeText = forge.aString()
        val mockTextView: TextView = forge.aMockTextView().apply {
            whenever(this.text).thenReturn(fakeText)
            whenever(this.typeface).thenReturn(mock())
            whenever(this.inputType).thenReturn(
                EditorInfo.TYPE_TEXT_VARIATION_WEB_PASSWORD
                    .or(EditorInfo.TYPE_CLASS_TEXT)
            )
        }

        // When
        val textWireframes = testedTextWireframeMapper.map(mockTextView, fakeSystemInformation)

        // Then
        val expectedWireframes = mockTextView.toTextWireframes()
            .map { it.copy(text = TextViewMapper.STATIC_MASK) }
        assertThat(textWireframes).isEqualTo(expectedWireframes)
    }

    @Test
    fun `M mask the text value W map() { inputType is email }`(forge: Forge) {
        // Given
        val fakeText = forge.aString()
        val mockTextView: TextView = forge.aMockTextView().apply {
            whenever(this.text).thenReturn(fakeText)
            whenever(this.typeface).thenReturn(mock())
            whenever(this.inputType).thenReturn(
                EditorInfo.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
                    .or(EditorInfo.TYPE_CLASS_TEXT)
            )
        }

        // When
        val textWireframes = testedTextWireframeMapper.map(mockTextView, fakeSystemInformation)

        // Then
        val expectedWireframes = mockTextView.toTextWireframes()
            .map { it.copy(text = TextViewMapper.STATIC_MASK) }
        assertThat(textWireframes).isEqualTo(expectedWireframes)
    }

    @Test
    fun `M mask the text value W map() { inputType is web email }`(forge: Forge) {
        // Given
        val fakeText = forge.aString()
        val mockTextView: TextView = forge.aMockTextView().apply {
            whenever(this.text).thenReturn(fakeText)
            whenever(this.typeface).thenReturn(mock())
            whenever(this.inputType).thenReturn(
                EditorInfo.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS
                    .or(EditorInfo.TYPE_CLASS_TEXT)
            )
        }

        // When
        val textWireframes = testedTextWireframeMapper.map(mockTextView, fakeSystemInformation)

        // Then
        val expectedWireframes = mockTextView.toTextWireframes()
            .map { it.copy(text = TextViewMapper.STATIC_MASK) }
        assertThat(textWireframes).isEqualTo(expectedWireframes)
    }

    @Test
    fun `M mask the text value W map() { inputType is postal address }`(forge: Forge) {
        // Given
        val fakeText = forge.aString()
        val mockTextView: TextView = forge.aMockTextView().apply {
            whenever(this.text).thenReturn(fakeText)
            whenever(this.typeface).thenReturn(mock())
            whenever(this.inputType).thenReturn(
                EditorInfo.TYPE_TEXT_VARIATION_POSTAL_ADDRESS
                    .or(EditorInfo.TYPE_CLASS_TEXT)
            )
        }

        // When
        val textWireframes = testedTextWireframeMapper.map(mockTextView, fakeSystemInformation)

        // Then
        val expectedWireframes = mockTextView.toTextWireframes()
            .map { it.copy(text = TextViewMapper.STATIC_MASK) }
        assertThat(textWireframes).isEqualTo(expectedWireframes)
    }

    @Test
    fun `M mask the text value W map() { inputType is phone number }`(forge: Forge) {
        // Given
        val fakeText = forge.aString()
        val mockTextView: TextView = forge.aMockTextView().apply {
            whenever(this.text).thenReturn(fakeText)
            whenever(this.typeface).thenReturn(mock())
            whenever(this.inputType).thenReturn(EditorInfo.TYPE_CLASS_PHONE)
        }

        // When
        val textWireframes = testedTextWireframeMapper.map(mockTextView, fakeSystemInformation)

        // Then
        val expectedWireframes = mockTextView.toTextWireframes()
            .map { it.copy(text = TextViewMapper.STATIC_MASK) }
        assertThat(textWireframes).isEqualTo(expectedWireframes)
    }
}
