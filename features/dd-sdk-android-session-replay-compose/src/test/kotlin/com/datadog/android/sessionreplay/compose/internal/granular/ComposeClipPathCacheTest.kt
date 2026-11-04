/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.compose.internal.granular

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

internal class ComposeClipPathCacheTest {
    private val testedCache = ComposeClipPathCache()

    @Test
    fun `M preserve rounded output and reuse its string W rounded()`() {
        // When
        val first = testedCache.rounded(100, 50, 10.0, 0.0, 0.0)
        val second = testedCache.rounded(100, 50, 10.0, 0.0, 0.0)

        // Then
        assertThat(first).isEqualTo(
            "M 10.0,0 L 90.0,0 A 10.0,10.0 0 0 1 100.0,10.0 " +
                "L 100.0,40.0 A 10.0,10.0 0 0 1 90.0,50.0 " +
                "L 10.0,50.0 A 10.0,10.0 0 0 1 0,40.0 L 0,10.0 A 10.0,10.0 0 0 1 10.0,0 Z"
        )
        assertThat(second).isSameAs(first)
    }

    @Test
    fun `M preserve translated rounded output W rounded()`() {
        // When
        val path = testedCache.rounded(100, 50, 10.0, -5.0, 3.0)

        // Then
        assertThat(path).isEqualTo(
            "M 5.0,3.0 L 85.0,3.0 A 10.0,10.0 0 0 1 95.0,13.0 " +
                "L 95.0,43.0 A 10.0,10.0 0 0 1 85.0,53.0 " +
                "L 5.0,53.0 A 10.0,10.0 0 0 1 -5.0,43.0 L -5.0,13.0 A 10.0,10.0 0 0 1 5.0,3.0 Z"
        )
    }

    @ParameterizedTest
    @CsvSource("101,50,10,0,0", "100,51,10,0,0", "100,50,11,0,0", "100,50,10,1,0", "100,50,10,0,1")
    fun `M distinguish every rounded geometry input W rounded()`(
        fakeWidth: Long,
        fakeHeight: Long,
        fakeRadius: Double,
        fakeX: Double,
        fakeY: Double
    ) {
        // Given
        val original = testedCache.rounded(100, 50, 10.0, 0.0, 0.0)

        // When
        val changed = testedCache.rounded(fakeWidth, fakeHeight, fakeRadius, fakeX, fakeY)

        // Then
        assertThat(changed).isNotEqualTo(original)
        assertThat(testedCache.rounded(100, 50, 10.0, 0.0, 0.0)).isSameAs(original)
    }

    @ParameterizedTest
    @CsvSource("1,0,100,50", "0,1,100,50", "0,0,101,50", "0,0,100,51")
    fun `M distinguish every rectangle edge W rectangle()`(
        fakeLeft: Double,
        fakeTop: Double,
        fakeRight: Double,
        fakeBottom: Double
    ) {
        // Given
        val original = testedCache.rectangle(0.0, 0.0, 100.0, 50.0)

        // When
        val changed = testedCache.rectangle(fakeLeft, fakeTop, fakeRight, fakeBottom)

        // Then
        assertThat(original).isEqualTo("M 0.0,0.0 L 100.0,0.0 L 100.0,50.0 L 0.0,50.0 Z")
        assertThat(changed).isNotEqualTo(original)
        assertThat(testedCache.rectangle(0.0, 0.0, 100.0, 50.0)).isSameAs(original)
    }

    @Test
    fun `M preserve signed zero formatting W rectangle()`() {
        // When
        val positive = testedCache.rectangle(0.0, 0.0, 100.0, 50.0)
        val negative = testedCache.rectangle(-0.0, 0.0, 100.0, 50.0)

        // Then
        assertThat(negative).isEqualTo("M -0.0,0.0 L 100.0,0.0 L 100.0,50.0 L -0.0,50.0 Z")
        assertThat(negative).isNotEqualTo(positive)
    }

    @Test
    fun `M evict the least recently used string W rectangle()`() {
        // Given
        val testedSmallCache = ComposeClipPathCache(maxEntries = 2)
        val first = testedSmallCache.rectangle(0.0, 0.0, 100.0, 50.0)
        val second = testedSmallCache.rectangle(1.0, 0.0, 100.0, 50.0)
        assertThat(testedSmallCache.rectangle(0.0, 0.0, 100.0, 50.0)).isSameAs(first)

        // When
        testedSmallCache.rectangle(2.0, 0.0, 100.0, 50.0)

        // Then
        assertThat(testedSmallCache.rectangle(0.0, 0.0, 100.0, 50.0)).isSameAs(first)
        assertThat(testedSmallCache.rectangle(1.0, 0.0, 100.0, 50.0)).isEqualTo(second).isNotSameAs(second)
    }

    @Test
    fun `M bound retained strings W rounded() { default capacity }`() {
        // Given
        val first = testedCache.rounded(100, 50, 10.0, 0.0, 0.0)

        // When
        repeat(32) { testedCache.rounded(101L + it, 50, 10.0, 0.0, 0.0) }

        // Then
        assertThat(testedCache.rounded(100, 50, 10.0, 0.0, 0.0)).isEqualTo(first).isNotSameAs(first)
    }

    @Test
    fun `M avoid retaining oversized paths W rounded()`() {
        // Given
        val testedShortCache = ComposeClipPathCache(maxEntries = 1, maxPathLength = 80)
        val short = testedShortCache.rectangle(0.0, 0.0, 100.0, 50.0)

        // When
        val long = testedShortCache.rounded(100, 50, 10.0, 0.0, 0.0)
        val repeated = testedShortCache.rounded(100, 50, 10.0, 0.0, 0.0)

        // Then
        assertThat(long.length).isGreaterThan(80)
        assertThat(repeated).isEqualTo(long).isNotSameAs(long)
        assertThat(testedShortCache.rectangle(0.0, 0.0, 100.0, 50.0)).isSameAs(short)
    }

    @Test
    fun `M preserve nonfinite formatting W rectangle()`() {
        // When
        val path = testedCache.rectangle(Double.NaN, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, 50.0)

        // Then
        assertThat(path).isEqualTo("M NaN,-Infinity L Infinity,-Infinity L Infinity,50.0 L NaN,50.0 Z")
    }
}
