/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.material

import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.android.sessionreplay.material.internal.densityNormalized
import com.datadog.android.sessionreplay.model.MobileSegment
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
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
internal class TabWireframeMapperTest : BaseTabWireframeMapperTest() {

    override fun provideTestInstance(): TabWireframeMapper {
        return TabWireframeMapper(
            viewUtils = mockViewUtils,
            uniqueIdentifierGenerator = mockUniqueIdentifierGenerator,
            textViewMapper = mockTextWireframeMapper
        )
    }

    @Test
    fun `M map the Tab to a list of wireframes W map() { tab has label, tab selected }`() {
        // Given
        whenever(mockTabView.isSelected).thenReturn(true)
        val density = fakeSystemInformation.screenDensity
        val expectedTabIndicatorStartPadding = fakePaddingStart.toLong().densityNormalized(density)
        val expectedTabIndicatorEndPadding = fakePaddingEnd.toLong().densityNormalized(density)
        val expectedTabIndicatorXPos = fakeGlobalBounds.x + expectedTabIndicatorStartPadding
        val expectedTabIndicatorHeight = TabWireframeMapper.SELECTED_TAB_INDICATOR_HEIGHT_IN_PX
            .densityNormalized(density)
        val expectedTabIndicatorYPos = fakeGlobalBounds.y + fakeGlobalBounds.height -
            expectedTabIndicatorHeight
        val expectedTabIndicatorWidth = fakeGlobalBounds.width - expectedTabIndicatorStartPadding -
            expectedTabIndicatorEndPadding
        val expectedTabIndicatorColor = fakeTextWireframes.first().textStyle.color
        val expectedTabIndicatorWireframe = MobileSegment.Wireframe.ShapeWireframe(
            id = fakeTabIndicatorUniqueId,
            x = expectedTabIndicatorXPos,
            y = expectedTabIndicatorYPos,
            width = expectedTabIndicatorWidth,
            height = expectedTabIndicatorHeight,
            shapeStyle = MobileSegment.ShapeStyle(
                backgroundColor = expectedTabIndicatorColor,
                opacity = mockTabView.alpha
            )
        )
        val expectedMappedWireframes = fakeTextWireframes + expectedTabIndicatorWireframe

        // When
        val mappedWireframes = testedTabWireframeMapper.map(mockTabView, fakeSystemInformation)

        // Then
        assertThat(mappedWireframes).isEqualTo(expectedMappedWireframes)
    }

    @Test
    fun `M map the Tab to a list of wireframes W map() { tab has label, tab not selected }`() {
        // Given
        whenever(mockTabView.isSelected).thenReturn(false)
        val expectedMappedWireframes = fakeTextWireframes

        // When
        val mappedWireframes = testedTabWireframeMapper.map(mockTabView, fakeSystemInformation)

        // Then
        assertThat(mappedWireframes).isEqualTo(expectedMappedWireframes)
    }

    @Test
    fun `M map the Tab to a list of wireframes W map() { tab has no label, tab selected }`() {
        // Given
        for (i in 0 until mockTabView.childCount) {
            whenever(mockTabView.getChildAt(i)).thenReturn(mock())
        }
        whenever(mockTabView.isSelected).thenReturn(true)
        val density = fakeSystemInformation.screenDensity
        val expectedTabIndicatorStartPadding = fakePaddingStart.toLong().densityNormalized(density)
        val expectedTabIndicatorEndPadding = fakePaddingEnd.toLong().densityNormalized(density)
        val expectedTabIndicatorXPos = fakeGlobalBounds.x + expectedTabIndicatorStartPadding
        val expectedTabIndicatorHeight = TabWireframeMapper.SELECTED_TAB_INDICATOR_HEIGHT_IN_PX
            .densityNormalized(density)
        val expectedTabIndicatorYPos = fakeGlobalBounds.y + fakeGlobalBounds.height -
            expectedTabIndicatorHeight
        val expectedTabIndicatorWidth = fakeGlobalBounds.width - expectedTabIndicatorStartPadding -
            expectedTabIndicatorEndPadding
        val expectedTabIndicatorColor = TabWireframeMapper.SELECTED_TAB_INDICATOR_DEFAULT_COLOR
        val expectedTabIndicatorWireframe = MobileSegment.Wireframe.ShapeWireframe(
            id = fakeTabIndicatorUniqueId,
            x = expectedTabIndicatorXPos,
            y = expectedTabIndicatorYPos,
            width = expectedTabIndicatorWidth,
            height = expectedTabIndicatorHeight,
            shapeStyle = MobileSegment.ShapeStyle(
                backgroundColor = expectedTabIndicatorColor,
                opacity = mockTabView.alpha
            )
        )
        val expectedMappedWireframes = listOf(expectedTabIndicatorWireframe)

        // When
        val mappedWireframes = testedTabWireframeMapper.map(mockTabView, fakeSystemInformation)

        // Then
        assertThat(mappedWireframes).isEqualTo(expectedMappedWireframes)
    }

