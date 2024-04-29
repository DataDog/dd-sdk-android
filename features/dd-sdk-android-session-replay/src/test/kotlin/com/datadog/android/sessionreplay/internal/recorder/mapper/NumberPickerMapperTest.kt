/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.mapper

import com.datadog.android.sessionreplay.SessionReplayPrivacy
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

    // region privacy = ALLOW

    @Test
    fun `M return a list of wireframes W map() {privacy=ALLOW}`() {
        // Given
        fakeMappingContext = fakeMappingContext.copy(privacy = SessionReplayPrivacy.ALLOW)
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
    fun `M return a list of wireframes W map() {privacy=ALLOW, value=max }`() {
        // Given
        fakeMappingContext = fakeMappingContext.copy(privacy = SessionReplayPrivacy.ALLOW)
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
    fun `M return a list of wireframes W map() {privacy=ALLOW, value=min }`() {
        // Given
        fakeMappingContext = fakeMappingContext.copy(privacy = SessionReplayPrivacy.ALLOW)
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
    fun `M return a list of wireframes W map() {privacy=ALLOW, with displayedValues}`(
        forge: Forge
    ) {
        // Given
        fakeMappingContext = fakeMappingContext.copy(privacy = SessionReplayPrivacy.ALLOW)
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
    fun `M return a list of wireframes W map() {privacy=ALLOW, with displayedValues, value=max}`(
        forge: Forge
    ) {
        // Given
        fakeMappingContext = fakeMappingContext.copy(privacy = SessionReplayPrivacy.ALLOW)
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
    fun `M return a list of wireframes W map() {privacy=ALLOW, with displayedValues, value=min}`(
        forge: Forge
    ) {
        // Given
        fakeMappingContext = fakeMappingContext.copy(privacy = SessionReplayPrivacy.ALLOW)
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
    fun `M return empty list W map {privacy=ALLOW, prevLabelId=null}`() {
        // Given
        fakeMappingContext = fakeMappingContext.copy(privacy = SessionReplayPrivacy.ALLOW)
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
    fun `M return empty list W map {privacy=ALLOW, nextLabelId=null}`() {
        // Given
        fakeMappingContext = fakeMappingContext.copy(privacy = SessionReplayPrivacy.ALLOW)
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

    // endregion

    // region privacy = MASK

    @Test
    fun `M return a list of wireframes W map() {privacy=MASK}`() {
        // Given
        fakeMappingContext = fakeMappingContext.copy(privacy = SessionReplayPrivacy.MASK)
        val expectedSelectedLabelValue = "xxx"
        val expectedTopDividerWireframe = fakeTopDividerWireframe()
        val expectedSelectedLabelWireframe = fakeSelectedLabelWireframe()
            .copy(text = expectedSelectedLabelValue)
        val expectedBottomDividerWireframe = fakeBottomDividerWireframe()
        // When
        val wireframes = testedNumberPickerMapper.map(mockNumberPicker, fakeMappingContext, mockAsyncJobStatusCallback)

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
    fun `M return a list of wireframes W map() {privacy=MASK, value=max}`() {
        // Given
        fakeMappingContext = fakeMappingContext.copy(privacy = SessionReplayPrivacy.MASK)
        fakeValue = fakeMaxValue
        whenever(mockNumberPicker.value).thenReturn(fakeValue)
        val expectedSelectedLabelValue = "xxx"
        val expectedTopDividerWireframe = fakeTopDividerWireframe()
        val expectedSelectedLabelWireframe = fakeSelectedLabelWireframe()
            .copy(text = expectedSelectedLabelValue)
        val expectedBottomDividerWireframe = fakeBottomDividerWireframe()
        // When
        val wireframes = testedNumberPickerMapper.map(mockNumberPicker, fakeMappingContext, mockAsyncJobStatusCallback)

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
    fun `M return a list of wireframes W map() {privacy=MASK, value=min }`() {
        // Given
        fakeMappingContext = fakeMappingContext.copy(privacy = SessionReplayPrivacy.MASK)
        fakeValue = fakeMinValue
        whenever(mockNumberPicker.value).thenReturn(fakeValue)
        val expectedSelectedLabelValue = "xxx"
        val expectedTopDividerWireframe = fakeTopDividerWireframe()
        val expectedSelectedLabelWireframe = fakeSelectedLabelWireframe()
            .copy(text = expectedSelectedLabelValue)
        val expectedBottomDividerWireframe = fakeBottomDividerWireframe()

        // When
        val wireframes = testedNumberPickerMapper.map(mockNumberPicker, fakeMappingContext, mockAsyncJobStatusCallback)

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
    fun `M return a list of wireframes W map() {privacy=MASK, with displayedValues}`(
        forge: Forge
    ) {
        // Given
        fakeMappingContext = fakeMappingContext.copy(privacy = SessionReplayPrivacy.MASK)
        val fakeDisplayedValues = forge.aList(size = (fakeMaxValue - fakeMinValue + 1)) { aString() }
            .toTypedArray()
        whenever(mockNumberPicker.displayedValues).thenReturn(fakeDisplayedValues)
        val expectedSelectedLabelValue = "xxx"
        val expectedTopDividerWireframe = fakeTopDividerWireframe()
        val expectedSelectedLabelWireframe = fakeSelectedLabelWireframe()
            .copy(text = expectedSelectedLabelValue)
        val expectedBottomDividerWireframe = fakeBottomDividerWireframe()
        // When
        val wireframes = testedNumberPickerMapper.map(mockNumberPicker, fakeMappingContext, mockAsyncJobStatusCallback)

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
    fun `M return a list of wireframes W map() {privacy=MASK, with displayedValues, value=max}`(
        forge: Forge
    ) {
        // Given
        fakeMappingContext = fakeMappingContext.copy(privacy = SessionReplayPrivacy.MASK)
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
        val wireframes = testedNumberPickerMapper.map(mockNumberPicker, fakeMappingContext, mockAsyncJobStatusCallback)

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
    fun `M return a list of wireframes W map() {privacy=MASK, with displayedValues, value=min}`(
        forge: Forge
    ) {
        // Given
        fakeMappingContext = fakeMappingContext.copy(privacy = SessionReplayPrivacy.MASK)
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
        val wireframes = testedNumberPickerMapper.map(mockNumberPicker, fakeMappingContext, mockAsyncJobStatusCallback)

        // Then
        assertThat(wireframes).isEqualTo(
            listOf(
                expectedTopDividerWireframe,
                expectedSelectedLabelWireframe,
                expectedBottomDividerWireframe
            )
        )
    }

    // endregion

    // region privacy = MASK_USER_INPUT

    @Test
    fun `M return a list of wireframes W map() {privacy=MASK_USER_INPUT}`() {
        // Given
        fakeMappingContext = fakeMappingContext.copy(privacy = SessionReplayPrivacy.MASK_USER_INPUT)
        val expectedSelectedLabelValue = "xxx"
        val expectedTopDividerWireframe = fakeTopDividerWireframe()
        val expectedSelectedLabelWireframe = fakeSelectedLabelWireframe()
            .copy(text = expectedSelectedLabelValue)
        val expectedBottomDividerWireframe = fakeBottomDividerWireframe()
        // When
        val wireframes = testedNumberPickerMapper.map(mockNumberPicker, fakeMappingContext, mockAsyncJobStatusCallback)

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
    fun `M return a list of wireframes W map() {privacy=MASK_USER_INPUT, value=max}`() {
        // Given
        fakeMappingContext = fakeMappingContext.copy(privacy = SessionReplayPrivacy.MASK_USER_INPUT)
        fakeValue = fakeMaxValue
        whenever(mockNumberPicker.value).thenReturn(fakeValue)
        val expectedSelectedLabelValue = "xxx"
        val expectedTopDividerWireframe = fakeTopDividerWireframe()
        val expectedSelectedLabelWireframe = fakeSelectedLabelWireframe()
            .copy(text = expectedSelectedLabelValue)
        val expectedBottomDividerWireframe = fakeBottomDividerWireframe()
        // When
        val wireframes = testedNumberPickerMapper.map(mockNumberPicker, fakeMappingContext, mockAsyncJobStatusCallback)

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
    fun `M return a list of wireframes W map() {privacy=MASK_USER_INPUT, value=min }`() {
        // Given
        fakeMappingContext = fakeMappingContext.copy(privacy = SessionReplayPrivacy.MASK_USER_INPUT)
        fakeValue = fakeMinValue
        whenever(mockNumberPicker.value).thenReturn(fakeValue)
        val expectedSelectedLabelValue = "xxx"
        val expectedTopDividerWireframe = fakeTopDividerWireframe()
        val expectedSelectedLabelWireframe = fakeSelectedLabelWireframe()
            .copy(text = expectedSelectedLabelValue)
        val expectedBottomDividerWireframe = fakeBottomDividerWireframe()

        // When
        val wireframes = testedNumberPickerMapper.map(mockNumberPicker, fakeMappingContext, mockAsyncJobStatusCallback)

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
    fun `M return a list of wireframes W map() {privacy=MASK_USER_INPUT, with displayedValues}`(
        forge: Forge
    ) {
        // Given
        fakeMappingContext = fakeMappingContext.copy(privacy = SessionReplayPrivacy.MASK_USER_INPUT)
        val fakeDisplayedValues = forge.aList(size = (fakeMaxValue - fakeMinValue + 1)) { aString() }
            .toTypedArray()
        whenever(mockNumberPicker.displayedValues).thenReturn(fakeDisplayedValues)
        val expectedSelectedLabelValue = "xxx"
        val expectedTopDividerWireframe = fakeTopDividerWireframe()
        val expectedSelectedLabelWireframe = fakeSelectedLabelWireframe()
            .copy(text = expectedSelectedLabelValue)
        val expectedBottomDividerWireframe = fakeBottomDividerWireframe()
        // When
        val wireframes = testedNumberPickerMapper.map(mockNumberPicker, fakeMappingContext, mockAsyncJobStatusCallback)

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
    fun `M return a list of wireframes W map() {privacy=MASK_USER_INPUT, with displayedValues, value=max}`(
        forge: Forge
    ) {
        // Given
        fakeMappingContext = fakeMappingContext.copy(privacy = SessionReplayPrivacy.MASK_USER_INPUT)
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
        val wireframes = testedNumberPickerMapper.map(mockNumberPicker, fakeMappingContext, mockAsyncJobStatusCallback)

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
    fun `M return a list of wireframes W map() {privacy=MASK_USER_INPUT, with displayedValues, value=min}`(
        forge: Forge
    ) {
        // Given
        fakeMappingContext = fakeMappingContext.copy(privacy = SessionReplayPrivacy.MASK_USER_INPUT)
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
        val wireframes = testedNumberPickerMapper.map(mockNumberPicker, fakeMappingContext, mockAsyncJobStatusCallback)

        // Then
        assertThat(wireframes).isEqualTo(
            listOf(
                expectedTopDividerWireframe,
                expectedSelectedLabelWireframe,
                expectedBottomDividerWireframe
            )
        )
    }

    // endregion
}
