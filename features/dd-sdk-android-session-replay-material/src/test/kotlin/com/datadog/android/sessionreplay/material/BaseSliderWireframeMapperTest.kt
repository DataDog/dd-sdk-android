/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.material

import android.content.res.ColorStateList
import com.datadog.android.sessionreplay.internal.recorder.MappingContext
import com.datadog.android.sessionreplay.material.internal.densityNormalized
import com.datadog.android.sessionreplay.utils.AsyncJobStatusCallback
import com.datadog.android.sessionreplay.utils.ColorStringFormatter
import com.datadog.android.sessionreplay.utils.GlobalBounds
import com.datadog.android.sessionreplay.utils.ViewBoundsResolver
import com.datadog.android.sessionreplay.utils.ViewIdentifierResolver
import com.google.android.material.slider.Slider
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.FloatForgery
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.annotation.LongForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

internal abstract class BaseSliderWireframeMapperTest {

    // misc
    @Forgery
    lateinit var fakeMappingContext: MappingContext

    // fake wireframe ids
    @LongForgery
    var fakeActiveTrackId: Long = 0

    @LongForgery
    var fakeInactiveTrackId: Long = 0

    @LongForgery
    var fakeThumbId: Long = 0

    // fake slider bounds
    lateinit var fakeViewGlobalBounds: GlobalBounds

    @LongForgery(min = 0, max = 100)
    var fakeSliderXPos: Long = 0

    @LongForgery(min = 0, max = 100)
    var fakeSliderYPos: Long = 0

    @IntForgery(min = 0, max = 10)
    var fakeTrackSidePadding: Int = 0

    @IntForgery(min = 0, max = 10)
    var fakeSliderStartPadding: Int = 0

    @IntForgery(min = 0, max = 10)
    var fakeSliderTopPadding: Int = 0

    @LongForgery(min = 10, max = 200)
    var fakeSliderHeight: Long = 0

    @LongForgery(min = 10, max = 200)
    var fakeSliderWidth: Long = 0

    // fake track bounds
    @IntForgery(min = 1, max = 200)
    var fakeTrackWidth: Int = 0

    @IntForgery(min = 1, max = 200)
    var fakeTrackHeight: Int = 0

    // fake thumb bounds
    @IntForgery(min = 1, max = 200)
    var fakeThumbRadius: Int = 0

    // fake slider values
    var fakeSliderValue: Float = 0f

    @FloatForgery(min = 0f, max = 100f / 2)
    var fakeSliderFromValue: Float = 0f

    @FloatForgery(min = 100f / 2, max = 100f)
    var fakeSliderToValue: Float = 0f

    // fake track colors
    @IntForgery
    var fakeTrackActiveColor: Int = 0

    @IntForgery
    var fakeTrackNotActiveColor: Int = 0

    // fake thumb colors
    @IntForgery
    var fakeThumbColor: Int = 0

    // expected thumb bounds
    var fakeExpectedThumbHeight: Long = 0
    var fakeExpectedThumbXPos: Long = 0
    var fakeExpectedThumbYPos: Long = 0

    // expected track bounds
    var fakeExpectedInactiveTrackWidth: Long = 0
    var fakeExpectedInactiveTrackHeight: Long = 0
    var fakeExpectedActiveTrackWidth: Long = 0
    var fakeExpectedActiveTrackHeight: Long = 0
    var fakeExpectedActiveTrackXPos: Long = 0
    var fakeExpectedActiveTrackYPos: Long = 0
    var fakeExpectedInactiveTrackXPos: Long = 0
    var fakeExpectedInactiveTrackYPos: Long = 0

    // fake expected track html colors
    @StringForgery(regex = "#[0-9A-Fa-f]{8}")
    lateinit var fakeExpectedTrackActiveHtmlColor: String

    @StringForgery(regex = "#[0-9A-Fa-f]{8}")
    lateinit var fakeExpectedTrackInactiveHtmlColor: String

    // fake expected thumb html colors
    @StringForgery(regex = "#[0-9A-Fa-f]{8}")
    lateinit var fakeExpectedThumbHtmlColor: String

    @FloatForgery(0f, 1f)
    var fakeViewAlpha: Float = 0f

    @Mock
    lateinit var mockThumbTintColors: ColorStateList

    @Mock
    lateinit var mockTrackActiveTintColors: ColorStateList

    @Mock
    lateinit var mockTrackNotActiveTintColors: ColorStateList

    @Mock
    lateinit var mockViewIdentifierResolver: ViewIdentifierResolver