    @Test
    fun `M map the Tab to a list of wireframes W map() { tab has no label, tab not selected }`() {
        // Given
        for (i in 0 until mockTabView.childCount) {
            whenever(mockTabView.getChildAt(i)).thenReturn(mock())
        }
        whenever(mockTabView.isSelected).thenReturn(false)

        // When
        val mappedWireframes = testedTabWireframeMapper.map(mockTabView, fakeSystemInformation)

        // Then
        assertThat(mappedWireframes).isEmpty()
    }

    @Test
    fun `M map the Tab to a list of wireframes W map() { tab has no children, tab selected }`() {
        // Given
        whenever(mockTabView.childCount).thenReturn(0)
        whenever(mockTabView.isSelected).thenReturn(true)
        val density = fakeSystemInformation.screenDensity
        val expectedTabIndicatorStartPadding = fakePaddingStart.toLong().densityNormalized(density)
        val expectedTabIndicatorEndPadding = fakePaddingEnd.toLong().densityNormalized(density)
        val expectedTabIndicatorXPos = fakeGlobalBounds.x + expectedTabIndicatorStartPadding
        val expectedTabIndicatorHeight = TabWireframeMapper.SELECTED_TAB_INDICATOR_HEIGHT_IN_PX
            .densityNormalized(density)
        val expectedTabIndicatorYPos = fakeGlobalBounds.y + fakeGlobalBounds.height -
            expectedTabIndicatorHeight
        val expectedTabIndicatorWidth = fakeGlobalBounds.width - expectedTabIndicatorStartPadding -
            expectedTabIndicatorEndPadding
        val expectedTabIndicatorColor = TabWireframeMapper.SELECTED_TAB_INDICATOR_DEFAULT_COLOR
        val expectedTabIndicatorWireframe = MobileSegment.Wireframe.ShapeWireframe(
            id = fakeTabIndicatorUniqueId,
            x = expectedTabIndicatorXPos,
            y = expectedTabIndicatorYPos,
            width = expectedTabIndicatorWidth,
            height = expectedTabIndicatorHeight,
            shapeStyle = MobileSegment.ShapeStyle(
                backgroundColor = expectedTabIndicatorColor,
                opacity = mockTabView.alpha
            )
        )
        val expectedMappedWireframes = listOf(expectedTabIndicatorWireframe)

        // When
        val mappedWireframes = testedTabWireframeMapper.map(mockTabView, fakeSystemInformation)

        // Then
        assertThat(mappedWireframes).isEqualTo(expectedMappedWireframes)
    }

    @Test
    fun `M map the Tab to a list of wireframes W map() { tab has no children, tab not selected }`() {
        // Given
        whenever(mockTabView.childCount).thenReturn(0)
        whenever(mockTabView.isSelected).thenReturn(false)

        // When
        val mappedWireframes = testedTabWireframeMapper.map(mockTabView, fakeSystemInformation)

        // Then
        assertThat(mappedWireframes).isEmpty()
    }
}
