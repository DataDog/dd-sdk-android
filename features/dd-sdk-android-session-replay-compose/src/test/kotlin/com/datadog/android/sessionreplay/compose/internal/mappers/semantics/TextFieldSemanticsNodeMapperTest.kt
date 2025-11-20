/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.compose.internal.mappers.semantics

import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.SemanticsConfiguration
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.GenericFontFamily
import com.datadog.android.sessionreplay.TextAndInputPrivacy
import com.datadog.android.sessionreplay.compose.internal.data.UiContext
import com.datadog.android.sessionreplay.compose.test.elmyr.SessionReplayComposeForgeConfigurator
import com.datadog.android.sessionreplay.model.MobileSegment
import com.datadog.android.sessionreplay.utils.AsyncJobStatusCallback
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.FloatForgery
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.LongForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(SessionReplayComposeForgeConfigurator::class)
internal class TextFieldSemanticsNodeMapperTest : AbstractSemanticsNodeMapperTest() {

    private lateinit var testedTextFieldSemanticsNodeMapper: TextFieldSemanticsNodeMapper

    @Mock
    private lateinit var mockSemanticsNode: SemanticsNode

    @Mock
    private lateinit var mockAsyncJobStatusCallback: AsyncJobStatusCallback

    @Mock
    private lateinit var mockShape: Shape

    @Mock
    private lateinit var mockSemanticsConfiguration: SemanticsConfiguration

    @LongForgery(min = 0xffffffff)
    var fakeBackgroundColor: Long = 0L

    @FloatForgery
    var fakeCornerRadius: Float = 0f

    @StringForgery(regex = "#[0-9A-F]{8}")
    lateinit var fakeBackgroundColorHexString: String

    @StringForgery(regex = "#[0-9A-F]{8}")
    lateinit var fakeTextColorHexString: String

    @Forgery
    lateinit var fakeUiContext: UiContext

    @Forgery
    lateinit var fakeTextLayoutInfo: TextLayoutInfo

    @StringForgery
    lateinit var fakeEditText: String

    @BeforeEach
    override fun `set up`(forge: Forge) {
        super.`set up`(forge)
        mockColorStringFormatter(fakeBackgroundColor, fakeBackgroundColorHexString)
        mockColorStringFormatter(fakeTextLayoutInfo.color.toLong(), fakeTextColorHexString)
        whenever(mockDensity.density) doReturn fakeDensity
        testedTextFieldSemanticsNodeMapper = TextFieldSemanticsNodeMapper(
            colorStringFormatter = mockColorStringFormatter,
            semanticsUtils = mockSemanticsUtils
        )
    }