    @Mock
    lateinit var mockColorStringFormatter: ColorStringFormatter

    @Mock
    lateinit var mockViewBoundsResolver: ViewBoundsResolver

    @Mock
    lateinit var mockAsyncJobStatusCallback: AsyncJobStatusCallback

    lateinit var testedSliderWireframeMapper: SliderWireframeMapper

    lateinit var mockSlider: Slider

    @BeforeEach
    fun `set up`(forge: Forge) {
        fakeViewGlobalBounds = GlobalBounds(
            fakeSliderXPos,
            fakeSliderYPos,
            fakeSliderWidth,
            fakeSliderHeight
        )
        fakeSliderValue = forge.aFloat(min = fakeSliderFromValue, max = fakeSliderToValue)
        val normalizedSliderYPos = fakeSliderYPos
        val normalizedSliderXPos = fakeSliderXPos
        val normalizedSliderTopPadding = fakeSliderTopPadding.toLong()
            .densityNormalized(fakeMappingContext.systemInformation.screenDensity)
        val normalizedSliderStartPadding = fakeSliderStartPadding.toLong()
            .densityNormalized(fakeMappingContext.systemInformation.screenDensity)
        val normalizedTrackSidePadding = fakeTrackSidePadding.toLong()
            .densityNormalized(fakeMappingContext.systemInformation.screenDensity)
        val normalizedSliderHeight = fakeSliderHeight
        val normalizedSliderValue = (fakeSliderValue - fakeSliderFromValue) /
            (fakeSliderToValue - fakeSliderFromValue)

        fakeExpectedInactiveTrackHeight = fakeTrackHeight.toLong()
            .densityNormalized(fakeMappingContext.systemInformation.screenDensity)
        fakeExpectedActiveTrackHeight = fakeExpectedInactiveTrackHeight

        fakeExpectedInactiveTrackWidth = fakeTrackWidth.toLong()
            .densityNormalized(fakeMappingContext.systemInformation.screenDensity)
        fakeExpectedActiveTrackWidth = (fakeExpectedInactiveTrackWidth * normalizedSliderValue)
            .toLong()
        fakeExpectedThumbHeight = fakeThumbRadius.toLong()
            .densityNormalized(fakeMappingContext.systemInformation.screenDensity) * 2
        fakeExpectedInactiveTrackXPos = normalizedSliderXPos + normalizedSliderStartPadding +
            normalizedTrackSidePadding
        fakeExpectedInactiveTrackYPos = normalizedSliderYPos + normalizedSliderTopPadding +
            (normalizedSliderHeight - fakeExpectedActiveTrackHeight) / 2
        fakeExpectedActiveTrackYPos = fakeExpectedInactiveTrackYPos
        fakeExpectedActiveTrackXPos = fakeExpectedInactiveTrackXPos
        fakeExpectedActiveTrackXPos = fakeExpectedInactiveTrackXPos
        fakeExpectedActiveTrackYPos = fakeExpectedInactiveTrackYPos
        fakeExpectedThumbXPos = fakeExpectedActiveTrackXPos + fakeExpectedActiveTrackWidth
        fakeExpectedThumbYPos = normalizedSliderYPos + normalizedSliderTopPadding +
            (normalizedSliderHeight - fakeExpectedThumbHeight) / 2
        whenever(
            mockColorStringFormatter.formatColorAndAlphaAsHexString(
                fakeThumbColor,
                SliderWireframeMapper.OPAQUE_ALPHA_VALUE
            )
        )
            .thenReturn(fakeExpectedThumbHtmlColor)

        whenever(
            mockColorStringFormatter.formatColorAndAlphaAsHexString(
                fakeTrackActiveColor,
                SliderWireframeMapper.OPAQUE_ALPHA_VALUE
            )
        )
            .thenReturn(fakeExpectedTrackActiveHtmlColor)
        whenever(
            mockColorStringFormatter.formatColorAndAlphaAsHexString(
                fakeTrackNotActiveColor,
                SliderWireframeMapper.PARTIALLY_OPAQUE_ALPHA_VALUE
            )
        )
            .thenReturn(fakeExpectedTrackInactiveHtmlColor)

        mockSlider = generateMockedSlider(forge)
        whenever(
            mockViewBoundsResolver.resolveViewGlobalBounds(
                mockSlider,
                fakeMappingContext.systemInformation.screenDensity
            )
        )
            .thenReturn(fakeViewGlobalBounds)
        whenever(
            mockViewIdentifierResolver.resolveChildUniqueIdentifier(
                mockSlider,
                SliderWireframeMapper.TRACK_ACTIVE_KEY_NAME
            )
        ).thenReturn(fakeActiveTrackId)
        whenever(
            mockViewIdentifierResolver.resolveChildUniqueIdentifier(
                mockSlider,
                SliderWireframeMapper.TRACK_NON_ACTIVE_KEY_NAME
            )
        ).thenReturn(fakeInactiveTrackId)
        whenever(
            mockViewIdentifierResolver.resolveChildUniqueIdentifier(
                mockSlider,
                SliderWireframeMapper.THUMB_KEY_NAME
            )
        ).thenReturn(fakeThumbId)
        testedSliderWireframeMapper = provideTestInstance()
    }

