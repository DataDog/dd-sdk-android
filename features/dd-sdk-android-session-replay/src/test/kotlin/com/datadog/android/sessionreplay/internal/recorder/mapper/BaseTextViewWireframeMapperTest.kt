/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.mapper

import android.content.res.ColorStateList
import android.content.res.Resources
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.util.DisplayMetrics
import android.widget.TextView
import com.datadog.android.sessionreplay.internal.AsyncJobStatusCallback
import com.datadog.android.sessionreplay.internal.recorder.aMockTextView
import com.datadog.android.sessionreplay.internal.recorder.base64.ImageWireframeHelper
import com.datadog.android.sessionreplay.internal.recorder.base64.ImageWireframeHelperCallback
import com.datadog.android.sessionreplay.internal.recorder.densityNormalized
import com.datadog.android.sessionreplay.internal.recorder.obfuscator.rules.TextValueObfuscationRule
import com.datadog.android.sessionreplay.internal.utils.shapeStyle
import com.datadog.android.sessionreplay.model.MobileSegment
import com.datadog.android.sessionreplay.utils.StringUtils
import com.datadog.android.sessionreplay.utils.UniqueIdentifierGenerator
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.StringForgery
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.Mock
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever

internal abstract class BaseTextViewWireframeMapperTest : BaseWireframeMapperTest() {

    private lateinit var testedTextWireframeMapper: TextViewMapper

    @Mock
    lateinit var mockObfuscationRule: TextValueObfuscationRule

    @Mock
    lateinit var mockResources: Resources

    @Mock
    lateinit var mockImageWireframeHelper: ImageWireframeHelper

    @Mock
    lateinit var mockUniqueIdentifierGenerator: UniqueIdentifierGenerator

    @Mock
    lateinit var mockDisplayMetrics: DisplayMetrics

    @StringForgery
    lateinit var fakeText: String

    @StringForgery
    lateinit var fakeDefaultObfuscatedText: String

    @Mock
    lateinit var mockJobStatusCallback: AsyncJobStatusCallback

    @BeforeEach
    fun `set up`() {
        whenever(mockUniqueIdentifierGenerator.resolveChildUniqueIdentifier(any(), any()))
            .thenReturn(System.identityHashCode(this).toLong())

        whenever(mockResources.displayMetrics).thenReturn(mockDisplayMetrics)
        testedTextWireframeMapper = initTestedMapper()
    }

    protected open fun initTestedMapper(): TextViewMapper {
        return TextViewMapper(
            imageWireframeHelper = mockImageWireframeHelper,
            uniqueIdentifierGenerator = mockUniqueIdentifierGenerator,
            textValueObfuscationRule = mockObfuscationRule
        )
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
            whenever(this.resources).thenReturn(mockResources)
        }

        whenever(mockObfuscationRule.resolveObfuscatedValue(mockTextView, fakeMappingContext))
            .thenReturn(fakeDefaultObfuscatedText)

        // When
        val textWireframes = testedTextWireframeMapper.map(mockTextView, fakeMappingContext)

        // Then
        val expectedWireframes = mockTextView.toTextWireframes().map {
            it.copy(
                text = fakeDefaultObfuscatedText,
                textStyle = MobileSegment.TextStyle(
                    expectedFontFamily,
                    fakeFontSize.toLong().densityNormalized(fakeMappingContext.systemInformation.screenDensity),
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
            whenever(this.resources).thenReturn(mockResources)
        }
        whenever(mockObfuscationRule.resolveObfuscatedValue(mockTextView, fakeMappingContext))
            .thenReturn(fakeDefaultObfuscatedText)

        // When
        val textWireframes = testedTextWireframeMapper.map(mockTextView, fakeMappingContext)

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
            whenever(this.resources).thenReturn(mockResources)
        }

        whenever(mockObfuscationRule.resolveObfuscatedValue(mockTextView, fakeMappingContext))
            .thenReturn(fakeDefaultObfuscatedText)

        // When
        val textWireframes = testedTextWireframeMapper.map(mockTextView, fakeMappingContext)

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
            whenever(this.resources).thenReturn(mockResources)
        }
        val expectedWireframeTextPadding = MobileSegment.Padding(
            fakeTextPaddingTop.densityNormalized(fakeMappingContext.systemInformation.screenDensity).toLong(),
            fakeTextPaddingBottom.densityNormalized(fakeMappingContext.systemInformation.screenDensity).toLong(),
            fakeTextPaddingStart.densityNormalized(fakeMappingContext.systemInformation.screenDensity).toLong(),
            fakeTextPaddingEnd.densityNormalized(fakeMappingContext.systemInformation.screenDensity).toLong()
        )
        whenever(mockObfuscationRule.resolveObfuscatedValue(mockTextView, fakeMappingContext))
            .thenReturn(fakeDefaultObfuscatedText)

