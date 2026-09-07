/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition

import android.view.View
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.kotlin.mock

@Extensions(
    ExtendWith(ForgeExtension::class)
)
@ForgeConfiguration(ForgeConfigurator::class)
internal class CompositionChangesetTest {

    @Test
    fun `M union windows W mergedWith { both sides non-empty }`(forge: Forge) {
        // Given
        val fakeFirstWindows = forge.aWindowList()
        val fakeSecondWindows = forge.aWindowList()
        val testedChangeset = CompositionChangeset.of(fakeFirstWindows)

        // When
        val merged = testedChangeset.mergedWith(CompositionChangeset.of(fakeSecondWindows)) as CompositionChangeset

        // Then
        assertThat(merged.changedWindows())
            .containsExactlyInAnyOrderElementsOf((fakeFirstWindows + fakeSecondWindows).distinct())
    }

    @Test
    fun `M keep a single entry W mergedWith { both sides carry the same window }`(forge: Forge) {
        // Given
        val fakeSharedWindow = mock<View>()
        val fakeOtherWindow = mock<View>()
        val fakeWindows = if (forge.aBool()) {
            listOf(fakeSharedWindow, fakeOtherWindow)
        } else {
            listOf(fakeOtherWindow, fakeSharedWindow)
        }
        val testedChangeset = CompositionChangeset.of(fakeWindows)

        // When
        val merged = testedChangeset
            .mergedWith(CompositionChangeset.of(listOf(fakeSharedWindow))) as CompositionChangeset

        // Then
        assertThat(merged.changedWindows()).containsExactlyInAnyOrder(fakeSharedWindow, fakeOtherWindow)
    }

    @Test
    fun `M stay empty W mergedWith { receiver carries unknown invalidation }`(forge: Forge) {
        // Given
        val testedChangeset = CompositionChangeset.EMPTY

        // When
        val merged = testedChangeset.mergedWith(CompositionChangeset.of(forge.aWindowList()))

        // Then
        assertThat(merged.isEmpty()).isTrue()
    }

    @Test
    fun `M become empty W mergedWith { incoming signal carries unknown invalidation }`(forge: Forge) {
        // Given
        val testedChangeset = CompositionChangeset.of(forge.aWindowList())

        // When
        val merged = testedChangeset.mergedWith(CaptureChangeset.EMPTY)

        // Then
        assertThat(merged.isEmpty()).isTrue()
    }

    @Test
    fun `M stay empty W mergedWith { both sides carry unknown invalidation }`() {
        // Given
        val testedChangeset = CompositionChangeset.EMPTY

        // When
        val merged = testedChangeset.mergedWith(CompositionChangeset.EMPTY)

        // Then
        assertThat(merged.isEmpty()).isTrue()
    }

    @Test
    fun `M report empty W of { no windows drew }`() {
        // When
        val testedChangeset = CompositionChangeset.of(emptyList())

        // Then
        assertThat(testedChangeset.isEmpty()).isTrue()
        assertThat(testedChangeset.changedWindows()).isEmpty()
    }

    private fun Forge.aWindowList(): List<View> = aList(size = anInt(min = 1, max = 5)) { mock<View>() }
}