    abstract fun provideTestInstance(): SliderWireframeMapper

    @Test
    fun `M return empty list W map { could not generate thumb id`() {
        // Given
        whenever(
            mockViewIdentifierResolver.resolveChildUniqueIdentifier(
                mockSlider,
                SliderWireframeMapper.THUMB_KEY_NAME
            )
        ).thenReturn(null)

        // Then
        assertThat(
            testedSliderWireframeMapper.map(
                mockSlider,
                fakeMappingContext,
                mockAsyncJobStatusCallback
            )
        ).isEmpty()
    }

    @Test
    fun `M return empty list W map { could not generate active track id`() {
        // Given
        whenever(
            mockViewIdentifierResolver.resolveChildUniqueIdentifier(
                mockSlider,
                SliderWireframeMapper.TRACK_ACTIVE_KEY_NAME
            )
        ).thenReturn(null)

        // Then
        assertThat(
            testedSliderWireframeMapper.map(
                mockSlider,
                fakeMappingContext,
                mockAsyncJobStatusCallback
            )
        ).isEmpty()
    }

    @Test
    fun `M return empty list W map { could not generate inactive track id`() {
        // Given
        whenever(
            mockViewIdentifierResolver.resolveChildUniqueIdentifier(
                mockSlider,
                SliderWireframeMapper.TRACK_NON_ACTIVE_KEY_NAME
            )
        ).thenReturn(null)

        // Then
        assertThat(
            testedSliderWireframeMapper.map(
                mockSlider,
                fakeMappingContext,
                mockAsyncJobStatusCallback
            )
        ).isEmpty()
    }

    private fun generateMockedSlider(forge: Forge): Slider {
        return mock {
            val fakeDrawableState = forge.aList { anInt() }.toIntArray()
            val defaultColor = forge.aPositiveInt()
            whenever(it.drawableState).thenReturn(fakeDrawableState)
            whenever(it.alpha).thenReturn(fakeViewAlpha)
            whenever(it.paddingTop).thenReturn(fakeSliderTopPadding)
            whenever(it.paddingStart).thenReturn(fakeSliderStartPadding)
            whenever(it.trackSidePadding).thenReturn(fakeTrackSidePadding)
            whenever(it.width).thenReturn(forge.aPositiveInt(strict = true))
            whenever(it.height).thenReturn(forge.aPositiveInt(strict = true))
            whenever(it.trackWidth).thenReturn(fakeTrackWidth)
            whenever(it.trackHeight).thenReturn(fakeTrackHeight)
            whenever(it.thumbRadius).thenReturn(fakeThumbRadius)
            whenever(it.valueFrom).thenReturn(fakeSliderFromValue)
            whenever(it.valueTo).thenReturn(fakeSliderToValue)
            whenever(it.value).thenReturn(fakeSliderValue)
            whenever(it.trackActiveTintList).thenReturn(mockTrackActiveTintColors)
            whenever(it.trackInactiveTintList).thenReturn(mockTrackNotActiveTintColors)
            whenever(it.thumbTintList).thenReturn(mockThumbTintColors)
            whenever(mockThumbTintColors.defaultColor).thenReturn(defaultColor)
            whenever(mockTrackActiveTintColors.defaultColor).thenReturn(defaultColor)
            whenever(mockTrackNotActiveTintColors.defaultColor).thenReturn(defaultColor)
            whenever(mockThumbTintColors.getColorForState(fakeDrawableState, defaultColor))
                .thenReturn(fakeThumbColor)
            whenever(mockTrackActiveTintColors.getColorForState(fakeDrawableState, defaultColor))
                .thenReturn(fakeTrackActiveColor)
            whenever(mockTrackNotActiveTintColors.getColorForState(fakeDrawableState, defaultColor))
                .thenReturn(fakeTrackNotActiveColor)
        }
    }
}