        // When
        val textWireframes = testedTextWireframeMapper.map(mockTextView, fakeMappingContext)

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
    fun `M resolve a ShapeWireframe background W map {ColorDrawable background}`(
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
            whenever(this.resources).thenReturn(mockResources)
        }
        whenever(mockObfuscationRule.resolveObfuscatedValue(mockTextView, fakeMappingContext))
            .thenReturn(fakeDefaultObfuscatedText)

        // When
        val actualWireframes = testedTextWireframeMapper.map(mockTextView, fakeMappingContext)

        // Then
        val textWireframes = mockTextView.toTextWireframes()

        val backgroundWireframe = MobileSegment.Wireframe.ShapeWireframe(
            id = System.identityHashCode(this).toLong(),
            x = textWireframes[0].x,
            y = textWireframes[0].y,
            width = mockTextView.width.densityNormalized(
                fakeMappingContext.systemInformation.screenDensity
            ).toLong(),
            height = mockTextView.height.densityNormalized(
                fakeMappingContext.systemInformation.screenDensity
            ).toLong(),
            shapeStyle = MobileSegment.ShapeStyle(
                backgroundColor = fakeStyleColor,
                opacity = fakeViewAlpha,
                cornerRadius = null
            ),
            border = null
        )

        val expectedWireframes = mutableListOf<MobileSegment.Wireframe>()
        expectedWireframes.add(backgroundWireframe)

        expectedWireframes.addAll(
            mockTextView.toTextWireframes().map {
                it.copy(
                    text = fakeDefaultObfuscatedText
                )
            }
        )

