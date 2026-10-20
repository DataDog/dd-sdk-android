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
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit

@ExtendWith(ForgeExtension::class)
@ForgeConfiguration(ForgeConfigurator::class)
internal class CompositionProcessingIsolationTest {

    @ParameterizedTest
    @CsvSource("140, true", "154, false")
    fun `M construct the next capture W completion is busy { original deadline still applies }`(
        fakeCompletionTimeMs: Long,
        fakeExpectedWrite: Boolean,
        forge: Forge
    ) {
        // Given
        val fixture = ProcessingFixture(forge)
        fixture.onCompletion = {
            if (fixture.fakeCompletedCount == 1) {
                // The next draw occurs while the preceding capture is still being mapped.
                fixture.fakeNowNs = TimeUnit.MILLISECONDS.toNanos(64)
                fixture.beginCapture()
                fixture.runConstruction()
                assertThat(fixture.fakeConstructedCount).isEqualTo(2)
                fixture.fakeNowNs = TimeUnit.MILLISECONDS.toNanos(fakeCompletionTimeMs)
            }
        }
        fixture.testedPipeline.resumeRecorders()
        fixture.beginCapture()
        fixture.runConstruction()

        // When
        fixture.runCompletion()

        // Then
        assertThat(fixture.fakeCompletedCount).isEqualTo(if (fakeExpectedWrite) 2 else 1)
        if (fakeExpectedWrite) {
            val records = argumentCaptor<EnrichedRecord>()
            verify(fixture.mockWriter).write(records.capture(), any())
            val record = records.firstValue.records
                .filterIsInstance<MobileSegment.MobileRecord.MobileFullSnapshotRecord>().single()
            assertThat(record.timestamp).isEqualTo(fixture.fakeTimestamp + 2)
        } else {
            verifyNoInteractions(fixture.mockWriter)
        }
    }

    @Test
    fun `M discard queued construction W stopProcessingRecords`(forge: Forge) {
        // Given
        val fixture = ProcessingFixture(forge)
        fixture.testedPipeline.resumeRecorders()
        fixture.beginCapture()

        // When
        fixture.testedPipeline.stopProcessingRecords()
        fixture.runConstruction()

        // Then
        assertThat(fixture.fakeConstructedCount).isZero()
        assertThat(fixture.fakeCompletionTasks).isEmpty()
        verify(fixture.mockConstructionExecutor).shutdownNow()
        verify(fixture.mockCompletionExecutor).shutdownNow()
        verifyNoInteractions(fixture.mockWriter)
    }

    @Test
    fun `M discard in-flight completion W stopProcessingRecords`(forge: Forge) {
        // Given
        val fixture = ProcessingFixture(forge)
        fixture.onCompletion = { fixture.testedPipeline.stopProcessingRecords() }
        fixture.testedPipeline.resumeRecorders()
        fixture.beginCapture()
        fixture.runConstruction()

        // When
        fixture.runCompletion()

        // Then
        verify(fixture.mockConstructionExecutor).shutdownNow()
        verify(fixture.mockCompletionExecutor).shutdownNow()
        verifyNoInteractions(fixture.mockWriter)
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
                DefaultSnapshotCompletionProcessor(
                    rumContextProvider = mockRumContextProvider,
                    recordWriter = mockWriter,
                    internalLogger = mock(),
                    timeProvider = mock(),
                    wireMapper = mockWireMapper
                ),
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
                    mockConstructionExecutor
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
