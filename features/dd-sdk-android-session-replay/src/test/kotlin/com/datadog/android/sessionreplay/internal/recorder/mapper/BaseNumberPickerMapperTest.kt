/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.mapper

import android.widget.NumberPicker
import com.datadog.android.sessionreplay.internal.recorder.densityNormalized
import com.datadog.android.sessionreplay.model.MobileSegment
import com.datadog.android.sessionreplay.recorder.mapper.BaseWireframeMapper
import com.datadog.android.sessionreplay.utils.GlobalBounds
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.FloatForgery
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.annotation.LongForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

internal abstract class BaseNumberPickerMapperTest : LegacyBaseWireframeMapperTest() {

    lateinit var testedNumberPickerMapper: BasePickerMapper

    @LongForgery
    var fakePrevLabelId: Long = 0L

    @LongForgery
    var fakeTopDividerId: Long = 0L

    @LongForgery
    var fakeSelectedLabelId: Long = 0L

    @LongForgery
    var fakeBottomDividerId: Long = 0L

    @LongForgery
    var fakeNextLabelId: Long = 0L

    @IntForgery(min = 0, max = 10)
    var fakePaddingStart: Int = 0

    @IntForgery(min = 0, max = 10)
    var fakePaddingEnd: Int = 0

    var fakeMinValue: Int = 0
    var fakeMaxValue: Int = 0
    var fakeValue: Int = 0

    @IntForgery
    var fakeTextColor: Int = 0

    @FloatForgery(min = 1f, max = 100f)
    var fakeTextSize: Float = 0f

    var fakeExpectedDividerWidth: Long = 0
    var fakeExpectedDividerHeight: Long = 0
    var fakeExpectedLabelWidth: Long = 0
    var fakeExpectedLabelHeight: Long = 0
    var fakeExpectedTextSize: Long = 0

    var fakeExpectedPrevLabelYPos: Long = 0
    var fakeExpectedPrevLabelXPos: Long = 0
    var fakeExpectedTopDividerYPos: Long = 0
    var fakeExpectedTopDividerXPos: Long = 0
    var fakeExpectedSelectedLabelYPos: Long = 0
    var fakeExpectedSelectedLabelXPos: Long = 0
    var fakeExpectedBottomDividerYPos: Long = 0
    var fakeExpectedBottomDividerXPos: Long = 0
    var fakeExpectedNextLabelYPos: Long = 0
    var fakeExpectedNextLabelXPos: Long = 0

    @StringForgery(regex = "#[0-9A-Fa-f]{8}")
    lateinit var fakeExpectedNextPrevLabelHtmlColor: String

    @StringForgery(regex = "#[0-9A-Fa-f]{8}")
    lateinit var fakeExpectedSelectedLabelHtmlColor: String

    @Forgery
    lateinit var fakeViewGlobalBounds: GlobalBounds

    lateinit var mockNumberPicker: NumberPicker

    abstract fun provideTestInstance(): BasePickerMapper