        assertThat(actualWireframes.size).isEqualTo(2)
        assertThat(actualWireframes[0].shapeStyle()).isEqualTo(expectedWireframes[0].shapeStyle())
        assertThat(actualWireframes[1].shapeStyle()).isNull()
        assertThat((actualWireframes[1] as MobileSegment.Wireframe.TextWireframe).text)
            .isEqualTo((expectedWireframes[1] as MobileSegment.Wireframe.TextWireframe).text)
    }

    @Test
    fun `M resolve an ImageWireframe background W map {no ColorDrawable background}`(
        forge: Forge
    ) {
        // Given
        val fakeViewAlpha = forge.aFloat(min = 0f, max = 1f)
        val mockDrawable = mock<Drawable>()
        val mockDrawableCopy = mock<Drawable>()
        val mockConstantState = mock<Drawable.ConstantState>() {
            whenever(it.newDrawable(any())).thenReturn(mockDrawableCopy)
        }
        whenever(mockDrawable.constantState).thenReturn(mockConstantState)

        val mockTextView = forge.aMockTextView().apply {
            whenever(this.background).thenReturn(mockDrawable)
            whenever(this.text).thenReturn(fakeText)
            whenever(this.typeface).thenReturn(mock())
            whenever(this.alpha).thenReturn(fakeViewAlpha)
            whenever(this.resources).thenReturn(mockResources)
        }
        val fakeBackgroundWireframe: MobileSegment.Wireframe.ImageWireframe = forge.getForgery()
        whenever(mockObfuscationRule.resolveObfuscatedValue(mockTextView, fakeMappingContext))
            .thenReturn(fakeDefaultObfuscatedText)
        whenever(
            mockImageWireframeHelper.createImageWireframe(
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                anyOrNull(),
                anyOrNull(),
                any(),
                any()
            )
        ).thenReturn(fakeBackgroundWireframe)

        // When
        val actualWireframes = testedTextWireframeMapper.map(
            mockTextView,
            fakeMappingContext,
            mockJobStatusCallback
        )

        // Then
        val expectedWireframes = mutableListOf<MobileSegment.Wireframe>()
        expectedWireframes.add(fakeBackgroundWireframe)

        expectedWireframes.addAll(
            mockTextView.toTextWireframes().map {
                it.copy(
                    text = fakeDefaultObfuscatedText
                )
            }
        )

        assertThat(actualWireframes.size).isEqualTo(2)
        assertThat(actualWireframes[0]).isEqualTo(fakeBackgroundWireframe)
        assertThat((actualWireframes[1] as MobileSegment.Wireframe.TextWireframe).text)
            .isEqualTo((expectedWireframes[1] as MobileSegment.Wireframe.TextWireframe).text)
        argumentCaptor<ImageWireframeHelperCallback>() {
            verify(mockImageWireframeHelper).createImageWireframe(
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                anyOrNull(),
                anyOrNull(),
                any(),
                capture()
            )
            allValues.forEach() {
                it.onStart()
                it.onFinished()
            }
            verify(mockJobStatusCallback).jobStarted()
            verify(mockJobStatusCallback).jobFinished()
            verifyNoMoreInteractions(mockJobStatusCallback)
        }
    }

    @Test
    fun `M resolve a TextWireframe W map() { TextView without text, with hint }`(forge: Forge) {
        // Given
        val fakeDefaultObfuscatedText = forge.aString()
        val fakeHintText = forge.aString()
        val fakeHintColor = forge.anInt(min = 0, max = 0xffffff)
        val mockColorStateList: ColorStateList = mock {
            whenever(it.defaultColor).thenReturn(fakeHintColor)
        }
        val mockTextView: TextView = forge.aMockTextView().apply {
            whenever(this.text).thenReturn("")
            whenever(this.hint).thenReturn(fakeHintText)
            whenever(this.hintTextColors).thenReturn(mockColorStateList)
            whenever(this.typeface).thenReturn(mock())
            whenever(this.resources).thenReturn(mockResources)
        }
        whenever(mockObfuscationRule.resolveObfuscatedValue(mockTextView, fakeMappingContext))
            .thenReturn(fakeDefaultObfuscatedText)

        // When
        val textWireframes = testedTextWireframeMapper.map(mockTextView, fakeMappingContext)

        // Then
        val expectedWireframes = mockTextView
            .toTextWireframes()
            .map {
                it.copy(
                    text = fakeDefaultObfuscatedText,
                    textStyle = MobileSegment.TextStyle(
                        TextViewMapper.SANS_SERIF_FAMILY_NAME,
                        mockTextView.textSize.toLong()
                            .densityNormalized(fakeMappingContext.systemInformation.screenDensity),
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
        val mockTextView: TextView = forge.aMockTextView().apply {
            whenever(this.text).thenReturn("")
            whenever(this.hint).thenReturn(fakeHintText)
            whenever(this.hintTextColors).thenReturn(null)
            whenever(this.typeface).thenReturn(mock())
            whenever(this.currentTextColor).thenReturn(fakeTextColor)
            whenever(this.resources).thenReturn(mockResources)
        }

        whenever(mockObfuscationRule.resolveObfuscatedValue(mockTextView, fakeMappingContext))
            .thenReturn(fakeDefaultObfuscatedText)

        // When
        val textWireframes = testedTextWireframeMapper.map(mockTextView, fakeMappingContext)

        // Then
        val expectedWireframes = mockTextView
            .toTextWireframes()
            .map {
                it.copy(
                    text = fakeDefaultObfuscatedText,
                    textStyle = MobileSegment.TextStyle(
                        TextViewMapper.SANS_SERIF_FAMILY_NAME,
                        mockTextView.textSize.toLong()
                            .densityNormalized(fakeMappingContext.systemInformation.screenDensity),
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
    fun `M resolve an imageWireframe W map() { TextView with compoundDrawable }`(forge: Forge) {
        // Given
        val fakeDefaultObfuscatedText = forge.aString()
        val fakeHintText = forge.aString()
        val fakeTextColor = forge.anInt(min = 0, max = 0xffffff)
        val fakeDrawables = arrayOf<Drawable>(
            mock(),
            mock(),
            mock(),
            mock()
        )
        val mockTextView: TextView = forge.aMockTextView().apply {
            whenever(this.text).thenReturn("")
            whenever(this.hint).thenReturn(fakeHintText)
            whenever(this.hintTextColors).thenReturn(null)
            whenever(this.typeface).thenReturn(mock())
            whenever(this.currentTextColor).thenReturn(fakeTextColor)
            whenever(this.resources).thenReturn(mockResources)
            whenever(this.compoundDrawables).thenReturn(
                fakeDrawables
            )
        }
        val mockImageWireframe: MobileSegment.Wireframe.ImageWireframe = mock()
        whenever(
            mockImageWireframeHelper.createCompoundDrawableWireframes(
                any(),
                any(),
                any(),
                any()
            )
        ).thenReturn(mutableListOf(mockImageWireframe))

        whenever(mockObfuscationRule.resolveObfuscatedValue(mockTextView, fakeMappingContext))
            .thenReturn(fakeDefaultObfuscatedText)

        // When
        val wireframes = testedTextWireframeMapper.map(
            mockTextView,
            fakeMappingContext,
            mockJobStatusCallback
        )
        val imageWireframes = wireframes.filter { it is MobileSegment.Wireframe.ImageWireframe }

        // Then
        argumentCaptor<ImageWireframeHelperCallback>() {
            verify(mockImageWireframeHelper)
                .createCompoundDrawableWireframes(
                    any(),
                    any(),
                    any(),
                    capture()
                )
            allValues.forEach {
                it.onStart()
                it.onFinished()
            }
            verify(mockJobStatusCallback).jobStarted()
            verify(mockJobStatusCallback).jobFinished()
            verifyNoMoreInteractions(mockJobStatusCallback)
        }
        assertThat(imageWireframes).isEqualTo(listOf(mockImageWireframe))
    }
}
