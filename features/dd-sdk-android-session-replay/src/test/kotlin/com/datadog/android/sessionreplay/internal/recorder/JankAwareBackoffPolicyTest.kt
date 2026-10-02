/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(ExtendWith(MockitoExtension::class))
@MockitoSettings(strictness = Strictness.LENIENT)
internal class JankAwareBackoffPolicyTest {

    @Test
    fun `M allow capture W allowsCapture { disabled }`() {
        // Given
        val mockFrameHealthMonitor: FrameHealthMonitor = mock()
        val testedPolicy = JankAwareBackoffPolicy(isEnabled = false, frameHealthMonitor = mockFrameHealthMonitor)

        // When/Then - never even asks the monitor, regardless of its state
        assertThat(testedPolicy.allowsCapture()).isTrue
        verifyNoInteractions(mockFrameHealthMonitor)
    }

    @Test
    fun `M allow capture W allowsCapture { enabled, monitor not degraded }`() {
        // Given
        val mockFrameHealthMonitor: FrameHealthMonitor = mock()
        whenever(mockFrameHealthMonitor.isDegraded()).thenReturn(false)
        val testedPolicy = JankAwareBackoffPolicy(isEnabled = true, frameHealthMonitor = mockFrameHealthMonitor)

        // When/Then
        assertThat(testedPolicy.allowsCapture()).isTrue
        verify(mockFrameHealthMonitor).isDegraded()
    }

    @Test
    fun `M deny capture W allowsCapture { enabled, monitor degraded }`() {
        // Given
        val mockFrameHealthMonitor: FrameHealthMonitor = mock()
        whenever(mockFrameHealthMonitor.isDegraded()).thenReturn(true)
        val testedPolicy = JankAwareBackoffPolicy(isEnabled = true, frameHealthMonitor = mockFrameHealthMonitor)

        // When/Then
        assertThat(testedPolicy.allowsCapture()).isFalse
    }

    @Test
    fun `M allow capture W allowsCapture { enabled, no monitor configured }`() {
        // Given
        val testedPolicy = JankAwareBackoffPolicy(isEnabled = true, frameHealthMonitor = null)

        // When/Then
        assertThat(testedPolicy.allowsCapture()).isTrue
    }
}
