/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.mapper

import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import fr.xgouchet.elmyr.Forge
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
internal class MaskNumberPickerMapperTest : BaseNumberPickerMapperTest() {

    override fun provideTestInstance(): BasePickerMapper {
        return MaskNumberPickerMapper(
            mockStringUtils,
            mockViewUtils,
            mockUniqueIdentifierGenerator
        )
    }

    @Test
    fun `M map the NumberPicker to a list of wireframes W map()`() {
        // Given
        val expectedSelectedLabelValue = "xxx"
        val expectedTopDividerWireframe = fakeTopDividerWireframe()
        val expectedSelectedLabelWireframe = fakeSelectedLabelWireframe()
            .copy(text = expectedSelectedLabelValue)
        val expectedBottomDividerWireframe = fakeBottomDividerWireframe()
        // When
        val wireframes = testedNumberPickerMapper.map(mockNumberPicker, fakeMappingContext)

        // Then
        assertThat(wireframes).isEqualTo(
            listOf(
                expectedTopDividerWireframe,
                expectedSelectedLabelWireframe,
                expectedBottomDividerWireframe
            )
        )
    }

    @Test
    fun `M map the NumberPicker to a list of wireframes W map(){ value equals max }`() {
        // Given
        fakeValue = fakeMaxValue
        whenever(mockNumberPicker.value).thenReturn(fakeValue)
        val expectedSelectedLabelValue = "xxx"
        val expectedTopDividerWireframe = fakeTopDividerWireframe()
        val expectedSelectedLabelWireframe = fakeSelectedLabelWireframe()
            .copy(text = expectedSelectedLabelValue)
        val expectedBottomDividerWireframe = fakeBottomDividerWireframe()
        // When
        val wireframes = testedNumberPickerMapper.map(mockNumberPicker, fakeMappingContext)

        // Then
        assertThat(wireframes).isEqualTo(
            listOf(
                expectedTopDividerWireframe,
                expectedSelectedLabelWireframe,
                expectedBottomDividerWireframe
            )
        )
    }

    @Test
    fun `M map the NumberPicker to a list of wireframes W map(){ value equals min }`() {
        // Given
        fakeValue = fakeMinValue
        whenever(mockNumberPicker.value).thenReturn(fakeValue)
        val expectedSelectedLabelValue = "xxx"
        val expectedTopDividerWireframe = fakeTopDividerWireframe()
        val expectedSelectedLabelWireframe = fakeSelectedLabelWireframe()
            .copy(text = expectedSelectedLabelValue)
        val expectedBottomDividerWireframe = fakeBottomDividerWireframe()

        // When
        val wireframes = testedNumberPickerMapper.map(mockNumberPicker, fakeMappingContext)

        // Then
        assertThat(wireframes).isEqualTo(
            listOf(
                expectedTopDividerWireframe,
                expectedSelectedLabelWireframe,
                expectedBottomDividerWireframe
            )
        )
    }

    @Test
    fun `M map the NumberPicker to a list of wireframes W map() { displayedValues}`(
        forge: Forge
    ) {
        // Given
        val fakeDisplayedValues = forge.aList(size = (fakeMaxValue - fakeMinValue + 1)) { aString() }
            .toTypedArray()
        whenever(mockNumberPicker.displayedValues).thenReturn(fakeDisplayedValues)
        val expectedSelectedLabelValue = "xxx"
        val expectedTopDividerWireframe = fakeTopDividerWireframe()
        val expectedSelectedLabelWireframe = fakeSelectedLabelWireframe()
            .copy(text = expectedSelectedLabelValue)
        val expectedBottomDividerWireframe = fakeBottomDividerWireframe()
        // When
        val wireframes = testedNumberPickerMapper.map(mockNumberPicker, fakeMappingContext)

        // Then
        assertThat(wireframes).isEqualTo(
            listOf(
                expectedTopDividerWireframe,
                expectedSelectedLabelWireframe,
                expectedBottomDividerWireframe
            )
        )
    }

    @Test
    fun `M map the NumberPicker to a list of wireframes W map(){ displayedValues, max value }`(
        forge: Forge
    ) {
        // Given
        fakeValue = fakeMaxValue
        whenever(mockNumberPicker.value).thenReturn(fakeValue)
        val fakeDisplayedValues = forge.aList(size = (fakeMaxValue - fakeMinValue + 1)) { aString() }
            .toTypedArray()
        whenever(mockNumberPicker.displayedValues).thenReturn(fakeDisplayedValues)
        val expectedSelectedLabelValue = "xxx"
        val expectedTopDividerWireframe = fakeTopDividerWireframe()
        val expectedSelectedLabelWireframe = fakeSelectedLabelWireframe()
            .copy(text = expectedSelectedLabelValue)
        val expectedBottomDividerWireframe = fakeBottomDividerWireframe()
        // When
        val wireframes = testedNumberPickerMapper.map(mockNumberPicker, fakeMappingContext)

        // Then
        assertThat(wireframes).isEqualTo(
            listOf(
                expectedTopDividerWireframe,
                expectedSelectedLabelWireframe,
                expectedBottomDividerWireframe
            )
        )
    }

    @Test
    fun `M map the NumberPicker to a list of wireframes W map(){ displayedValues, min value }`(
        forge: Forge
    ) {
        // Given
        fakeValue = fakeMinValue
        whenever(mockNumberPicker.value).thenReturn(fakeValue)
        val fakeDisplayedValues = forge.aList(size = (fakeMaxValue - fakeMinValue + 1)) { aString() }
            .toTypedArray()
        whenever(mockNumberPicker.displayedValues).thenReturn(fakeDisplayedValues)
        val expectedSelectedLabelValue = "xxx"
        val expectedTopDividerWireframe = fakeTopDividerWireframe()
        val expectedSelectedLabelWireframe = fakeSelectedLabelWireframe()
            .copy(text = expectedSelectedLabelValue)
        val expectedBottomDividerWireframe = fakeBottomDividerWireframe()

        // When
        val wireframes = testedNumberPickerMapper.map(mockNumberPicker, fakeMappingContext)

        // Then
        assertThat(wireframes).isEqualTo(
            listOf(
                expectedTopDividerWireframe,
                expectedSelectedLabelWireframe,
                expectedBottomDividerWireframe
            )
        )
    }
}
