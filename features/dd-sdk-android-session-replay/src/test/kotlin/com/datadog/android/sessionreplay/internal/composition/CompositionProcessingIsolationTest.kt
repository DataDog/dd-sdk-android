/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition

import com.datadog.android.internal.sessionreplay.composition.RumViewIdentityScope
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.android.sessionreplay.internal.composition.mapper.CapturedViewData
import com.datadog.android.sessionreplay.internal.composition.mapper.CapturedViewMapperResult
import com.datadog.android.sessionreplay.internal.processor.EnrichedRecord
import com.datadog.android.sessionreplay.internal.storage.RecordWriter
import com.datadog.android.sessionreplay.internal.utils.RumContextProvider
import com.datadog.android.sessionreplay.internal.utils.SessionReplayRumContext
import com.datadog.android.sessionreplay.model.MobileSegment
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.isA
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@ForgeConfiguration(ForgeConfigurator::class)
@MockitoSettings(strictness = Strictness.LENIENT)
internal class CompositionProcessingIsolationTest {

    @Test
    fun `M construct another capture W runCompletion() { preceding completion still busy }`(forge: Forge) {
        // Given
        val fakeFixture = ProcessingFixture(forge)
        var fakeConstructedWhileBusy = 0
        fakeFixture.onCompletion = {
            if (fakeFixture.fakeCompletedCount == 1) {
                fakeFixture.fakeNowNs = TimeUnit.MILLISECONDS.toNanos(64)
                fakeFixture.beginCapture()
                fakeFixture.runConstruction()
                fakeConstructedWhileBusy = fakeFixture.fakeConstructedCount
            }
        }
        fakeFixture.testedPipeline.resumeRecorders()
        fakeFixture.beginCapture()
        fakeFixture.runConstruction()

        // When
        fakeFixture.runCompletion()

        // Then
        assertThat(fakeConstructedWhileBusy).isEqualTo(2)
    }

    @ParameterizedTest
    @CsvSource("140, true", "154, false")
    fun `M retain original deadline W runCompletion() { completion queue was busy }`(
        fakeCompletionTimeMs: Long,
        fakeExpectedWrite: Boolean,
        forge: Forge
    ) {
        // Given
        val fakeFixture = ProcessingFixture(forge)
        fakeFixture.onCompletion = {
            if (fakeFixture.fakeCompletedCount == 1) {
                fakeFixture.fakeNowNs = TimeUnit.MILLISECONDS.toNanos(64)
                fakeFixture.beginCapture()
                fakeFixture.runConstruction()
                fakeFixture.fakeNowNs = TimeUnit.MILLISECONDS.toNanos(fakeCompletionTimeMs)
            }
        }
        fakeFixture.testedPipeline.resumeRecorders()
        fakeFixture.beginCapture()
        fakeFixture.runConstruction()

        // When
        fakeFixture.runCompletion()

        // Then
        assertThat(fakeFixture.fakeCompletedCount).isEqualTo(if (fakeExpectedWrite) 2 else 1)
        if (fakeExpectedWrite) {
            val records = argumentCaptor<EnrichedRecord>()
            verify(fakeFixture.mockWriter).write(records.capture(), isA())
            val record = records.firstValue.records.single() as MobileSegment.MobileRecord.MobileFullSnapshotRecord
            assertThat(record.timestamp).isEqualTo(fakeFixture.fakeTimestamp + 2)
        } else {
            verifyNoInteractions(fakeFixture.mockWriter)
        }
    }

    @Test
    fun `M discard queued construction W stopProcessingRecords`(forge: Forge) {
        // Given
        val fakeFixture = ProcessingFixture(forge)
        fakeFixture.testedPipeline.resumeRecorders()
        fakeFixture.beginCapture()

        // When
        fakeFixture.testedPipeline.stopProcessingRecords()
        fakeFixture.runConstruction()

        // Then
        assertThat(fakeFixture.fakeConstructedCount).isZero()
        assertThat(fakeFixture.fakeCompletionTasks).isEmpty()
        verify(fakeFixture.mockConstructionExecutor).shutdownNow()
        verify(fakeFixture.mockCompletionExecutor).shutdownNow()
        verifyNoInteractions(fakeFixture.mockWriter)
    }

    @Test
    fun `M discard in-flight completion W runCompletion() { stopProcessingRecords }`(forge: Forge) {
        // Given
        val fakeFixture = ProcessingFixture(forge)
        fakeFixture.onCompletion = { fakeFixture.testedPipeline.stopProcessingRecords() }
        fakeFixture.testedPipeline.resumeRecorders()
        fakeFixture.beginCapture()
        fakeFixture.runConstruction()

        // When
        fakeFixture.runCompletion()

        // Then
        verify(fakeFixture.mockConstructionExecutor).shutdownNow()
        verify(fakeFixture.mockCompletionExecutor).shutdownNow()
        verifyNoInteractions(fakeFixture.mockWriter)
    }

