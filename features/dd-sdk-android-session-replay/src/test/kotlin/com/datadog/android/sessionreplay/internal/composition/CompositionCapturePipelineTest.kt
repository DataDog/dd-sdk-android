/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition

import com.datadog.android.sessionreplay.internal.recorder.Recorder
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

internal class CompositionCapturePipelineTest {

    @Test
    fun `M select only legacy pipeline W create { flag disabled }`() {
        // Given
        val legacy = mock<Recorder>()
        var legacyConstructions = 0
        var compositionConstructions = 0
        val selector = CapturePipelineSelector(
            compositionEnabled = false,
            legacyFactory = {
                legacyConstructions++
                legacy
            },
            compositionFactory = {
                compositionConstructions++
                mock()
            }
        )

        // When
        val result = selector.create()

        // Then
        assertThat(result).isSameAs(legacy)
        assertThat(legacyConstructions).isEqualTo(1)
        assertThat(compositionConstructions).isZero()
    }

    @Test
    fun `M select only composition pipeline W create { flag enabled }`() {
        // Given
        val composition = mock<Recorder>()
        var legacyConstructions = 0
        var compositionConstructions = 0
        val selector = CapturePipelineSelector(
            compositionEnabled = true,
            legacyFactory = {
                legacyConstructions++
                mock()
            },
            compositionFactory = {
                compositionConstructions++
                composition
            }
        )

        // When
        val result = selector.create()

        // Then
        assertThat(result).isSameAs(composition)
        assertThat(legacyConstructions).isZero()
        assertThat(compositionConstructions).isEqualTo(1)
    }

    @Test
    fun `M keep recording cycle on selected pipeline W invoke recorder lifecycle`() {
        // Given
        val legacy = mock<Recorder>()
        var compositionConstructions = 0
        val recorder = CapturePipelineSelector(
            compositionEnabled = false,
            legacyFactory = { legacy },
            compositionFactory = {
                compositionConstructions++
                mock()
            }
        ).create()

        // When
        recorder.registerCallbacks()
        recorder.stopProcessingRecords()
        recorder.resumeRecorders()
        recorder.stopRecorders()
        recorder.unregisterCallbacks()

        // Then
        verify(legacy).registerCallbacks()
        verify(legacy).stopProcessingRecords()
        verify(legacy).resumeRecorders()
        verify(legacy).stopRecorders()
        verify(legacy).unregisterCallbacks()
        assertThat(compositionConstructions).isZero()
    }

    @Test
    fun `M keep composition state isolated W create multiple pipelines`() {
        // Given
        val selector = CapturePipelineSelector(
            compositionEnabled = true,
            legacyFactory = { mock() },
            compositionFactory = { StatefulRecorder() }
        )
        val first = selector.create() as StatefulRecorder
        val second = selector.create() as StatefulRecorder

        // When
        first.registerCallbacks()

        // Then
        assertThat(first.callbacksRegistered).isTrue()
        assertThat(second.callbacksRegistered).isFalse()
    }

    private class StatefulRecorder : Recorder {
        var callbacksRegistered = false

        override fun registerCallbacks() {
            callbacksRegistered = true
        }

        override fun unregisterCallbacks() = Unit

        override fun stopProcessingRecords() = Unit

        override fun resumeRecorders() = Unit

        override fun stopRecorders() = Unit
    }
}
