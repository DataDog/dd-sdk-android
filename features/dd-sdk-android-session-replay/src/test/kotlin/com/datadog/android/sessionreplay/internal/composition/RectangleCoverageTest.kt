/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class RectangleCoverageTest {
    @Test
    fun `M reuse capacity after replacing multiple contained rectangles W add`() {
        // Given
        val testedCoverage = RectangleCoverage(maxBounds = 3, maxFragments = 3)
        testedCoverage.add(CoverageRectangle(0, 0, 1, 1))
        testedCoverage.add(CoverageRectangle(2, 0, 3, 1))
        testedCoverage.add(CoverageRectangle(4, 0, 5, 1))

        // When
        testedCoverage.add(CoverageRectangle(0, 0, 5, 1))
        testedCoverage.add(CoverageRectangle(5, 0, 6, 1))
        testedCoverage.add(CoverageRectangle(6, 0, 7, 1))

        // Then
        assertThat(testedCoverage.covers(CoverageRectangle(0, 0, 7, 1))).isTrue()
        assertThat(testedCoverage.covers(CoverageRectangle(0, 0, 8, 1))).isFalse()
    }
}