    @Test
    fun `M return the correct wireframe W map`() {
        // Given
        val mockSemanticsNode = mockSemanticsNode()
        val innerBounds = rectToBounds(fakeBounds, fakeDensity)
        whenever(mockSemanticsNode.config) doReturn mockSemanticsConfiguration
        whenever(mockSemanticsConfiguration.getOrNull(SemanticsProperties.EditableText)) doReturn AnnotatedString(
            fakeEditText
        )
        whenever(mockSemanticsUtils.resolveTextLayoutInfo(mockSemanticsNode)) doReturn fakeTextLayoutInfo
        whenever(mockSemanticsUtils.resolveInnerBounds(mockSemanticsNode)) doReturn innerBounds
        whenever(mockSemanticsUtils.resolveBackgroundColor(mockSemanticsNode)) doReturn fakeBackgroundColor
        whenever(mockSemanticsUtils.resolveBackgroundShape(mockSemanticsNode)) doReturn mockShape
        whenever(
            mockSemanticsUtils.resolveCornerRadius(
                eq(mockShape),
                any(),
                any()
            )
        ) doReturn fakeCornerRadius
        whenever(
            mockSemanticsUtils.resolveCornerRadius(
                mockShape,
                fakeGlobalBounds,
                mockDensity
            )
        ) doReturn fakeCornerRadius

        // When
        val actual = testedTextFieldSemanticsNodeMapper.map(
            mockSemanticsNode,
            fakeUiContext,
            mockAsyncJobStatusCallback
        )

        // Then
        val expectedShapeWireframe = MobileSegment.Wireframe.ShapeWireframe(
            id = (fakeSemanticsId.toLong() shl 32),
            x = (fakeBounds.left / fakeDensity).toLong(),
            y = (fakeBounds.top / fakeDensity).toLong(),
            width = (fakeBounds.size.width / fakeDensity).toLong(),
            height = (fakeBounds.size.height / fakeDensity).toLong(),
            shapeStyle = MobileSegment.ShapeStyle(
                backgroundColor = fakeBackgroundColorHexString,
                cornerRadius = fakeCornerRadius
            )
        )
        val expectedText =
            if (fakeUiContext.textAndInputPrivacy == TextAndInputPrivacy.MASK_SENSITIVE_INPUTS) {
                fakeEditText
            } else {
                "***"
            }
        val expectedTextWireframe = MobileSegment.Wireframe.TextWireframe(
            id = (fakeSemanticsId.toLong() shl 32) + 1,
            x = (fakeBounds.left / fakeDensity).toLong(),
            y = (fakeBounds.top / fakeDensity).toLong(),
            width = (fakeBounds.size.width / fakeDensity).toLong(),
            height = (fakeBounds.size.height / fakeDensity).toLong(),
            text = expectedText,
            textStyle = MobileSegment.TextStyle(
                family = (fakeTextLayoutInfo.fontFamily as? GenericFontFamily)?.name
                    ?: DEFAULT_FONT_FAMILY,
                size = fakeTextLayoutInfo.fontSize,
                color = fakeTextColorHexString,
                truncationMode = fakeTextLayoutInfo.textOverflow
            ),
            textPosition = MobileSegment.TextPosition(
                padding = MobileSegment.Padding(
                    left = (4 * fakeUiContext.composeDensity.density).toLong()
                ),
                alignment = MobileSegment.Alignment(
                    horizontal = MobileSegment.Horizontal.LEFT,
                    vertical = MobileSegment.Vertical.CENTER
                )
            )
        )

        assertThat(actual.wireframes).contains(expectedShapeWireframe, expectedTextWireframe)
    }

    @Test
    fun `M pass down the override privacy W map() { privacy is overridden }`(forge: Forge) {
        // Given
        val mockSemanticsNode = mockSemanticsNodeWithBound {}
        val fakeTextInputPrivacy = forge.aValueFrom(TextAndInputPrivacy::class.java)
        val innerBounds = rectToBounds(fakeBounds, fakeDensity)
        whenever(mockSemanticsNode.config) doReturn mockSemanticsConfiguration
        whenever(mockSemanticsConfiguration.getOrNull(SemanticsProperties.EditableText)) doReturn AnnotatedString(
            fakeEditText
        )
        whenever(mockSemanticsUtils.resolveTextLayoutInfo(mockSemanticsNode)) doReturn fakeTextLayoutInfo
        whenever(mockSemanticsUtils.resolveInnerBounds(mockSemanticsNode)) doReturn innerBounds
        whenever(mockSemanticsUtils.resolveBackgroundColor(mockSemanticsNode)) doReturn fakeBackgroundColor
        whenever(mockSemanticsUtils.resolveBackgroundShape(mockSemanticsNode)) doReturn mockShape
        whenever(
            mockSemanticsUtils.resolveCornerRadius(
                eq(mockShape),
                any(),
                any()
            )
        ) doReturn fakeCornerRadius
        whenever(
            mockSemanticsUtils.resolveCornerRadius(
                mockShape,
                fakeGlobalBounds,
                mockDensity
            )
        ) doReturn fakeCornerRadius
        whenever(mockSemanticsUtils.getTextAndInputPrivacyOverride(mockSemanticsNode)) doReturn fakeTextInputPrivacy

        // When
        val result = testedTextFieldSemanticsNodeMapper.map(
            mockSemanticsNode,
            fakeUiContext,
            mockAsyncJobStatusCallback
        )

        // Then
        assertThat(result.uiContext?.textAndInputPrivacy).isEqualTo(fakeTextInputPrivacy)
    }

    private fun mockSemanticsNode(): SemanticsNode {
        return mockSemanticsNodeWithBound {
            whenever(mockSemanticsNode.layoutInfo).doReturn(mockLayoutInfo)
        }
    }

    companion object {
        private const val DEFAULT_FONT_FAMILY = "Roboto, sans-serif"
    }
}
