/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.mapper

import android.graphics.Typeface
import android.text.InputType
import android.view.Gravity
import android.widget.Button
import android.widget.TextView
import com.datadog.android.sessionreplay.TextAndInputPrivacy
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.android.sessionreplay.model.MobileSegment
import com.datadog.android.sessionreplay.recorder.mapper.BaseAsyncBackgroundWireframeMapperTest
import com.datadog.android.sessionreplay.recorder.mapper.TextViewMapper
import com.datadog.android.sessionreplay.recorder.mapper.TextViewMapperTest.Companion.parametersMatrix
import com.datadog.android.sessionreplay.utils.OPAQUE_ALPHA_VALUE
import fr.xgouchet.elmyr.annotation.FloatForgery
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.data.Offset.offset
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.util.stream.Stream

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(ForgeConfigurator::class)
internal abstract class ButtonMapperTest : BaseAsyncBackgroundWireframeMapperTest<Button, ButtonMapper>() {

    @StringForgery
    lateinit var fakeText: String

    @FloatForgery(0f, 255f)
    var fakeFontSize: Float = 0f

    @IntForgery(min = 0, max = 0xffffff)
    var fakeTextColor: Int = 0

    @StringForgery(regex = "#[0-9A-F]{8}")
    lateinit var fakeTextColorHexString: String

    @BeforeEach
    fun `set up`() {
        whenever(
            mockColorStringFormatter.formatColorAndAlphaAsHexString(
                fakeTextColor,
                OPAQUE_ALPHA_VALUE
            )
        ) doReturn fakeTextColorHexString

        withTextAndInputPrivacy(privacyOption())

        testedWireframeMapper = ButtonMapper(
            mockViewIdentifierResolver,
            mockColorStringFormatter,
            mockViewBoundsResolver,
            mockDrawableToColorMapper
        )
    }

    abstract fun expectedPrivacyCompliantText(text: String): String

    abstract fun privacyOption(): TextAndInputPrivacy

    @ParameterizedTest(name = "{index} (typeface: {0}, align:{2}, gravity:{3})")
    @MethodSource("basicParametersMatrix")
    fun `M resolves wireframe W map`(
        fakeTypeface: Typeface?,
        expectedFontFamily: String,
        fakeTextAlignment: Int,
        fakeTextGravity: Int,
        expectedHorizontal: MobileSegment.Horizontal,
        expectedVertical: MobileSegment.Vertical
    ) {
        // Given
        prepareMockView<Button> { mockView ->
            whenever(mockView.typeface) doReturn fakeTypeface
            whenever(mockView.textSize) doReturn fakeFontSize
            whenever(mockView.currentTextColor) doReturn fakeTextColor
            whenever(mockView.textAlignment) doReturn fakeTextAlignment
            whenever(mockView.gravity) doReturn fakeTextGravity
            whenever(mockView.text) doReturn fakeText
            whenever(mockView.inputType) doReturn InputType.TYPE_CLASS_TEXT
        }
        val expectedFontSize = (fakeFontSize / fakeMappingContext.systemInformation.screenDensity).toLong()

        // When
        val wireframes = testedWireframeMapper.map(
            mockMappedView,
            fakeMappingContext,
            mockAsyncJobStatusCallback,
            mockInternalLogger
        )

        // Then
        assertThat(wireframes).hasSize(1)
        val textWireframe = wireframes[0] as MobileSegment.Wireframe.TextWireframe
        assertThat(textWireframe)
            .usingRecursiveComparison()
            .ignoringFieldsMatchingRegexes("textStyle.*") // Compared below, because of rounding error with density
            .isEqualTo(
                MobileSegment.Wireframe.TextWireframe(
                    id = fakeViewIdentifier,
                    x = fakeViewGlobalBounds.x,
                    y = fakeViewGlobalBounds.y,
                    width = fakeViewGlobalBounds.width,
                    height = fakeViewGlobalBounds.height,
                    clip = null,
                    shapeStyle = null,
                    border = MobileSegment.ShapeBorder(
                        color = "#000000ff",
                        width = 1L
                    ),
                    text = expectedPrivacyCompliantText(fakeText),
                    textStyle = MobileSegment.TextStyle(
                        family = expectedFontFamily,
                        size = expectedFontSize,
                        color = fakeTextColorHexString
                    ),
                    textPosition = MobileSegment.TextPosition(
                        padding = MobileSegment.Padding(0L, 0L, 0L, 0L),
                        alignment = MobileSegment.Alignment(
                            horizontal = expectedHorizontal,
                            vertical = expectedVertical
                        )
                    )
                )
            )
        assertThat(textWireframe.textStyle.family).isEqualTo(expectedFontFamily)
        assertThat(textWireframe.textStyle.color).isEqualTo(fakeTextColorHexString)
        assertThat(textWireframe.textStyle.size).isCloseTo(expectedFontSize, offset(1L))
    }

    @Test
    fun `M resolves wireframe W map {no text}`() {
        // Given
        prepareMockView<Button> { mockView ->
            whenever(mockView.typeface) doReturn null
            whenever(mockView.textSize) doReturn fakeFontSize
            whenever(mockView.currentTextColor) doReturn fakeTextColor
            whenever(mockView.textAlignment) doReturn TextView.TEXT_ALIGNMENT_GRAVITY
            whenever(mockView.gravity) doReturn Gravity.NO_GRAVITY
            whenever(mockView.text) doReturn ""
        }
        val expectedFontSize = (fakeFontSize / fakeMappingContext.systemInformation.screenDensity).toLong()

        // When
        val wireframes = testedWireframeMapper.map(
            mockMappedView,
            fakeMappingContext,
            mockAsyncJobStatusCallback,
            mockInternalLogger
        )

        // Then
        assertThat(wireframes).hasSize(1)
        val textWireframe = wireframes[0] as MobileSegment.Wireframe.TextWireframe
        assertThat(textWireframe)
            .usingRecursiveComparison()
            .ignoringFieldsMatchingRegexes("textStyle.*") // Compared below, because of rounding error with density
            .isEqualTo(
                MobileSegment.Wireframe.TextWireframe(
                    id = fakeViewIdentifier,
                    x = fakeViewGlobalBounds.x,
                    y = fakeViewGlobalBounds.y,
                    width = fakeViewGlobalBounds.width,
                    height = fakeViewGlobalBounds.height,
                    clip = null,
                    shapeStyle = null,
                    border = MobileSegment.ShapeBorder(
                        color = "#000000ff",
                        width = 1L
                    ),
                    text = expectedPrivacyCompliantText(""),
                    textStyle = MobileSegment.TextStyle(
                        family = TextViewMapper.SANS_SERIF_FAMILY_NAME,
                        size = expectedFontSize,
                        color = fakeTextColorHexString
                    ),
                    textPosition = MobileSegment.TextPosition(
                        padding = MobileSegment.Padding(0L, 0L, 0L, 0L),
                        alignment = MobileSegment.Alignment(
                            horizontal = MobileSegment.Horizontal.LEFT,
                            vertical = MobileSegment.Vertical.CENTER
                        )
                    )
                )
            )
        assertThat(textWireframe.textStyle.family).isEqualTo(TextViewMapper.SANS_SERIF_FAMILY_NAME)
        assertThat(textWireframe.textStyle.color).isEqualTo(fakeTextColorHexString)
        assertThat(textWireframe.textStyle.size).isCloseTo(expectedFontSize, offset(1L))
    }

    companion object {

        @JvmStatic
        fun basicParametersMatrix(): Stream<Arguments> {
            return parametersMatrix()
        }
    }
}