    private class ProcessingFixture(forge: Forge) {
        var fakeNowNs = 0L
        var fakeConstructedCount = 0
        var fakeCompletedCount = 0
        var onCompletion: () -> Unit = {}
        val fakeTimestamp = forge.aLong(min = 1L, max = 1_000_000L)
        val mockConstructionExecutor = mock<ExecutorService>()
        val mockCompletionExecutor = mock<ExecutorService>()
        val mockWriter = mock<RecordWriter>()
        val fakeCompletionTasks = ArrayDeque<Runnable>()
        private val fakeConstructionTasks = ArrayDeque<Runnable>()
        private val fakeCaptureTasks = ArrayDeque<() -> Unit>()
        private val fakeIdentityFactory =
            DefaultCapturedIdentityFactory(RumViewIdentityScope(forge.anAlphabeticalString()))
        private val fakeWindowIdentity = fakeIdentityFactory.window(forge.anAlphabeticalString())
        private val fakeDensity = forge.aFloat(min = 0.75f, max = 4f)
        private val fakeBounds = forge.aCapturedBounds()
        private val mockMapping = mock<CapturedViewData>()
        private val mockRumContextProvider = mock<RumContextProvider>()
        private val mockWireMapper = mock<CapturedTreeWireMapper>()
        private val testedOrchestrator: SnapshotCaptureOrchestrator
        val testedPipeline: CompositionCapturePipeline

        init {
            doAnswer { fakeConstructionTasks.addLast(it.getArgument(0)); null }
                .whenever(mockConstructionExecutor).execute(any())
            doAnswer { fakeCompletionTasks.addLast(it.getArgument(0)); null }
                .whenever(mockCompletionExecutor).execute(any())
            whenever(mockMapping.map(any())).thenAnswer {
                fakeConstructedCount++
                CapturedViewMapperResult.None
            }
            whenever(mockRumContextProvider.getRumContext()).thenReturn(
                SessionReplayRumContext(
                    forge.anAlphabeticalString(),
                    forge.anAlphabeticalString(),
                    fakeIdentityFactory.scope.value
                )
            )
            whenever(mockWireMapper.mapFullSnapshot(any())).thenAnswer {
                fakeCompletedCount++
                onCompletion()
                DefaultCapturedTreeWireMapper().mapFullSnapshot(it.getArgument(0))
            }
            val completionQueue = SnapshotCompletionQueue(
                mockCompletionExecutor,
                DefaultSnapshotCompletionProcessor(mockRumContextProvider, mockWriter, mock(), mockWireMapper),
                mock()
            )
            var fakeCapturedCount = 0L
            val fakeProducer = CapturedSnapshotProducer {
                val fakeView = CapturedNativeView(
                    fakeWindowIdentity,
                    -1,
                    fakeBounds,
                    1.0,
                    mockMapping,
                    true,
                    null,
                    null
                )

                CapturedNativeSnapshot(
                    fakeTimestamp + ++fakeCapturedCount,
                    fakeIdentityFactory.scope,
                    listOf(CapturedNativeWindow(fakeDensity, listOf(fakeView)))
                )
            }
            testedOrchestrator = SnapshotCaptureOrchestrator(
                producer = fakeProducer,
                processor = ExecutorCapturedSnapshotProcessor(mockConstructionExecutor, mock()),
                consumer = completionQueue,
                timeProvider = { fakeNowNs },
                captureScheduler = CaptureTaskScheduler { _, task ->
                    fakeCaptureTasks.addLast(task)
                    CancellableCaptureWork { fakeCaptureTasks.remove(task) }
                },
                mainThreadExecutor = CaptureMainThreadExecutor { task -> task(); CancellableCaptureWork.NONE },
                expiryScheduler = CaptureTaskScheduler { _, _ -> CancellableCaptureWork.NONE },
                captureDelayNs = 0L
            )
            testedPipeline =
                CompositionCapturePipeline(
                    testedOrchestrator,
                    mock(),
                    completionQueue,
                    mockConstructionExecutor,
                    mock()
                )
        }

        fun beginCapture() {
            testedOrchestrator.requestCapture()
            fakeCaptureTasks.removeFirst().invoke()
        }

        fun runConstruction() {
            fakeConstructionTasks.removeFirst().run()
        }

        fun runCompletion() {
            fakeCompletionTasks.removeFirst().run()
        }
    }
}
