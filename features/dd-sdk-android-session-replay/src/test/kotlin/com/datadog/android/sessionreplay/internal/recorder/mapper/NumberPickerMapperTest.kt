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
internal class NumberPickerMapperTest : BaseNumberPickerMapperTest() {

    override fun provideTestInstance(): NumberPickerMapper {
        return NumberPickerMapper(
            mockViewIdentifierResolver,
            mockColorStringFormatter,
            mockViewBoundsResolver,
            mockDrawableToColorMapper
        )
    }

    @Test
    fun `M map the NumberPicker to a list of wireframes W map()`() {
        // Given
        val expectedSelectedLabelValue = fakeValue.toString()
        val expectedPrevLabelValue = (fakeValue - 1).toString()
        val expectedNextLabelValue = (fakeValue + 1).toString()
        val expectedPrevLabelWireframe = fakePrevLabelWireframe()
            .copy(text = expectedPrevLabelValue)
        val expectedTopDividerWireframe = fakeTopDividerWireframe()
        val expectedSelectedLabelWireframe = fakeSelectedLabelWireframe()
            .copy(text = expectedSelectedLabelValue)
        val expectedBottomDividerWireframe = fakeBottomDividerWireframe()
        val expectedNextLabelWireframe = fakeNextLabelWireframe()
            .copy(text = expectedNextLabelValue)

        // When
        val wireframes = testedNumberPickerMapper.map(mockNumberPicker, fakeMappingContext, mockAsyncJobStatusCallback)

        // Then
        assertThat(wireframes).isEqualTo(
            listOf(
                expectedPrevLabelWireframe,
                expectedTopDividerWireframe,
                expectedSelectedLabelWireframe,
                expectedBottomDividerWireframe,
                expectedNextLabelWireframe
            )
        )
    }

    @Test
    fun `M map the NumberPicker to a list of wireframes W map(){ value equals max }`() {
        // Given
        fakeValue = fakeMaxValue
        whenever(mockNumberPicker.value).thenReturn(fakeValue)
        val expectedSelectedLabelValue = fakeValue.toString()
        val expectedPrevLabelValue = (fakeValue - 1).toString()
        val expectedNextLabelValue = fakeMinValue.toString()
        val expectedPrevLabelWireframe = fakePrevLabelWireframe()
            .copy(text = expectedPrevLabelValue)
        val expectedTopDividerWireframe = fakeTopDividerWireframe()
        val expectedSelectedLabelWireframe = fakeSelectedLabelWireframe()
            .copy(text = expectedSelectedLabelValue)
        val expectedBottomDividerWireframe = fakeBottomDividerWireframe()
        val expectedNextLabelWireframe = fakeNextLabelWireframe()
            .copy(text = expectedNextLabelValue)

        // When
        val wireframes = testedNumberPickerMapper.map(mockNumberPicker, fakeMappingContext, mockAsyncJobStatusCallback)

        // Then
        assertThat(wireframes).isEqualTo(
            listOf(
                expectedPrevLabelWireframe,
                expectedTopDividerWireframe,
                expectedSelectedLabelWireframe,
                expectedBottomDividerWireframe,
                expectedNextLabelWireframe
            )
        )
    }

