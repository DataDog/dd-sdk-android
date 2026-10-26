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
import org.junit.jupiter.api.BeforeEach
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
import java.util.concurrent.ExecutorService

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

    @Mock
    lateinit var mockConstructionExecutor: ExecutorService

    @Mock
    lateinit var mockOrchestrator: SnapshotCaptureOrchestrator

    @Mock
    lateinit var mockLifecycle: CompositionCaptureLifecycle

    @Mock
    lateinit var mockCompletionQueue: SnapshotCompletionQueue

    private lateinit var testedPipeline: CompositionCapturePipeline

    @BeforeEach
    fun setUp() {
        testedPipeline = CompositionCapturePipeline(
            mockOrchestrator,
            mockLifecycle,
            mockCompletionQueue,
            mockConstructionExecutor,
            mockInternalLogger
        )
    }

    @Test
    fun `M select only legacy pipeline W create { flag disabled }`() {
        // Given
        var fakeCompositionConstructions = 0
        val testedSelector = CapturePipelineSelector(
            compositionEnabled = false,
            legacyFactory = { mockLegacyRecorder },
            compositionFactory = {
                fakeCompositionConstructions++
                mockCompositionRecorder
            }
        )

        // When
        val result = testedSelector.create()

        // Then
        assertThat(result).isSameAs(mockLegacyRecorder)
        assertThat(fakeCompositionConstructions).isZero()
        verifyNoInteractions(mockCompositionRecorder)
    }

    @Test
    fun `M select only composition pipeline W create { flag enabled }`() {
        // Given
        var fakeLegacyConstructions = 0
        val testedSelector = CapturePipelineSelector(
            compositionEnabled = true,
            legacyFactory = {
                fakeLegacyConstructions++
                mockLegacyRecorder
            },
            compositionFactory = { mockCompositionRecorder }
        )

        // When
        val result = testedSelector.create()

        // Then
        assertThat(result).isSameAs(mockCompositionRecorder)
        assertThat(fakeLegacyConstructions).isZero()
        verifyNoInteractions(mockLegacyRecorder)
    }

    @Test
    fun `M retain selected pipeline W registerCallbacks() { repeated recording cycles }`(
        @IntForgery(min = 1, max = 5) fakeRecordingCycles: Int
    ) {
        // Given
        var fakeCompositionConstructions = 0
        val testedRecorder = CapturePipelineSelector(
            compositionEnabled = false,
            legacyFactory = { mockLegacyRecorder },
            compositionFactory = {
                fakeCompositionConstructions++
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
        assertThat(fakeCompositionConstructions).isZero()
    }

    @Test
    fun `M keep composition state isolated W registerCallbacks() { create multiple pipelines }`(
        @IntForgery(min = 2, max = 5) fakePipelineCount: Int
    ) {
        // Given
        val testedSelector = CapturePipelineSelector(
            compositionEnabled = true,
            legacyFactory = { mock() },
            compositionFactory = { StatefulRecorder() }
        )
        val fakeRecorders = List(fakePipelineCount) { testedSelector.create() as StatefulRecorder }

        // When
        fakeRecorders.first().registerCallbacks()

        // Then
        assertThat(fakeRecorders.first().callbacksRegistered).isTrue()
        assertThat(fakeRecorders.drop(1)).allMatch { !it.callbacksRegistered }
    }

    @Test
    fun `M register lifecycle callbacks W registerCallbacks()`() {
        // When
        testedPipeline.registerCallbacks()

        // Then
        verify(mockLifecycle).registerCallbacks()
    }

    @Test
    fun `M start orchestration before capture lifecycle W resumeRecorders()`() {
        // When
        testedPipeline.resumeRecorders()

        // Then
        inOrder(mockOrchestrator, mockLifecycle) {
            verify(mockOrchestrator).start()
            verify(mockLifecycle).start()
        }
    }

    @Test
    fun `M stop capture lifecycle before orchestration W stopRecorders()`() {
        // When
        testedPipeline.stopRecorders()

        // Then
        inOrder(mockLifecycle, mockOrchestrator) {
            verify(mockLifecycle).stop()
            verify(mockOrchestrator).stop()
        }
    }

    @Test
    fun `M shut down orchestration and processing in order W stopProcessingRecords()`() {
        // When
        testedPipeline.stopProcessingRecords()

        // Then
        inOrder(mockOrchestrator, mockConstructionExecutor, mockCompletionQueue) {
            verify(mockOrchestrator).shutdown()
            verify(mockConstructionExecutor).shutdownNow()
            verify(mockCompletionQueue).stop()
        }
    }

    @Test
    fun `M unregister lifecycle callbacks W unregisterCallbacks()`() {
        // When
        testedPipeline.unregisterCallbacks()

        // Then
        verify(mockLifecycle).unregisterCallbacks()
    }

    @Test
    fun `M warn the user W requestCapture { embedded content slots requested }`(
        @StringForgery fakeSlotIds: Set<String>
    ) {
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

        override fun onViewTransition() = Unit

        override fun stopRecorders() = Unit
    }
}
