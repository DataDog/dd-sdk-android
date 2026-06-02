/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.trace.internal.ddsketch

import com.datadog.android.utils.forge.Configurator
import fr.xgouchet.elmyr.annotation.DoubleForgery
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.data.Percentage.withPercentage
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import kotlin.math.exp

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class DDSketchTest {

    // region Empty sketch

    @Test
    fun `M be empty W isEmpty() {no values added}`() {
        // When / Then
        assertThat(DDSketch(RELATIVE_ACCURACY, MAX_BINS).isEmpty()).isTrue()
    }

    @Test
    fun `M return zero W getCount() {no values added}`() {
        // When / Then
        assertThat(DDSketch(RELATIVE_ACCURACY, MAX_BINS).getCount()).isZero()
    }

    @Test
    fun `M return zero W getSum() {no values added}`() {
        // When / Then
        assertThat(DDSketch(RELATIVE_ACCURACY, MAX_BINS).getSum()).isZero()
    }

    // endregion

    // region Add / count

    @Test
    fun `M count zeros W add() {value is 0}`(
        @IntForgery(min = 1, max = 50) fakeCount: Int
    ) {
        // Given
        val sketch = DDSketch(RELATIVE_ACCURACY, MAX_BINS)

        // When
        repeat(fakeCount) { sketch.add(0.0) }

        // Then: zeros go to zeroCount, not a store — total count still accumulates
        assertThat(sketch.getCount()).isEqualTo(fakeCount.toDouble())
        assertThat(sketch.isEmpty()).isFalse()
    }

    @Test
    fun `M not be empty W isEmpty() {after adding a value}`(
        @DoubleForgery(min = 1.0, max = 1000.0) fakeValue: Double
    ) {
        // Given
        val sketch = DDSketch(RELATIVE_ACCURACY, MAX_BINS)

        // When
        sketch.add(fakeValue)

        // Then
        assertThat(sketch.isEmpty()).isFalse()
    }

    @Test
    fun `M increment count W getCount() {multiple values}`(
        @DoubleForgery(min = 1.0, max = 1000.0) fakeValue: Double,
        @IntForgery(min = 1, max = 100) fakeCount: Int
    ) {
        // Given
        val sketch = DDSketch(RELATIVE_ACCURACY, MAX_BINS)

        // When
        repeat(fakeCount) { sketch.add(fakeValue) }

        // Then
        assertThat(sketch.getCount()).isEqualTo(fakeCount.toDouble())
    }

    @Test
    fun `M bound memory usage W add() {value range exceeds maxNumBins}`() {
        // Given
        val sketch = DDSketch(0.01, 32)

        // When
        for (i in 0 until 10_000) {
            sketch.add(exp(i.toDouble() / 100.0))
        }

        // Then
        assertThat(sketch.isEmpty()).isFalse()
        assertThat(sketch.getCount()).isEqualTo(10_000.0)
    }

    // endregion

    // region Sum

    @Test
    fun `M return sum of added values W getSum()`(
        @DoubleForgery(min = 1.0, max = 1000.0) fakeValue: Double,
        @IntForgery(min = 1, max = 50) fakeCount: Int
    ) {
        // Given
        val sketch = DDSketch(RELATIVE_ACCURACY, MAX_BINS)
        val expectedSum = fakeValue * fakeCount

        // When
        repeat(fakeCount) { sketch.add(fakeValue) }

        // Then
        assertThat(sketch.getSum()).isCloseTo(expectedSum, withPercentage(RELATIVE_ACCURACY * 100))
    }

    @Test
    fun `M return negative sum W getSum() {only negative values}`(
        @DoubleForgery(min = 1.0, max = 500.0) fakeValue: Double,
        @IntForgery(min = 1, max = 50) fakeCount: Int
    ) {
        // Given
        val sketch = DDSketch(RELATIVE_ACCURACY, MAX_BINS)
        val expectedSum = -fakeValue * fakeCount

        // When
        repeat(fakeCount) { sketch.add(-fakeValue) }

        // Then
        assertThat(sketch.getSum()).isCloseTo(expectedSum, withPercentage(RELATIVE_ACCURACY * 100))
    }

    // endregion

    // region Serialization

    @Test
    fun `M match actual serialized byte count W serializedSize()`(
        @DoubleForgery(min = -1000.0, max = 1000.0) fakeValue: Double,
        @IntForgery(min = 1, max = 50) fakeCount: Int
    ) {
        // Given
        val sketch = DDSketch(RELATIVE_ACCURACY, MAX_BINS)
        repeat(fakeCount) { sketch.add(fakeValue) }

        // When / Then
        assertThat(sketch.serialize().size).isEqualTo(sketch.serializedSize())
    }

    @Test
    fun `M write headers and delegates in order W writeTo()`() {
        // Given
        val mockMapping = mock<LogarithmicMapping>()
        val mockPositiveStore = mock<CollapsingLowestDenseStore>()
        val mockNegativeStore = mock<CollapsingLowestDenseStore>()
        val mockSerializer = mock<DDSketchSerializer>()
        whenever(mockMapping.serializedSize()).thenReturn(9)
        whenever(mockPositiveStore.serializedSize()).thenReturn(10)
        whenever(mockNegativeStore.serializedSize()).thenReturn(10)
        val sketch = DDSketch(mockMapping, mockNegativeStore, mockPositiveStore)

        // When
        sketch.writeTo(mockSerializer)

        // Then: headers written before each delegate, fields 1=mapping, 2=positive, 3=negative, 4=zeroCount
        inOrder(mockSerializer, mockMapping, mockPositiveStore, mockNegativeStore) {
            verify(mockSerializer).writeHeader(1, 9)
            verify(mockMapping).writeTo(mockSerializer)
            verify(mockSerializer).writeHeader(2, 10)
            verify(mockPositiveStore).writeTo(mockSerializer)
            verify(mockSerializer).writeHeader(3, 10)
            verify(mockNegativeStore).writeTo(mockSerializer)
            verify(mockSerializer).writeDouble(4, 0.0)
        }
    }

    // endregion

    companion object {
        private const val RELATIVE_ACCURACY = 0.01
        private const val MAX_BINS = 10_000
    }
}
