/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition

import com.datadog.android.api.InternalLogger
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.android.sessionreplay.internal.recorder.Recorder
import com.datadog.android.utils.verifyLog
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(ForgeConfigurator::class)
internal class CompositionCapturePipelineTest {

    @Mock
    lateinit var mockLegacyRecorder: Recorder

    @Mock
    lateinit var mockCompositionRecorder: Recorder

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @Test
    fun `M select only legacy pipeline W create { flag disabled }`() {
        // Given
        var compositionConstructions = 0
        val testedSelector = CapturePipelineSelector(
            compositionEnabled = false,
            legacyFactory = { mockLegacyRecorder },
            compositionFactory = {
                compositionConstructions++
                mockCompositionRecorder
            }
        )

        // When
        val result = testedSelector.create()

        // Then
        assertThat(result).isSameAs(mockLegacyRecorder)
        assertThat(compositionConstructions).isZero()
        verifyNoInteractions(mockCompositionRecorder)
    }

    @Test
    fun `M select only composition pipeline W create { flag enabled }`() {
        // Given
        var legacyConstructions = 0
        val testedSelector = CapturePipelineSelector(
            compositionEnabled = true,
            legacyFactory = {
                legacyConstructions++
                mockLegacyRecorder
            },
            compositionFactory = { mockCompositionRecorder }
        )

        // When
        val result = testedSelector.create()

        // Then
        assertThat(result).isSameAs(mockCompositionRecorder)
        assertThat(legacyConstructions).isZero()
        verifyNoInteractions(mockLegacyRecorder)
    }

    @Test
    fun `M keep recording cycle on selected pipeline W invoke recorder lifecycle`(
        @IntForgery(min = 1, max = 5) fakeRecordingCycles: Int
    ) {
        // Given
        var compositionConstructions = 0
        val testedRecorder = CapturePipelineSelector(
            compositionEnabled = false,
            legacyFactory = { mockLegacyRecorder },
            compositionFactory = {
                compositionConstructions++
                mockCompositionRecorder
            }
        ).create()

        // When
        repeat(fakeRecordingCycles) {
            testedRecorder.registerCallbacks()
            testedRecorder.stopProcessingRecords()
            testedRecorder.resumeRecorders()
            testedRecorder.stopRecorders()
            testedRecorder.unregisterCallbacks()
        }

        // Then
        verify(mockLegacyRecorder, times(fakeRecordingCycles)).registerCallbacks()
        verify(mockLegacyRecorder, times(fakeRecordingCycles)).stopProcessingRecords()
        verify(mockLegacyRecorder, times(fakeRecordingCycles)).resumeRecorders()
        verify(mockLegacyRecorder, times(fakeRecordingCycles)).stopRecorders()
        verify(mockLegacyRecorder, times(fakeRecordingCycles)).unregisterCallbacks()
        assertThat(compositionConstructions).isZero()
    }

    @Test
    fun `M keep composition state isolated W create multiple pipelines`(
        @IntForgery(min = 2, max = 5) fakePipelineCount: Int
    ) {
        // Given
        val testedSelector = CapturePipelineSelector(
            compositionEnabled = true,
            legacyFactory = { mock() },
            compositionFactory = { StatefulRecorder() }
        )
        val recorders = List(fakePipelineCount) { testedSelector.create() as StatefulRecorder }

        // When
        recorders.first().registerCallbacks()

        // Then
        assertThat(recorders.first().callbacksRegistered).isTrue()
        assertThat(recorders.drop(1)).allMatch { !it.callbacksRegistered }
    }

    @Test
    fun `M delegate recording lifecycle W composition pipeline is orchestrated`() {
        // Given
        val mockOrchestrator = mock<SnapshotCaptureOrchestrator>()
        val mockLifecycle = mock<CompositionCaptureLifecycle>()
        val mockCompletionQueue = mock<SnapshotCompletionQueue>()
        val testedPipeline = CompositionCapturePipeline(
            mockOrchestrator,
            mockLifecycle,
            mockCompletionQueue,
            mockInternalLogger
        )

        // When
        testedPipeline.registerCallbacks()
        testedPipeline.resumeRecorders()
        testedPipeline.stopRecorders()
        testedPipeline.stopProcessingRecords()
        testedPipeline.unregisterCallbacks()

        // Then
        inOrder(mockOrchestrator, mockLifecycle, mockCompletionQueue) {
            verify(mockLifecycle).registerCallbacks()
            verify(mockOrchestrator).start()
            verify(mockLifecycle).start()
            verify(mockLifecycle).stop()
            verify(mockOrchestrator).stop()
            verify(mockOrchestrator).shutdown()
            verify(mockCompletionQueue).stop()
            verify(mockLifecycle).unregisterCallbacks()
        }
    }

    @Test
    fun `M warn the user W requestCapture { embedded content slots requested }`(
        @StringForgery fakeSlotIds: Set<String>
    ) {
        // Given
        val mockOrchestrator = mock<SnapshotCaptureOrchestrator>()
        val mockLifecycle = mock<CompositionCaptureLifecycle>()
        val mockCompletionQueue = mock<SnapshotCompletionQueue>()
        val testedPipeline = CompositionCapturePipeline(
            mockOrchestrator,
            mockLifecycle,
            mockCompletionQueue,
            mockInternalLogger
        )

        // When
        testedPipeline.requestCapture(fakeSlotIds)

        // Then
        mockInternalLogger.verifyLog(
            InternalLogger.Level.WARN,
            InternalLogger.Target.USER,
            CompositionCapturePipeline.EMBEDDED_CONTENT_UNSUPPORTED_WITH_COMPOSITION_RECORDING_MESSAGE,
            onlyOnce = true
        )
        verifyNoInteractions(mockOrchestrator, mockLifecycle, mockCompletionQueue)
    }

    @Test
    fun `M delegate warning deduplication to the logger W requestCapture { repeated requests }`(
        @StringForgery fakeSlotIds: Set<String>,
        @IntForgery(min = 2, max = 10) fakeRequestCount: Int
    ) {
        // Given
        val testedPipeline = CompositionCapturePipeline(
            mock<SnapshotCaptureOrchestrator>(),
            mock<CompositionCaptureLifecycle>(),
            mock<SnapshotCompletionQueue>(),
            mockInternalLogger
        )

        // When
        repeat(fakeRequestCount) { testedPipeline.requestCapture(fakeSlotIds) }

        // Then
        mockInternalLogger.verifyLog(
            InternalLogger.Level.WARN,
            InternalLogger.Target.USER,
            CompositionCapturePipeline.EMBEDDED_CONTENT_UNSUPPORTED_WITH_COMPOSITION_RECORDING_MESSAGE,
            onlyOnce = true,
            mode = times(fakeRequestCount)
        )
    }

    @Test
    fun `M not warn the user W requestCapture { no embedded content slots }`() {
        // Given
        val testedPipeline = CompositionCapturePipeline(
            mock<SnapshotCaptureOrchestrator>(),
            mock<CompositionCaptureLifecycle>(),
            mock<SnapshotCompletionQueue>(),
            mockInternalLogger
        )

        // When
        testedPipeline.requestCapture(emptySet())

        // Then
        verifyNoInteractions(mockInternalLogger)
    }

    private class StatefulRecorder : Recorder {
        var callbacksRegistered = false

        override fun registerCallbacks() {
            callbacksRegistered = true
        }

        override fun unregisterCallbacks() = Unit

        override fun stopProcessingRecords() = Unit

        override fun resumeRecorders() = Unit

        override fun requestCapture(slotIds: Set<String>) = Unit

        override fun stopRecorders() = Unit
    }
}
