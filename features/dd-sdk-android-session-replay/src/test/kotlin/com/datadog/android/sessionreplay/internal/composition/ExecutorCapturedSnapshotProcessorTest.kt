/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition

import com.datadog.android.sessionreplay.internal.composition.mapper.CapturedViewData
import com.datadog.android.sessionreplay.internal.composition.mapper.CapturedViewMapperResult
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.concurrent.Executor
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicReference

internal class ExecutorCapturedSnapshotProcessorTest {
    private val fakeFactory = DefaultCapturedIdentityFactory(RumViewIdentityScope("view"))
    private val fakeContext = CaptureGenerationContext(1L, 0L, 100L, CaptureTimeProvider { 0L })
    private val fakeNone = CapturedViewData.Mapped(CapturedViewMapperResult.None)

    private fun snapshot(mapping: CapturedViewData = fakeNone): CapturedNativeSnapshot = CapturedNativeSnapshot(
        123L,
        fakeFactory.scope,
        listOf(
            CapturedNativeWindow(
                1f,
                listOf(
                    CapturedNativeView(
                        fakeFactory.window("window"),
                        -1,
                        CapturedBounds(0, 0, 100, 100),
                        1.0,
                        mapping,
                        true,
                        null,
                        null
                    )
                )
            )
        )
    )

    @Test
    fun `M build on the executor W process`() {
        // Given
        val fakeQueue = mutableListOf<Runnable>()
        val testedProcessor = ExecutorCapturedSnapshotProcessor(Executor(fakeQueue::add), mock())
        val fakeResult = AtomicReference<SnapshotProcessingResult>()
        val fakeCallbackThread = AtomicReference<Thread>()

        // When
        testedProcessor.process(SnapshotProcessingRequest(fakeContext, snapshot())) {
            fakeResult.set(it)
            fakeCallbackThread.set(Thread.currentThread())
        }

        // Then
        assertThat(fakeResult.get()).isNull()
        val fakeWorker = Thread { fakeQueue.single().run() }.apply { start(); join() }
        assertThat(fakeCallbackThread.get()).isSameAs(fakeWorker)
        val snapshot = (fakeResult.get() as SnapshotProcessingResult.Completed).snapshot
        assertThat(snapshot.timestamp).isEqualTo(123L)
        assertThat(DefaultCapturedTreeValidator().validate(snapshot)).isEqualTo(CaptureValidationResult.Valid)
    }

    @Test
    fun `M retain completed generation W callback runs before process returns`() {
        // Given: a fast executor finishes before the orchestrator can register the returned handle.
        val fakeResults = mutableListOf<SnapshotProcessingResult>()
        val testedProcessor = ExecutorCapturedSnapshotProcessor(Executor(Runnable::run), mock())

        // When
        val work = testedProcessor.process(SnapshotProcessingRequest(fakeContext, snapshot()), fakeResults::add)
        work.cancel()

        // Then: late registration cleanup must not revoke the snapshot already handed downstream.
        assertThat(fakeResults).hasSize(1)
        assertThat(fakeResults.single()).isInstanceOf(SnapshotProcessingResult.Completed::class.java)
        assertThat(fakeContext.isActive()).isTrue()
    }

    @Test
    fun `M discard queued input W cancel`() {
        // Given
        val fakeQueue = mutableListOf<Runnable>()
        val fakeResults = mutableListOf<SnapshotProcessingResult>()
        val testedProcessor = ExecutorCapturedSnapshotProcessor(Executor(fakeQueue::add), mock())
        val work = testedProcessor.process(SnapshotProcessingRequest(fakeContext, snapshot()), fakeResults::add)

        // When
        work.cancel()
        fakeQueue.single().run()

        // Then
        assertThat(fakeContext.isActive()).isFalse()
        assertThat(fakeResults).isEmpty()
    }

    @Test
    fun `M stop construction W cancel during mapping`() {
        // Given
        val fakeQueue = mutableListOf<Runnable>()
        val fakeResults = mutableListOf<SnapshotProcessingResult>()
        val mockMapping: CapturedViewData = mock()
        lateinit var work: CancellableCaptureWork
        whenever(mockMapping.map(any())).thenAnswer {
            work.cancel()
            CapturedViewMapperResult.None
        }
        val testedProcessor = ExecutorCapturedSnapshotProcessor(Executor(fakeQueue::add), mock())
        work = testedProcessor.process(SnapshotProcessingRequest(fakeContext, snapshot(mockMapping)), fakeResults::add)

        // When
        fakeQueue.single().run()

        // Then
        assertThat(fakeContext.isActive()).isFalse()
        assertThat(fakeResults).isEmpty()
    }

    @Test
    fun `M report failure W executor rejects work`() {
        // Given
        val fakeResults = mutableListOf<SnapshotProcessingResult>()
        val testedProcessor = ExecutorCapturedSnapshotProcessor(Executor { throw RejectedExecutionException() }, mock())

        // When
        testedProcessor.process(SnapshotProcessingRequest(fakeContext, snapshot()), fakeResults::add)

        // Then
        assertThat(fakeResults).containsExactly(SnapshotProcessingResult.Failed(fakeContext.id))
    }

    @Test
    fun `M contain mapper failure W construct snapshot`() {
        // Given
        val fakeResults = mutableListOf<SnapshotProcessingResult>()
        val mockMapping: CapturedViewData = mock()
        whenever(mockMapping.map(any())).thenThrow(IllegalStateException("Invalid copied value"))
        val testedProcessor = ExecutorCapturedSnapshotProcessor(Executor(Runnable::run), mock())

        // When
        testedProcessor.process(SnapshotProcessingRequest(fakeContext, snapshot(mockMapping)), fakeResults::add)

        // Then
        assertThat(fakeResults).containsExactly(SnapshotProcessingResult.Failed(fakeContext.id))
    }

    @Test
    fun `M construct deep hierarchy without recursion W build`() {
        // Given
        val fakeWindowIdentity = fakeFactory.window("window")
        val fakeViews = List(5000) { index ->
            CapturedNativeView(
                if (index == 0) fakeWindowIdentity else fakeFactory.view(fakeWindowIdentity, index.toString()),
                index - 1,
                CapturedBounds(0, 0, 100, 100),
                1.0,
                fakeNone,
                true,
                null,
                null
            )
        }
        val testedBuilder = CapturedNativeSnapshotBuilder()

        // When
        val result = testedBuilder.buildWindow(CapturedNativeWindow(1f, fakeViews), fakeContext)

        // Then
        assertThat(result!!.layers).hasSize(5000)
        assertThat(result.rootLayer.identity).isEqualTo(fakeWindowIdentity)
        assertThat(result.layers.first().identity).isEqualTo(fakeViews.last().identity)
    }
}
