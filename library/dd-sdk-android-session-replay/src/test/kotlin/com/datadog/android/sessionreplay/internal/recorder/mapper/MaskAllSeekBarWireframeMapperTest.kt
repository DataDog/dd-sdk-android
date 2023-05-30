/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.mapper

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.android.sessionreplay.model.MobileSegment
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(ForgeConfigurator::class)
internal class MaskAllSeekBarWireframeMapperTest : BaseSeekBarWireframeMapperTest() {

    override fun provideTestInstance(): SeekBarWireframeMapper {
        return MaskAllSeekBarWireframeMapper(
            viewUtils = mockViewUtils,
            stringUtils = mockStringUtils,
            uniqueIdentifierGenerator = mockUniqueIdentifierGenerator
        )
    }

    @Test
    fun `M map the SeekBar to a list of wireframes W map()`() {
        // Given
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
        val mappedWireframes = testedSeekBarWireframeMapper.map(mockSeekBar, fakeMappingContext)

        // Then
        assertThat(mappedWireframes).isEqualTo(
            listOf(
                expectedInactiveTrackWireframe
            )
        )
    }

    @Test
    fun `M use default day color W map(progressTintList null, day theme)`(forge: Forge) {
        // Given
        val fakeDefaultDayNotActiveHtmlColor = forge.aStringMatching("#[0-9A-Fa-f]{8}")
        whenever(
            mockStringUtils.formatColorAndAlphaAsHexa(
                SeekBarWireframeMapper.DAY_MODE_COLOR,
                SeekBarWireframeMapper.PARTIALLY_OPAQUE_ALPHA_VALUE
            )
        )
            .thenReturn(fakeDefaultDayNotActiveHtmlColor)
        whenever(mockSeekBar.progressTintList).thenReturn(null)
        val mockResources = mock<Resources> {
            whenever(it.configuration).thenReturn(
                Configuration().apply {
                    this.uiMode =
                        Configuration.UI_MODE_NIGHT_NO
                }
            )
        }
        val mockContext = mock<Context> {
            whenever(it.resources).thenReturn(mockResources)
        }
        whenever(mockSeekBar.context).thenReturn(mockContext)
        val expectedInactiveTrackWireframe = MobileSegment.Wireframe.ShapeWireframe(
            id = fakeInactiveTrackId,
            x = fakeExpectedInactiveTrackXPos,
            y = fakeExpectedInactiveTrackYPos,
            width = fakeExpectedInactiveTrackWidth,
            height = fakeExpectedInactiveTrackHeight,
            shapeStyle = MobileSegment.ShapeStyle(
                backgroundColor = fakeDefaultDayNotActiveHtmlColor,
                opacity = fakeViewAlpha
            )
        )

        // When
        val mappedWireframes = testedSeekBarWireframeMapper.map(mockSeekBar, fakeMappingContext)

        // Then
        assertThat(mappedWireframes).isEqualTo(listOf(expectedInactiveTrackWireframe))
    }

    @Test
    fun `M use default night color W map(progressTintList null, night theme)`(forge: Forge) {
        // Given
        val fakeDefaultNightNotActiveHtmlColor = forge.aStringMatching("#[0-9A-Fa-f]{8}")
        whenever(
            mockStringUtils.formatColorAndAlphaAsHexa(
                SeekBarWireframeMapper.NIGHT_MODE_COLOR,
                SeekBarWireframeMapper.PARTIALLY_OPAQUE_ALPHA_VALUE
            )
        )
            .thenReturn(fakeDefaultNightNotActiveHtmlColor)
        whenever(mockSeekBar.progressTintList).thenReturn(null)
        val mockResources = mock<Resources> {
            whenever(it.configuration).thenReturn(
                Configuration().apply {
                    this.uiMode =
                        Configuration.UI_MODE_NIGHT_YES
                }
            )
        }
        val mockContext = mock<Context> {
            whenever(it.resources).thenReturn(mockResources)
        }
        whenever(mockSeekBar.context).thenReturn(mockContext)
        val expectedInactiveTrackWireframe = MobileSegment.Wireframe.ShapeWireframe(
            id = fakeInactiveTrackId,
            x = fakeExpectedInactiveTrackXPos,
            y = fakeExpectedInactiveTrackYPos,
            width = fakeExpectedInactiveTrackWidth,
            height = fakeExpectedInactiveTrackHeight,
            shapeStyle = MobileSegment.ShapeStyle(
                backgroundColor = fakeDefaultNightNotActiveHtmlColor,
                opacity = fakeViewAlpha
            )
        )

        // When
        val mappedWireframes = testedSeekBarWireframeMapper.map(mockSeekBar, fakeMappingContext)

        // Then
        assertThat(mappedWireframes).isEqualTo(listOf(expectedInactiveTrackWireframe))
    }
}
