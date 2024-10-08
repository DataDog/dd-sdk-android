/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.material

import com.datadog.android.sessionreplay.TextAndInputPrivacy
import com.datadog.android.sessionreplay.material.forge.ForgeConfigurator
import com.datadog.android.sessionreplay.material.internal.SliderWireframeMapper
import com.datadog.android.sessionreplay.model.MobileSegment
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(ForgeConfigurator::class)
internal class SliderWireframeMapperTest : BaseSliderWireframeMapperTest() {

    override fun provideTestInstance(): SliderWireframeMapper {
        return SliderWireframeMapper(mockViewIdentifierResolver, mockColorStringFormatter, mockViewBoundsResolver)
    }

    @Test
    fun `M map the Slider to a list of wireframes W map() {ALLOW}`() {
        // Given
        fakeMappingContext = fakeMappingContext.copy(textAndInputPrivacy = TextAndInputPrivacy.MASK_SENSITIVE_INPUTS)
        val expectedInactiveTrackWireframe = MobileSegment.Wireframe.ShapeWireframe(
            id = fakeInactiveTrackId,
            x = fakeExpectedInactiveTrackXPos,
            y = fakeExpectedInactiveTrackYPos,
            width = fakeExpectedInactiveTrackWidth,
            height = fakeExpectedInactiveTrackHeight,
            shapeStyle = MobileSegment.ShapeStyle(
                backgroundColor = fakeExpectedTrackInactiveHtmlColor,
                opacity = fakeViewAlpha
            )
        )
        val expectedActiveTrackWireframe = MobileSegment.Wireframe.ShapeWireframe(
            id = fakeActiveTrackId,
            x = fakeExpectedActiveTrackXPos,
            y = fakeExpectedActiveTrackYPos,
            width = fakeExpectedActiveTrackWidth,
            height = fakeExpectedActiveTrackHeight,
            shapeStyle = MobileSegment.ShapeStyle(
                backgroundColor = fakeExpectedTrackActiveHtmlColor,
                opacity = fakeViewAlpha
            )
        )
        val expectedThumbWireframe = MobileSegment.Wireframe.ShapeWireframe(
            id = fakeThumbId,
            x = fakeExpectedThumbXPos,
            y = fakeExpectedThumbYPos,
            width = fakeExpectedThumbHeight,
            height = fakeExpectedThumbHeight,
            shapeStyle = MobileSegment.ShapeStyle(
                backgroundColor = fakeExpectedThumbHtmlColor,
                opacity = fakeViewAlpha,
                cornerRadius = SliderWireframeMapper.THUMB_SHAPE_CORNER_RADIUS
            )
        )

        // When
        val mappedWireframes = testedSliderWireframeMapper.map(
            mockSlider,
            fakeMappingContext,
            mockAsyncJobStatusCallback,
            mockInternalLogger
        )

        // Then
        assertThat(mappedWireframes).isEqualTo(
            listOf(
                expectedInactiveTrackWireframe,
                expectedActiveTrackWireframe,
                expectedThumbWireframe
            )
        )
    }

    @Test
    fun `M map the Slider to a list of wireframes W map() {MASK}`() {
        // Given
        fakeMappingContext = fakeMappingContext.copy(textAndInputPrivacy = TextAndInputPrivacy.MASK_ALL)
        val expectedInactiveTrackWireframe = MobileSegment.Wireframe.ShapeWireframe(
            id = fakeInactiveTrackId,
            x = fakeExpectedInactiveTrackXPos,
            y = fakeExpectedInactiveTrackYPos,
            width = fakeExpectedInactiveTrackWidth,
            height = fakeExpectedInactiveTrackHeight,
            shapeStyle = MobileSegment.ShapeStyle(
                backgroundColor = fakeExpectedTrackInactiveHtmlColor,
                opacity = fakeViewAlpha
            )
        )

        // When
        val mappedWireframes = testedSliderWireframeMapper.map(
            mockSlider,
            fakeMappingContext,
            mockAsyncJobStatusCallback,
            mockInternalLogger
        )

        // Then
        assertThat(mappedWireframes).isEqualTo(
            listOf(
                expectedInactiveTrackWireframe
            )
        )
    }

    @Test
    fun `M map the Slider to a list of wireframes W map() {MASK_USER_INPUT}`() {
        // Given
        fakeMappingContext = fakeMappingContext.copy(textAndInputPrivacy = TextAndInputPrivacy.MASK_ALL_INPUTS)
        val expectedInactiveTrackWireframe = MobileSegment.Wireframe.ShapeWireframe(
            id = fakeInactiveTrackId,
            x = fakeExpectedInactiveTrackXPos,
            y = fakeExpectedInactiveTrackYPos,
            width = fakeExpectedInactiveTrackWidth,
            height = fakeExpectedInactiveTrackHeight,
            shapeStyle = MobileSegment.ShapeStyle(
                backgroundColor = fakeExpectedTrackInactiveHtmlColor,
                opacity = fakeViewAlpha
            )
        )

        // When
        val mappedWireframes = testedSliderWireframeMapper.map(
            mockSlider,
            fakeMappingContext,
            mockAsyncJobStatusCallback,
            mockInternalLogger
        )

        // Then
        assertThat(mappedWireframes).isEqualTo(
            listOf(
                expectedInactiveTrackWireframe
            )
        )
    }
}