    @BeforeEach
    fun `set up`(forge: Forge) {
        fakeMinValue = forge.anInt(min = 0, max = 10)
        fakeMaxValue = forge.anInt(min = fakeMinValue + 10, max = 100)
        fakeValue = forge.anInt(min = fakeMinValue + 1, max = fakeMaxValue)
        mockNumberPicker = generateMockedNumberPicker(forge)
        val normalizedPaddingEnd = fakePaddingEnd
            .densityNormalized(fakeMappingContext.systemInformation.screenDensity)
        val normalizedPaddingStart = fakePaddingStart
            .densityNormalized(fakeMappingContext.systemInformation.screenDensity)
        val normalizedPadding = BasePickerMapper.PADDING_IN_PX
            .densityNormalized(fakeMappingContext.systemInformation.screenDensity)
        fakeExpectedDividerWidth = fakeViewGlobalBounds.width -
            normalizedPaddingEnd -
            normalizedPaddingStart
        fakeExpectedDividerHeight = BasePickerMapper.DIVIDER_HEIGHT_IN_PX
            .densityNormalized(fakeMappingContext.systemInformation.screenDensity)
        fakeExpectedLabelWidth = fakeViewGlobalBounds.width
        fakeExpectedTextSize = fakeTextSize.toLong()
            .densityNormalized(fakeMappingContext.systemInformation.screenDensity)
        fakeExpectedLabelHeight = fakeExpectedTextSize * 2

        fakeExpectedSelectedLabelYPos = fakeViewGlobalBounds.y +
            (fakeViewGlobalBounds.height - fakeExpectedLabelHeight) / 2
        fakeExpectedSelectedLabelXPos = fakeViewGlobalBounds.x
        fakeExpectedTopDividerYPos = fakeExpectedSelectedLabelYPos -
            normalizedPadding - fakeExpectedDividerHeight
        fakeExpectedTopDividerXPos = fakeViewGlobalBounds.x + normalizedPaddingStart
        fakeExpectedPrevLabelXPos = fakeExpectedSelectedLabelXPos
        fakeExpectedPrevLabelYPos = fakeExpectedTopDividerYPos -
            normalizedPadding - fakeExpectedLabelHeight
        fakeExpectedBottomDividerYPos = fakeExpectedSelectedLabelYPos +
            fakeExpectedLabelHeight + normalizedPadding
        fakeExpectedBottomDividerXPos = fakeExpectedTopDividerXPos
        fakeExpectedNextLabelXPos = fakeExpectedSelectedLabelXPos
        fakeExpectedNextLabelYPos = fakeExpectedBottomDividerYPos + normalizedPadding

        whenever(
            mockViewBoundsResolver.resolveViewGlobalBounds(
                mockNumberPicker,
                fakeMappingContext.systemInformation.screenDensity
            )
        )
            .thenReturn(fakeViewGlobalBounds)
        whenever(
            mockColorStringFormatter.formatColorAndAlphaAsHexString(
                fakeTextColor,
                BaseWireframeMapper.OPAQUE_ALPHA_VALUE
            )
        )
            .thenReturn(fakeExpectedSelectedLabelHtmlColor)
        whenever(
            mockColorStringFormatter.formatColorAndAlphaAsHexString(
                fakeTextColor,
                BasePickerMapper.PARTIALLY_OPAQUE_ALPHA_VALUE
            )
        )
            .thenReturn(fakeExpectedNextPrevLabelHtmlColor)
        whenever(
            mockViewIdentifierResolver.resolveChildUniqueIdentifier(
                mockNumberPicker,
                BasePickerMapper.PREV_INDEX_KEY_NAME
            )
        ).thenReturn(fakePrevLabelId)
        whenever(
            mockViewIdentifierResolver.resolveChildUniqueIdentifier(
                mockNumberPicker,
                BasePickerMapper.DIVIDER_TOP_KEY_NAME
            )
        ).thenReturn(fakeTopDividerId)
        whenever(
            mockViewIdentifierResolver.resolveChildUniqueIdentifier(
                mockNumberPicker,
                BasePickerMapper.SELECTED_INDEX_KEY_NAME
            )
        ).thenReturn(fakeSelectedLabelId)
        whenever(
            mockViewIdentifierResolver.resolveChildUniqueIdentifier(
                mockNumberPicker,
                BasePickerMapper.DIVIDER_BOTTOM_KEY_NAME
            )
        ).thenReturn(fakeBottomDividerId)
        whenever(
            mockViewIdentifierResolver.resolveChildUniqueIdentifier(
                mockNumberPicker,
                BasePickerMapper.NEXT_INDEX_KEY_NAME
            )
        ).thenReturn(fakeNextLabelId)
        testedNumberPickerMapper = provideTestInstance()
    }

    private fun generateMockedNumberPicker(forge: Forge): NumberPicker {
        return mock {
            whenever(it.width).thenReturn(forge.aPositiveInt(strict = true))
            whenever(it.height).thenReturn(forge.aPositiveInt(strict = true))
            whenever(it.paddingStart).thenReturn(fakePaddingStart)
            whenever(it.paddingEnd).thenReturn(fakePaddingEnd)
            whenever(it.maxValue).thenReturn(fakeMaxValue)
            whenever(it.minValue).thenReturn(fakeMinValue)
            whenever(it.value).thenReturn(fakeValue)
            whenever(it.textSize).thenReturn(fakeTextSize)
            whenever(it.textColor).thenReturn(fakeTextColor)
        }
    }

    @Test
    fun `M return empty list W map { topDividerId null }`() {
        // Given
        whenever(
            mockViewIdentifierResolver
                .resolveChildUniqueIdentifier(
                    mockNumberPicker,
                    BasePickerMapper.DIVIDER_TOP_KEY_NAME
                )
        )
            .thenReturn(null)

        // When
        val wireframes = testedNumberPickerMapper.map(
            mockNumberPicker,
            fakeMappingContext,
            mockAsyncJobStatusCallback,
            mockInternalLogger
        )

        // Then
        assertThat(wireframes).isEmpty()
    }

