/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.trace.internal.ddsketch

import com.datadog.android.utils.forge.Configurator
import com.datadoghq.sketch.ddsketch.mapping.IndexMappingProtoBinding
import fr.xgouchet.elmyr.annotation.DoubleForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.assertj.core.data.Percentage.withPercentage
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness
import kotlin.math.sqrt
import com.datadoghq.sketch.ddsketch.mapping.LogarithmicMapping as RefLogarithmicMapping

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class LogarithmicMappingTest {

    // region Accuracy

    @Test
    fun `M round-trip within relativeAccuracy W value(getIndexFor(v))`(
        @DoubleForgery(min = 0.001, max = 0.1) fakeRelativeAccuracy: Double
    ) {
        // Given
        val mapping = LogarithmicMapping(fakeRelativeAccuracy)
        val max = mapping.maxIndexedValue
        val testValues = generateSequence(mapping.minIndexedValue) { it * STEP_MULTIPLIER }
            .takeWhile { it < max }
            .toList() + max

        // When
        val roundTripped = testValues.map { mapping.value(mapping.getIndexFor(it)) }

        // Then
        testValues.zip(roundTripped).forEach { (original, mapped) ->
            assertThat(mapped).isCloseTo(original, withPercentage((fakeRelativeAccuracy + FLOATING_POINT_ERROR) * 100))
        }
    }

    // endregion

    // region Monotonicity

    @Test
    fun `M be monotonically non-decreasing W getIndexFor() {increasing values}`(
        @DoubleForgery(min = 1.0, max = 1000.0) fakeStart: Double
    ) {
        // Given
        val mapping = LogarithmicMapping(0.01)
        val values = generateSequence(fakeStart) { it * STEP_MULTIPLIER }.take(20).toList()

        // Then
        for (i in 0 until values.size - 1) {
            assertThat(mapping.getIndexFor(values[i])).isLessThanOrEqualTo(mapping.getIndexFor(values[i + 1]))
        }
    }

    // endregion

    // region Lower bound invariant

    @Test
    fun `M satisfy lowerBound in value(i-1) to value(i) W lowerBound() {various indices}`() {
        // Given
        val mapping = LogarithmicMapping(0.01)
        val testIndices = listOf(2, 10, 25, 100, 10_000)

        // Then
        for (i in testIndices) {
            val lowerBound = mapping.lowerBound(i)
            val previous = mapping.value(i - 1)
            val next = mapping.value(i)
            assertThat(lowerBound).isGreaterThanOrEqualTo(previous)
            assertThat(next).isGreaterThanOrEqualTo(lowerBound)
        }
    }

    @Test
    fun `M have bin boundaries within relativeAccuracy of value W lowerBound() {various values}`() {
        // Given
        val accuracy = 0.01
        val mapping = LogarithmicMapping(accuracy)
        val testValues = listOf(0.1, 1.0, 10.0, 100.0, 1_000.0, 50_000.0, 1_000_000.0)

        // When / Then
        for (value in testValues) {
            val idx = mapping.getIndexFor(value)
            val lower = mapping.lowerBound(idx)
            val upper = mapping.lowerBound(idx + 1)
            assertThat(lower).isLessThanOrEqualTo(value * (1 + accuracy))
            assertThat(upper).isGreaterThanOrEqualTo(value * (1 - accuracy))
        }
    }

    // endregion

    // region Clamping

    @Test
    fun `M clamp to MIN_SAFE_RELATIVE_ACCURACY W relativeAccuracy() {input is zero}`() {
        // When / Then
        assertThat(LogarithmicMapping(0.0).relativeAccuracy)
            .isCloseTo(LogarithmicMapping.MIN_SAFE_RELATIVE_ACCURACY, within(1e-10))
    }

    @Test
    fun `M clamp to MIN_SAFE_RELATIVE_ACCURACY W relativeAccuracy() {input is negative}`() {
        // When / Then
        assertThat(LogarithmicMapping(-0.5).relativeAccuracy)
            .isCloseTo(LogarithmicMapping.MIN_SAFE_RELATIVE_ACCURACY, within(1e-10))
    }

    @Test
    fun `M clamp to MAX_SAFE_RELATIVE_ACCURACY W relativeAccuracy() {input is 1}`() {
        // When / Then
        assertThat(LogarithmicMapping(1.0).relativeAccuracy)
            .isCloseTo(LogarithmicMapping.MAX_SAFE_RELATIVE_ACCURACY, within(1e-10))
    }

    @Test
    fun `M clamp to MAX_SAFE_RELATIVE_ACCURACY W relativeAccuracy() {input exceeds 1}`() {
        // When / Then
        assertThat(LogarithmicMapping(2.0).relativeAccuracy)
            .isCloseTo(LogarithmicMapping.MAX_SAFE_RELATIVE_ACCURACY, within(1e-10))
    }

    // endregion

    // region minIndexableValue / maxIndexableValue

    @Test
    fun `M return positive minIndexableValue W minIndexedValue()`() {
        // When / Then
        assertThat(LogarithmicMapping(0.01).minIndexedValue).isGreaterThan(0.0)
    }

    @Test
    fun `M return positive maxIndexableValue W maxIndexedValue()`() {
        // When / Then
        assertThat(LogarithmicMapping(0.01).maxIndexedValue).isGreaterThan(0.0)
    }

    @Test
    fun `M have minIndexableValue less than maxIndexableValue W minIndexedValue()`() {
        // Given
        val mapping = LogarithmicMapping(0.01)

        // When / Then
        assertThat(mapping.minIndexedValue).isLessThan(mapping.maxIndexedValue)
    }

    @Test
    fun `M have index at minIndexableValue within Int range W getIndexFor() {minIndexableValue}`() {
        // Given
        val mapping = LogarithmicMapping(0.01)

        // When / Then
        assertThat(mapping.getIndexFor(mapping.minIndexedValue)).isGreaterThanOrEqualTo(Int.MIN_VALUE)
    }

    @Test
    fun `M have index at maxIndexableValue within Int range W getIndexFor() {maxIndexableValue}`() {
        // Given
        val mapping = LogarithmicMapping(0.01)

        // When / Then
        assertThat(mapping.getIndexFor(mapping.maxIndexedValue)).isLessThanOrEqualTo(Int.MAX_VALUE)
    }

    // endregion

    // region Serialization

    @Test
    fun `M return 9 W serializedSize()`(
        @DoubleForgery(min = 0.001, max = 0.1) fakeRelativeAccuracy: Double
    ) {
        // gamma is always non-zero (1 tag byte + 8 double bytes); indexOffset is always 0 (omitted)
        // When / Then
        assertThat(LogarithmicMapping(fakeRelativeAccuracy).serializedSize()).isEqualTo(9)
    }

    @Test
    fun `M produce identical bytes to reference proto encoding W writeTo()`(
        @DoubleForgery(min = 0.001, max = 0.1) fakeRelativeAccuracy: Double
    ) {
        // Given
        val ourMapping = LogarithmicMapping(fakeRelativeAccuracy)
        val refMapping = RefLogarithmicMapping(fakeRelativeAccuracy)
        val serializer = DDSketchSerializer(ourMapping.serializedSize())

        // When
        ourMapping.writeTo(serializer)
        val ourBytes = serializer.toByteArray()
        val refBytes = IndexMappingProtoBinding.toProto(refMapping).toByteArray()

        // Then
        assertThat(ourBytes).isEqualTo(refBytes)
    }

    // endregion

    companion object {
        private val STEP_MULTIPLIER = 1.0 + sqrt(2.0) * 100.0
        private const val FLOATING_POINT_ERROR = 1e-12
    }
}
