/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.heatmaps

import android.view.View
import android.view.ViewParent
import com.datadog.android.internal.forge.Configurator
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class HeatmapViewKeyTest {

    @Test
    fun `M include parent hash W heatmapViewKey() {view has parent}`() {
        // Given
        val fakeView: View = mock()
        val fakeParent: ViewParent = mock()
        whenever(fakeView.parent).thenReturn(fakeParent)

        // When
        val key = heatmapViewKey(fakeView)

        // Then
        val expected = HEATMAP_VIEW_KEY_COEFFICIENT * System.identityHashCode(fakeView).toLong() +
            System.identityHashCode(fakeParent).toLong()
        assertThat(key).isEqualTo(expected)
    }

    @Test
    fun `M use zero for parent hash W heatmapViewKey() {view has no parent}`() {
        // Given
        val fakeView: View = mock()
        whenever(fakeView.parent).thenReturn(null)

        // When
        val key = heatmapViewKey(fakeView)

        // Then
        assertThat(key).isEqualTo(HEATMAP_VIEW_KEY_COEFFICIENT * System.identityHashCode(fakeView).toLong())
    }

    @Test
    fun `M return distinct keys W heatmapViewKey() {two views with same parent}`() {
        // Given
        val fakeParent: ViewParent = mock()
        val fakeView1: View = mock()
        val fakeView2: View = mock()
        whenever(fakeView1.parent).thenReturn(fakeParent)
        whenever(fakeView2.parent).thenReturn(fakeParent)

        // When / Then
        assertThat(heatmapViewKey(fakeView1)).isNotEqualTo(heatmapViewKey(fakeView2))
    }

    @Test
    fun `M return distinct keys W heatmapViewKey() {view and parent hashes swapped}`() {
        // Given — verifies the combinator is non-commutative:
        // key(view=A, parent=B) must not equal key(view=B, parent=A).
        // We wire fakeViewA's parent to fakeParentB and fakeViewB's parent to fakeParentA,
        // so the two keys use the same pair of identity hashes but in opposite roles.
        //
        // Note: the two keys are equal if and only if 31*H(viewA) + H(parentB) == 31*H(viewB) + H(parentA),
        // i.e. when the identity hashes satisfy a specific linear equation. This holds with
        // probability ~1/2^32 per run, which is considered acceptable.
        val fakeViewA: View = mock()
        val fakeViewB: View = mock()
        val fakeParentA: ViewParent = mock()
        val fakeParentB: ViewParent = mock()
        whenever(fakeViewA.parent).thenReturn(fakeParentB)
        whenever(fakeViewB.parent).thenReturn(fakeParentA)

        // When / Then
        assertThat(heatmapViewKey(fakeViewA)).isNotEqualTo(heatmapViewKey(fakeViewB))
    }
}
