/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.instrumentation.gestures

import android.view.View
import android.view.ViewGroup
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
internal class TapTargetSelectorTest {

    @Test
    fun `M prefer candidate host W shouldSelectCandidate {candidate has higher Z}`() {
        // Given
        val parent: ViewGroup = mock()
        val currentHost: View = mock {
            whenever(it.parent).thenReturn(parent)
            whenever(it.z).thenReturn(0f)
        }
        val candidateHost: View = mock {
            whenever(it.parent).thenReturn(parent)
            whenever(it.z).thenReturn(10f)
        }
        val testedSelector = TapTargetSelector()

        // When
        val result = testedSelector.shouldSelectCandidate(currentHost, candidateHost)

        // Then
        assertThat(result).isTrue()
        verify(parent, never()).indexOfChild(any())
    }

    @Test
    fun `M prefer later host W shouldSelectCandidate {drawing order callback differs from child order}`() {
        // Given
        val parent: ViewGroup = mock()
        val earlierHost: View = mock { whenever(it.parent).thenReturn(parent) }
        val laterHost: View = mock { whenever(it.parent).thenReturn(parent) }
        whenever(parent.indexOfChild(earlierHost)).thenReturn(0)
        whenever(parent.indexOfChild(laterHost)).thenReturn(1)
        whenever(parent.getChildDrawingOrder(0)).thenReturn(1)
        whenever(parent.getChildDrawingOrder(1)).thenReturn(0)
        val testedSelector = TapTargetSelector()

        // When
        val result = testedSelector.shouldSelectCandidate(earlierHost, laterHost)

        // Then
        assertThat(result).isTrue()
        verify(parent, never()).getChildDrawingOrder(any())
    }

    @Test
    fun `M prefer later host W shouldSelectCandidate {parent throws while resolving child index}`() {
        // Given
        val parent: ViewGroup = mock()
        val earlierHost: View = mock { whenever(it.parent).thenReturn(parent) }
        val laterHost: View = mock { whenever(it.parent).thenReturn(parent) }
        whenever(parent.indexOfChild(earlierHost)).thenThrow(IllegalStateException())
        whenever(parent.indexOfChild(laterHost)).thenReturn(1)
        val testedSelector = TapTargetSelector()

        // When
        val result = testedSelector.shouldSelectCandidate(earlierHost, laterHost)

        // Then
        assertThat(result).isTrue()
    }
}
