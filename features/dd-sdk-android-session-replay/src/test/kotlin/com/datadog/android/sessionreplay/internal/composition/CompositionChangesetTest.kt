/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition

import android.view.View
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

internal class CompositionChangesetTest {

    @Test
    fun `M union windows W mergedWith { both sides non-empty }`() {
        // Given
        val first = CompositionChangeset.of(listOf(mock<View>()))
        val secondView = mock<View>()
        val second = CompositionChangeset.of(listOf(secondView))

        // When
        val merged = first.mergedWith(second) as CompositionChangeset

        // Then
        assertThat(merged.changedWindows()).containsAll(first.changedWindows() + secondView)
    }

    @Test
    fun `M stay empty W mergedWith { receiver carries unknown invalidation }`() {
        // Given
        val unknown = CompositionChangeset.EMPTY
        val specific = CompositionChangeset.of(listOf(mock<View>()))

        // When
        val merged = unknown.mergedWith(specific)

        // Then
        assertThat(merged.isEmpty()).isTrue()
    }

    @Test
    fun `M become empty W mergedWith { incoming signal carries unknown invalidation }`() {
        // Given
        val specific = CompositionChangeset.of(listOf(mock<View>()))
        val unknown: CaptureChangeset = CaptureChangeset.EMPTY

        // When
        val merged = specific.mergedWith(unknown)

        // Then
        assertThat(merged.isEmpty()).isTrue()
    }

    @Test
    fun `M stay empty W mergedWith { both sides carry unknown invalidation }`() {
        // Given
        val first = CompositionChangeset.EMPTY
        val second = CompositionChangeset.EMPTY

        // When
        val merged = first.mergedWith(second)

        // Then
        assertThat(merged.isEmpty()).isTrue()
    }
}