    @Test
    fun `M return empty list W map { selectedLabelId null }`() {
        // Given
        whenever(
            mockViewIdentifierResolver
                .resolveChildUniqueIdentifier(
                    mockNumberPicker,
                    BasePickerMapper.SELECTED_INDEX_KEY_NAME
                )
        )
            .thenReturn(null)

        // When
        val wireframes = testedNumberPickerMapper.map(
            mockNumberPicker,
            fakeMappingContext,
            mockAsyncJobStatusCallback,
            mockInternalLogger
        )

        // Then
        assertThat(wireframes).isEmpty()
    }

    @Test
    fun `M return empty list W map { bottomDividerId null }`() {
        // Given
        whenever(
            mockViewIdentifierResolver
                .resolveChildUniqueIdentifier(
                    mockNumberPicker,
                    BasePickerMapper.DIVIDER_BOTTOM_KEY_NAME
                )
        )
            .thenReturn(null)

        // When
        val wireframes = testedNumberPickerMapper.map(
            mockNumberPicker,
            fakeMappingContext,
            mockAsyncJobStatusCallback,
            mockInternalLogger
        )

        // Then
        assertThat(wireframes).isEmpty()
    }

    protected fun fakeNextLabelWireframe() =
        MobileSegment.Wireframe.TextWireframe(
            id = fakeNextLabelId,
            x = fakeExpectedNextLabelXPos,
            y = fakeExpectedNextLabelYPos,
            width = fakeExpectedLabelWidth,
            height = fakeExpectedLabelHeight,
            textStyle = MobileSegment.TextStyle(
                family = BasePickerMapper.FONT_FAMILY,
                size = fakeExpectedTextSize,
                color = fakeExpectedNextPrevLabelHtmlColor
            ),
            textPosition = MobileSegment.TextPosition(
                alignment = MobileSegment.Alignment(
                    horizontal = MobileSegment.Horizontal.CENTER,
                    vertical = MobileSegment.Vertical.CENTER
                )
            ),
            text = ""
        )

    protected fun fakeBottomDividerWireframe() = MobileSegment.Wireframe.ShapeWireframe(
        id = fakeBottomDividerId,
        x = fakeExpectedBottomDividerXPos,
        y = fakeExpectedBottomDividerYPos,
        width = fakeExpectedDividerWidth,
        height = fakeExpectedDividerHeight,
        shapeStyle = MobileSegment.ShapeStyle(
            backgroundColor = fakeExpectedSelectedLabelHtmlColor
        )
    )

    protected fun fakeSelectedLabelWireframe() =
        MobileSegment.Wireframe.TextWireframe(
            id = fakeSelectedLabelId,
            x = fakeExpectedSelectedLabelXPos,
            y = fakeExpectedSelectedLabelYPos,
            width = fakeExpectedLabelWidth,
            height = fakeExpectedLabelHeight,
            textStyle = MobileSegment.TextStyle(
                family = BasePickerMapper.FONT_FAMILY,
                size = fakeExpectedTextSize,
                color = fakeExpectedSelectedLabelHtmlColor
            ),
            textPosition = MobileSegment.TextPosition(
                alignment = MobileSegment.Alignment(
                    horizontal = MobileSegment.Horizontal.CENTER,
                    vertical = MobileSegment.Vertical.CENTER
                )
            ),
            text = ""
        )

    protected fun fakeTopDividerWireframe() = MobileSegment.Wireframe.ShapeWireframe(
        id = fakeTopDividerId,
        x = fakeExpectedTopDividerXPos,
        y = fakeExpectedTopDividerYPos,
        width = fakeExpectedDividerWidth,
        height = fakeExpectedDividerHeight,
        shapeStyle = MobileSegment.ShapeStyle(
            backgroundColor = fakeExpectedSelectedLabelHtmlColor
        )
    )

    protected fun fakePrevLabelWireframe() =
        MobileSegment.Wireframe.TextWireframe(
            id = fakePrevLabelId,
            x = fakeExpectedPrevLabelXPos,
            y = fakeExpectedPrevLabelYPos,
            width = fakeExpectedLabelWidth,
            height = fakeExpectedLabelHeight,
            textStyle = MobileSegment.TextStyle(
                family = BasePickerMapper.FONT_FAMILY,
                size = fakeExpectedTextSize,
                color = fakeExpectedNextPrevLabelHtmlColor
            ),
            textPosition = MobileSegment.TextPosition(
                alignment = MobileSegment.Alignment(
                    horizontal = MobileSegment.Horizontal.CENTER,
                    vertical = MobileSegment.Vertical.CENTER
                )
            ),
            text = ""
        )
}
