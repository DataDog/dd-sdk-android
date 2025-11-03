/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.insights.internal.extensions

import fr.xgouchet.elmyr.annotation.DoubleForgery
import fr.xgouchet.elmyr.annotation.FloatForgery
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.annotation.LongForgery
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness
import kotlin.math.pow
import kotlin.math.roundToInt

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
internal class LangExtTest {

    @Test
    fun `M convert nanos to millis W ms`(@LongForgery fakeNanos: Long) {
        assertThat(fakeNanos.ms).isEqualTo(fakeNanos / NANOS_PER_MILLI)
    }

    @Test
    fun `M convert bytes to megabytes W Mb`(@DoubleForgery fakeBytes: Double) {
        assertThat(fakeBytes.Mb).isEqualTo(fakeBytes / BYTES_PER_MB)
    }

    @Test
    fun `M return NaN W round() {null}`() {
        // When
        val tested = null.round(2)

        // Then
        assertThat(tested).isNaN
    }

    @Test
    fun `M return NaN W round() {NaN}`() {
        // When
        val tested = Double.NaN.round(3)

        // Then
        assertThat(tested).isNaN
    }

    @Test
    fun `M round to given digits W round()`(
        @DoubleForgery(max = 1e300) fakeValue: Double,
        @IntForgery(min = 0, max = 6) digits: Int
    ) {
        val multiplier = 10.0.pow(digits)
        val expected = (fakeValue * multiplier).roundToInt() / multiplier

        val tested = fakeValue.round(digits)

        assertThat(tested).isEqualTo(expected)
    }

    @Test
    fun `M clip value inside {min, max - size} W clip()`(
        @IntForgery(min = 0, max = 200) size: Int,
        @IntForgery(min = 0, max = 500) minBoundSeed: Int,
        @IntForgery(min = 1, max = 500) spanSeed: Int,
        @FloatForgery(min = -500f, max = 1000f) insideXSeed: Float
    ) {
        // Given
        val minBound = minBoundSeed
        val maxBound = (minBound + spanSeed + size).coerceAtLeast(minBound + size + 1)
        val upper = (maxBound - size).toFloat()
        val belowMin = (minBound - 100).toFloat()
        val insideX = insideXSeed.coerceIn(minBound.toFloat(), upper)
        val aboveMax = upper + 100f

        // When
        val clippedBelow = belowMin.clip(size, minBound, maxBound)
        val clippedInside = insideX.clip(size, minBound, maxBound)
        val clippedAbove = aboveMax.clip(size, minBound, maxBound)
        val clippedAtMin = minBound.toFloat().clip(size, minBound, maxBound)
        val clippedAtUpper = upper.clip(size, minBound, maxBound)

        // Then
        assertThat(clippedBelow).isEqualTo(minBound.toFloat())
        assertThat(clippedInside).isEqualTo(insideX)
        assertThat(clippedAtMin).isEqualTo(minBound.toFloat())
        assertThat(clippedAtUpper).isEqualTo(upper)
        assertThat(clippedAbove).isEqualTo(upper)

        val result = insideXSeed.clip(size, minBound, maxBound)
        assertThat(result).isBetween(minBound.toFloat(), upper)
        assertThat(result.clip(size, minBound, maxBound)).isEqualTo(result)
    }
}