    @Test
    fun `M map the NumberPicker to a list of wireframes W map(){ value equals min }`() {
        // Given
        fakeValue = fakeMinValue
        whenever(mockNumberPicker.value).thenReturn(fakeValue)
        val expectedSelectedLabelValue = fakeValue.toString()
        val expectedPrevLabelValue = fakeMaxValue.toString()
        val expectedNextLabelValue = (fakeValue + 1).toString()
        val expectedPrevLabelWireframe = fakePrevLabelWireframe()
            .copy(text = expectedPrevLabelValue)
        val expectedTopDividerWireframe = fakeTopDividerWireframe()
        val expectedSelectedLabelWireframe = fakeSelectedLabelWireframe()
            .copy(text = expectedSelectedLabelValue)
        val expectedBottomDividerWireframe = fakeBottomDividerWireframe()
        val expectedNextLabelWireframe = fakeNextLabelWireframe()
            .copy(text = expectedNextLabelValue)

        // When
        val wireframes = testedNumberPickerMapper.map(mockNumberPicker, fakeMappingContext, mockAsyncJobStatusCallback)

        // Then
        assertThat(wireframes).isEqualTo(
            listOf(
                expectedPrevLabelWireframe,
                expectedTopDividerWireframe,
                expectedSelectedLabelWireframe,
                expectedBottomDividerWireframe,
                expectedNextLabelWireframe
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
        val normalizedIndex = fakeValue - fakeMinValue
        val expectedSelectedLabelValue = fakeDisplayedValues[normalizedIndex]
        val expectedPrevLabelValue = fakeDisplayedValues[normalizedIndex - 1]
        val expectedNextLabelValue = fakeDisplayedValues[normalizedIndex + 1]
        val expectedPrevLabelWireframe = fakePrevLabelWireframe()
            .copy(text = expectedPrevLabelValue)
        val expectedTopDividerWireframe = fakeTopDividerWireframe()
        val expectedSelectedLabelWireframe = fakeSelectedLabelWireframe()
            .copy(text = expectedSelectedLabelValue)
        val expectedBottomDividerWireframe = fakeBottomDividerWireframe()
        val expectedNextLabelWireframe = fakeNextLabelWireframe()
            .copy(text = expectedNextLabelValue)

        // When
        val wireframes = testedNumberPickerMapper.map(mockNumberPicker, fakeMappingContext, mockAsyncJobStatusCallback)

        // Then
        assertThat(wireframes).isEqualTo(
            listOf(
                expectedPrevLabelWireframe,
                expectedTopDividerWireframe,
                expectedSelectedLabelWireframe,
                expectedBottomDividerWireframe,
                expectedNextLabelWireframe
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
        val normalizedIndex = fakeValue - fakeMinValue
        val expectedSelectedLabelValue = fakeDisplayedValues[normalizedIndex]
        val expectedPrevLabelValue = fakeDisplayedValues[normalizedIndex - 1]
        val expectedNextLabelValue = fakeDisplayedValues[0]
        val expectedPrevLabelWireframe = fakePrevLabelWireframe().copy(text = expectedPrevLabelValue)
        val expectedTopDividerWireframe = fakeTopDividerWireframe()
        val expectedSelectedLabelWireframe = fakeSelectedLabelWireframe()
            .copy(text = expectedSelectedLabelValue)
        val expectedBottomDividerWireframe = fakeBottomDividerWireframe()
        val expectedNextLabelWireframe = fakeNextLabelWireframe()
            .copy(text = expectedNextLabelValue)

        // When
        val wireframes = testedNumberPickerMapper.map(mockNumberPicker, fakeMappingContext, mockAsyncJobStatusCallback)

        // Then
        assertThat(wireframes).isEqualTo(
            listOf(
                expectedPrevLabelWireframe,
                expectedTopDividerWireframe,
                expectedSelectedLabelWireframe,
                expectedBottomDividerWireframe,
                expectedNextLabelWireframe
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
        val normalizedIndex = fakeValue - fakeMinValue
        val expectedSelectedLabelValue = fakeDisplayedValues[normalizedIndex]
        val expectedPrevLabelValue = fakeDisplayedValues[fakeDisplayedValues.size - 1]
        val expectedNextLabelValue = fakeDisplayedValues[normalizedIndex + 1]
        val expectedPrevLabelWireframe = fakePrevLabelWireframe()
            .copy(text = expectedPrevLabelValue)
        val expectedTopDividerWireframe = fakeTopDividerWireframe()
        val expectedSelectedLabelWireframe = fakeSelectedLabelWireframe()
            .copy(text = expectedSelectedLabelValue)
        val expectedBottomDividerWireframe = fakeBottomDividerWireframe()
        val expectedNextLabelWireframe = fakeNextLabelWireframe()
            .copy(text = expectedNextLabelValue)

        // When
        val wireframes = testedNumberPickerMapper.map(mockNumberPicker, fakeMappingContext, mockAsyncJobStatusCallback)

        // Then
        assertThat(wireframes).isEqualTo(
            listOf(
                expectedPrevLabelWireframe,
                expectedTopDividerWireframe,
                expectedSelectedLabelWireframe,
                expectedBottomDividerWireframe,
                expectedNextLabelWireframe
            )
        )
    }

    @Test
    fun `M return empty list W map { prevLabelId null }`() {
        // Given
        whenever(
            mockViewIdentifierResolver
                .resolveChildUniqueIdentifier(
                    mockNumberPicker,
                    BasePickerMapper.PREV_INDEX_KEY_NAME
                )
        )
            .thenReturn(null)

        // When
        val wireframes = testedNumberPickerMapper.map(mockNumberPicker, fakeMappingContext, mockAsyncJobStatusCallback)

        // Then
        assertThat(wireframes).isEmpty()
    }

    @Test
    fun `M return empty list W map { nextLabelId null }`() {
        // Given
        whenever(
            mockViewIdentifierResolver
                .resolveChildUniqueIdentifier(
                    mockNumberPicker,
                    BasePickerMapper.NEXT_INDEX_KEY_NAME
                )
        )
            .thenReturn(null)

        // When
        val wireframes = testedNumberPickerMapper.map(mockNumberPicker, fakeMappingContext, mockAsyncJobStatusCallback)

        // Then
        assertThat(wireframes).isEmpty()
    }
}
