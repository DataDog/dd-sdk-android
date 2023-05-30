/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.mapper

import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.android.sessionreplay.model.MobileSegment
import com.datadog.android.sessionreplay.utils.StringUtils
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(ForgeConfigurator::class)
internal class MaskAllSwitchCompatMapperTest : BaseSwitchCompatMapperTest() {

    override fun setupTestedMapper(): SwitchCompatMapper {
        return MaskAllSwitchCompatMapper(
            mockTextWireframeMapper,
            uniqueIdentifierGenerator = mockuniqueIdentifierGenerator,
            viewUtils = mockViewUtils
        )
    }

    @Test
    fun `M resolve the switch as wireframes W map() { checked }`() {
        // Given
        whenever(mockSwitch.isChecked).thenReturn(true)
        val expectedColor = StringUtils.formatColorAndAlphaAsHexa(
            fakeCurrentTextColor,
            OPAQUE_ALPHA_VALUE
        )
        val expectedThumbWidth =
            normalizedThumbWidth - normalizedThumbRightPadding - normalizedThumbLeftPadding
        val expectedTrackWidth = expectedThumbWidth * 2
        val expectedTrackHeight =
            normalizedTrackHeight - normalizedThumbRightPadding - normalizedThumbLeftPadding
        val expectedTrackWireframe = MobileSegment.Wireframe.ShapeWireframe(
            id = fakeTrackIdentifier,
            x = fakeViewGlobalBounds.x + fakeViewGlobalBounds.width - expectedTrackWidth,
            y = fakeViewGlobalBounds.y + (fakeViewGlobalBounds.height - expectedTrackHeight) / 2,
            width = expectedTrackWidth,
            height = expectedTrackHeight,
            border = null,
            shapeStyle = MobileSegment.ShapeStyle(
                backgroundColor = expectedColor,
                mockSwitch.alpha
            )
        )

        // When
        val resolvedWireframes = testedSwitchCompatMapper.map(
            mockSwitch,
            fakeMappingContext
        )

        // Then
        assertThat(resolvedWireframes).isEqualTo(fakeTextWireframes + expectedTrackWireframe)
    }

    @Test
    fun `M resolve the switch as wireframes W map() { not checked }`() {
        // Given
        whenever(mockSwitch.isChecked).thenReturn(false)
        val expectedColor = StringUtils.formatColorAndAlphaAsHexa(
            fakeCurrentTextColor,
            OPAQUE_ALPHA_VALUE
        )
        val expectedThumbWidth =
            normalizedThumbWidth - normalizedThumbRightPadding - normalizedThumbLeftPadding
        val expectedTrackWidth = expectedThumbWidth * 2
        val expectedTrackHeight =
            normalizedTrackHeight - normalizedThumbRightPadding - normalizedThumbLeftPadding
        val expectedTrackWireframe = MobileSegment.Wireframe.ShapeWireframe(
            id = fakeTrackIdentifier,
            x = fakeViewGlobalBounds.x + fakeViewGlobalBounds.width - expectedTrackWidth,
            y = fakeViewGlobalBounds.y + (fakeViewGlobalBounds.height - expectedTrackHeight) / 2,
            width = expectedTrackWidth,
            height = expectedTrackHeight,
            border = null,
            shapeStyle = MobileSegment.ShapeStyle(
                backgroundColor = expectedColor,
                mockSwitch.alpha
            )
        )

        // When
        val resolvedWireframes = testedSwitchCompatMapper.map(
            mockSwitch,
            fakeMappingContext
        )

        // Then
        assertThat(resolvedWireframes).isEqualTo(fakeTextWireframes + expectedTrackWireframe)
    }
}
