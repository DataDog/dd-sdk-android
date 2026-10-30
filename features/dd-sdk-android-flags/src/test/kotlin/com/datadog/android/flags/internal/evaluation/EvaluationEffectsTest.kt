/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal.evaluation

import com.datadog.android.api.InternalLogger
import com.datadog.android.utils.verifyLog
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

internal class EvaluationEffectsTest {
    @Test
    fun `M preserve queued order W drain() { reentrant delivery }`() {
        // Given
        val lock = Any()
        val testedEffects = EvaluationEffects(lock, mock())
        val delivered = mutableListOf<Int>()
        synchronized(lock) {
            testedEffects.enqueue {
                delivered.add(1)
                synchronized(lock) { testedEffects.enqueue { delivered.add(3) } }
                testedEffects.drain()
            }
            testedEffects.enqueue { delivered.add(2) }
        }

        // When
        testedEffects.drain()

        // Then
        assertThat(delivered).containsExactly(1, 2, 3)
    }

    @Test
    fun `M continue delivery W drain() { callback throws }`() {
        // Given
        val lock = Any()
        val mockLogger = mock<InternalLogger>()
        val testedEffects = EvaluationEffects(lock, mockLogger)
        val failure = IllegalStateException("listener failed")
        var delivered = false
        synchronized(lock) {
            testedEffects.enqueue { throw failure }
            testedEffects.enqueue { delivered = true }
        }

        // When
        testedEffects.drain()

        // Then
        assertThat(delivered).isTrue()
        mockLogger.verifyLog(
            InternalLogger.Level.ERROR,
            InternalLogger.Target.USER,
            "A feature flags state listener or evaluation callback failed.",
            throwable = failure
        )
    }
}
