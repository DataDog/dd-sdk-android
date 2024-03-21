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
import com.datadog.android.sessionreplay.internal.recorder.aMockTextView
import com.datadog.android.sessionreplay.internal.recorder.densityNormalized
import com.datadog.android.sessionreplay.internal.recorder.obfuscator.rules.TextValueObfuscationRule
import com.datadog.android.sessionreplay.internal.utils.shapeStyle
import com.datadog.android.sessionreplay.model.MobileSegment
import com.datadog.android.sessionreplay.utils.GlobalBounds
import com.datadog.android.sessionreplay.utils.ImageWireframeHelper
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.FloatForgery
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.annotation.LongForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.Mock
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
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
    lateinit var mockDisplayMetrics: DisplayMetrics

    @StringForgery
    lateinit var fakeText: String

    @StringForgery
    lateinit var fakeDefaultObfuscatedText: String

    @LongForgery
    var fakeWireframeId: Long = 0

    @IntForgery(min = 0, max = 0xffffff)
    var fakeCurrentTextColor: Int = 0

    @StringForgery(regex = "#[0-9A-F]{8}")
    lateinit var fakeCurrentTextColorString: String

    @Forgery
    lateinit var fakeBounds: GlobalBounds

    @BeforeEach
    fun `set up`() {
        fakeMappingContext = fakeMappingContext.copy(imageWireframeHelper = mockImageWireframeHelper)
        whenever(mockViewIdentifierResolver.resolveChildUniqueIdentifier(any(), any()))
            .thenReturn(System.identityHashCode(this).toLong())

        whenever(mockResources.displayMetrics).thenReturn(mockDisplayMetrics)

        whenever(mockViewIdentifierResolver.resolveViewId(any())) doReturn fakeWireframeId

        whenever(mockViewBoundsResolver.resolveViewGlobalBounds(any(), any()))
            .thenReturn(fakeBounds)

        whenever(
            mockColorStringFormatter.formatColorAndAlphaAsHexString(
                fakeCurrentTextColor,
                OPAQUE_ALPHA_VALUE
            )
        ) doReturn fakeCurrentTextColorString

        testedTextWireframeMapper = initTestedMapper()
    }

    abstract fun initTestedMapper(): TextViewMapper

    protected fun TextView.toTextWireframes(): List<MobileSegment.Wireframe.TextWireframe> {
        val screenDensity = fakeMappingContext.systemInformation.screenDensity
        return listOf(
            MobileSegment.Wireframe.TextWireframe(
                id = fakeWireframeId,
                x = fakeBounds.x,
                y = fakeBounds.y,
                width = fakeBounds.width,
                height = fakeBounds.height,
                text = resolveTextValue(this),
                textStyle = MobileSegment.TextStyle(
                    TextViewMapper.SANS_SERIF_FAMILY_NAME,
                    textSize.toLong().densityNormalized(screenDensity),
                    fakeCurrentTextColorString
                ),
                textPosition = MobileSegment.TextPosition(
                    MobileSegment.Padding(0, 0, 0, 0),
                    alignment =
                    MobileSegment.Alignment(
                        MobileSegment.Horizontal.LEFT,
                        MobileSegment.Vertical
                            .CENTER
                    )
                )
            )
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
        val mockTextView: TextView = forge.aMockTextView().apply {
            whenever(this.typeface).thenReturn(fakeTypeface)
            whenever(this.textSize).thenReturn(fakeFontSize)
            whenever(this.currentTextColor).thenReturn(fakeCurrentTextColor)
            whenever(this.text).thenReturn(fakeText)
            whenever(this.resources).thenReturn(mockResources)
        }

        whenever(mockObfuscationRule.resolveObfuscatedValue(mockTextView, fakeMappingContext))
            .thenReturn(fakeDefaultObfuscatedText)

        // When
        val textWireframes = testedTextWireframeMapper.map(mockTextView, fakeMappingContext, mockAsyncJobStatusCallback)

        // Then
        val expectedWireframes = mockTextView.toTextWireframes().map {
            it.copy(
                text = fakeDefaultObfuscatedText,
                textStyle = MobileSegment.TextStyle(
                    expectedFontFamily,
                    fakeFontSize.toLong().densityNormalized(fakeMappingContext.systemInformation.screenDensity),
                    fakeCurrentTextColorString
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
            whenever(this.currentTextColor).thenReturn(fakeCurrentTextColor)
        }
        whenever(mockObfuscationRule.resolveObfuscatedValue(mockTextView, fakeMappingContext))
            .thenReturn(fakeDefaultObfuscatedText)

        // When
        val textWireframes = testedTextWireframeMapper.map(mockTextView, fakeMappingContext, mockAsyncJobStatusCallback)

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
            whenever(this.currentTextColor).thenReturn(fakeCurrentTextColor)
        }
        whenever(mockObfuscationRule.resolveObfuscatedValue(mockTextView, fakeMappingContext))
            .thenReturn(fakeDefaultObfuscatedText)

        // When
        val textWireframes = testedTextWireframeMapper.map(mockTextView, fakeMappingContext, mockAsyncJobStatusCallback)

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
    fun `M resolve a TextWireframe W map() { TextView with textPadding }`(
        forge: Forge
    ) {
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
            whenever(this.currentTextColor).thenReturn(fakeCurrentTextColor)
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
        val textWireframes = testedTextWireframeMapper.map(mockTextView, fakeMappingContext, mockAsyncJobStatusCallback)

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
        forge: Forge,
        @IntForgery fakeBackgroundColor: Int,
        @StringForgery(regex = "#[0-9A-F]{8}") fakeBackgroundColorString: String,
        @FloatForgery(0f, 1f) fakeViewAlpha: Float
    ) {
        // Given
        val mockDrawable = mock<ColorDrawable>()
        val mockTextView = forge.aMockTextView().apply {
            whenever(this.background).thenReturn(mockDrawable)
            whenever(this.text).thenReturn(fakeText)
            whenever(this.typeface).thenReturn(mock())
            whenever(this.alpha).thenReturn(fakeViewAlpha)
            whenever(this.resources).thenReturn(mockResources)
            whenever(this.currentTextColor).thenReturn(fakeCurrentTextColor)
        }
        whenever(mockObfuscationRule.resolveObfuscatedValue(mockTextView, fakeMappingContext))
            .thenReturn(fakeDefaultObfuscatedText)
        whenever(mockDrawableToColorMapper.mapDrawableToColor(mockDrawable)).thenReturn(fakeBackgroundColor)
        whenever(mockColorStringFormatter.formatColorAsHexString(fakeBackgroundColor))
            .thenReturn(fakeBackgroundColorString)

        // When
        val actualWireframes = testedTextWireframeMapper.map(
            mockTextView,
            fakeMappingContext,
            mockAsyncJobStatusCallback
        )

        // Then
        val textWireframes = mockTextView.toTextWireframes()

        val backgroundWireframe = MobileSegment.Wireframe.ShapeWireframe(
            id = fakeWireframeId,
            x = textWireframes[0].x,
            y = textWireframes[0].y,
            width = mockTextView.width.densityNormalized(
                fakeMappingContext.systemInformation.screenDensity
            ).toLong(),
            height = mockTextView.height.densityNormalized(
                fakeMappingContext.systemInformation.screenDensity
            ).toLong(),
            shapeStyle = MobileSegment.ShapeStyle(
                backgroundColor = fakeBackgroundColorString,
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
        val mockDrawable = mock<Drawable>().also { mockDrawable ->
            val mockConstantState = mock<Drawable.ConstantState>().also { mockState ->
                whenever(mockState.newDrawable(anyOrNull())) doReturn mockDrawable
            }
            whenever(mockDrawable.constantState) doReturn mockConstantState
        }
        whenever(mockDrawableToColorMapper.mapDrawableToColor(mockDrawable)) doReturn null
        val mockTextView = forge.aMockTextView().apply {
            whenever(this.background).thenReturn(mockDrawable)
            whenever(this.text).thenReturn(fakeText)
            whenever(this.typeface).thenReturn(mock())
            whenever(this.alpha).thenReturn(fakeViewAlpha)
            whenever(this.resources).thenReturn(mockResources)
            whenever(this.currentTextColor).thenReturn(fakeCurrentTextColor)
        }
        val fakeBackgroundWireframe: MobileSegment.Wireframe.ImageWireframe = forge.getForgery()
        whenever(mockObfuscationRule.resolveObfuscatedValue(mockTextView, fakeMappingContext))
            .thenReturn(fakeDefaultObfuscatedText)
        whenever(
            mockImageWireframeHelper.createImageWireframe(
                view = any(),
                currentWireframeIndex = any(),
                x = any(),
                y = any(),
                width = any(),
                height = any(),
                usePIIPlaceholder = any(),
                drawable = any(),
                shapeStyle = anyOrNull(),
                border = anyOrNull(),
                clipping = anyOrNull(),
                prefix = anyOrNull(),
                asyncJobStatusCallback = anyOrNull()
            )
        ).thenReturn(fakeBackgroundWireframe)

        // When
        val actualWireframes = testedTextWireframeMapper.map(
            mockTextView,
            fakeMappingContext,
            mockAsyncJobStatusCallback
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

        verify(mockImageWireframeHelper).createImageWireframe(
            view = eq(mockTextView),
            currentWireframeIndex = any(),
            x = any(),
            y = any(),
            width = any(),
            height = any(),
            usePIIPlaceholder = any(),
            drawable = any(),
            asyncJobStatusCallback = eq(mockAsyncJobStatusCallback),
            clipping = anyOrNull(),
            shapeStyle = anyOrNull(),
            border = anyOrNull(),
            prefix = anyOrNull()
        )
    }

    @Test
    fun `M resolve a TextWireframe W map() { TextView without text, with hint }`(
        forge: Forge,
        @StringForgery fakeHintText: String,
        @IntForgery(0, 0xFFFFFFF) fakeHintColor: Int,
        @StringForgery(regex = "#[0-9A-F]{8}") fakeHintColorString: String
    ) {
        // Given
        val fakeDefaultObfuscatedText = forge.aString()
        val mockColorStateList: ColorStateList = mock {
            whenever(it.defaultColor).thenReturn(fakeHintColor)
        }
        val mockTextView: TextView = forge.aMockTextView().apply {
            whenever(this.text).thenReturn("")
            whenever(this.hint).thenReturn(fakeHintText)
            whenever(this.hintTextColors).thenReturn(mockColorStateList)
            whenever(this.typeface).thenReturn(mock())
            whenever(this.resources).thenReturn(mockResources)
            whenever(this.currentTextColor).thenReturn(fakeCurrentTextColor)
        }
        whenever(mockObfuscationRule.resolveObfuscatedValue(mockTextView, fakeMappingContext))
            .thenReturn(fakeDefaultObfuscatedText)
        whenever(mockColorStringFormatter.formatColorAndAlphaAsHexString(fakeHintColor, 255))
            .doReturn(fakeHintColorString)

        // When
        val textWireframes = testedTextWireframeMapper.map(mockTextView, fakeMappingContext, mockAsyncJobStatusCallback)

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
                        fakeHintColorString
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
        val mockTextView: TextView = forge.aMockTextView().apply {
            whenever(this.text).thenReturn("")
            whenever(this.hint).thenReturn(fakeHintText)
            whenever(this.hintTextColors).thenReturn(null)
            whenever(this.typeface).thenReturn(mock())
            whenever(this.currentTextColor).thenReturn(fakeCurrentTextColor)
            whenever(this.resources).thenReturn(mockResources)
        }

        whenever(mockObfuscationRule.resolveObfuscatedValue(mockTextView, fakeMappingContext))
            .thenReturn(fakeDefaultObfuscatedText)

        // When
        val textWireframes = testedTextWireframeMapper.map(mockTextView, fakeMappingContext, mockAsyncJobStatusCallback)

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
                        fakeCurrentTextColorString
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
            whenever(this.currentTextColor).thenReturn(fakeCurrentTextColor)
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
            mockAsyncJobStatusCallback
        )
        val imageWireframes = wireframes.filterIsInstance<MobileSegment.Wireframe.ImageWireframe>()

        // Then
        verify(mockImageWireframeHelper)
            .createCompoundDrawableWireframes(
                any(),
                any(),
                any(),
                eq(mockAsyncJobStatusCallback)
            )
        assertThat(imageWireframes).isEqualTo(listOf(mockImageWireframe))
    }
}
