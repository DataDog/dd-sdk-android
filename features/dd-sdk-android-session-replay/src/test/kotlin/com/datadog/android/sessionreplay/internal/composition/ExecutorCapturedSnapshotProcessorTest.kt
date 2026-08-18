/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition

import com.datadog.android.api.InternalLogger
import com.datadog.android.internal.sessionreplay.composition.CapturedBounds
import com.datadog.android.internal.sessionreplay.composition.CapturedChild
import com.datadog.android.internal.sessionreplay.composition.CapturedLayer
import com.datadog.android.internal.sessionreplay.composition.CapturedLayerKind
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.android.sessionreplay.internal.composition.mapper.CapturedViewData
import com.datadog.android.sessionreplay.internal.composition.mapper.CapturedViewMapperResult
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.concurrent.Executor
import java.util.concurrent.RejectedExecutionException

@ExtendWith(ForgeExtension::class)
@ForgeConfiguration(ForgeConfigurator::class)
internal class ExecutorCapturedSnapshotProcessorTest {
    private lateinit var fakeSnapshot: CapturedNativeSnapshot
    private lateinit var fakeFactory: DefaultCapturedIdentityFactory
    private val fakeContext = CaptureGenerationContext(1L, 0L, 100L, CaptureTimeProvider { 0L })
    private val fakeNone = CapturedViewData.Mapped(CapturedViewMapperResult.None)

    @BeforeEach
    fun setUp(@Forgery fakeInput: CapturedNativeSnapshot) {
        fakeSnapshot = fakeInput
        fakeFactory = DefaultCapturedIdentityFactory(fakeInput.scope)
    }

    private fun snapshot(viewData: CapturedViewData = fakeNone): CapturedNativeSnapshot = fakeSnapshot.copy(
        windows = fakeSnapshot.windows.map { window ->
            window.copy(views = window.views.map { it.copy(viewData = viewData) })
        }
    )

    @Test
    fun `M build on the executor W process`() {
        // Given
        val fakeQueue = mutableListOf<Runnable>()
        val testedProcessor = ExecutorCapturedSnapshotProcessor(Executor(fakeQueue::add), mock())
        val fakeResults = mutableListOf<SnapshotProcessingResult>()

        // When
        testedProcessor.process(SnapshotProcessingRequest(fakeContext, snapshot()), fakeResults::add)

        // Then
        assertThat(fakeResults).isEmpty()
        fakeQueue.single().run()
        val snapshot = (fakeResults.single() as SnapshotProcessingResult.Completed).snapshot
        assertThat(snapshot.timestamp).isEqualTo(fakeSnapshot.timestamp)
        assertThat(DefaultCapturedTreeValidator().validate(snapshot)).isEqualTo(CaptureValidationResult.Valid)
    }

    @Test
    fun `M pass through a completed tree W process`(@Forgery fakeWindow: CapturedWindow) {
        // Given
        val factory = DefaultCapturedIdentityFactory(fakeWindow.rootLayer.identity.scope)
        val fakeTree = CapturedFullSnapshot(
            fakeSnapshot.timestamp,
            factory.scope,
            CapturedLayer(
                factory.screenRoot(),
                CapturedLayerKind.SYNTHETIC_SCREEN_ROOT,
                fakeWindow.rootLayer.bounds,
                listOf(CapturedChild.Layer(fakeWindow.rootLayer.identity))
            ),
            fakeWindow.layers,
            fakeWindow.wireframes
        )
        val fakeResults = mutableListOf<SnapshotProcessingResult>()
        val testedProcessor = ExecutorCapturedSnapshotProcessor(Executor(Runnable::run), mock())

        // When
        testedProcessor.process(SnapshotProcessingRequest(fakeContext, fakeTree), fakeResults::add)

        // Then
        assertThat((fakeResults.single() as SnapshotProcessingResult.Completed).snapshot).isSameAs(fakeTree)
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
        val fakeError = RejectedExecutionException()
        val mockLogger: InternalLogger = mock()
        val testedProcessor = ExecutorCapturedSnapshotProcessor(Executor { throw fakeError }, mockLogger)

        // When
        testedProcessor.process(SnapshotProcessingRequest(fakeContext, snapshot()), fakeResults::add)

        // Then
        assertThat(fakeResults).containsExactly(SnapshotProcessingResult.Failed(fakeContext.id))
        val fakeMessage = argumentCaptor<() -> String>()
        verify(mockLogger).log(
            eq(InternalLogger.Level.ERROR),
            eq(InternalLogger.Target.TELEMETRY),
            fakeMessage.capture(),
            eq(fakeError),
            eq(true),
            eq(null)
        )
        assertThat(fakeMessage.firstValue()).isEqualTo("Unable to schedule composition snapshot construction")
    }

    @Test
    fun `M contain mapper failure W construct snapshot`() {
        // Given
        val fakeResults = mutableListOf<SnapshotProcessingResult>()
        val mockMapping: CapturedViewData = mock()
        val fakeError = IllegalStateException("Invalid copied value")
        whenever(mockMapping.map(any())).thenThrow(fakeError)
        val mockLogger: InternalLogger = mock()
        val testedProcessor = ExecutorCapturedSnapshotProcessor(Executor(Runnable::run), mockLogger)

        // When
        testedProcessor.process(SnapshotProcessingRequest(fakeContext, snapshot(mockMapping)), fakeResults::add)

        // Then
        assertThat(fakeResults).containsExactly(SnapshotProcessingResult.Failed(fakeContext.id))
        val fakeMessage = argumentCaptor<() -> String>()
        verify(mockLogger).log(
            eq(InternalLogger.Level.ERROR),
            eq(InternalLogger.Target.TELEMETRY),
            fakeMessage.capture(),
            eq(fakeError),
            eq(true),
            eq(null)
        )
        assertThat(fakeMessage.firstValue()).isEqualTo("Composition snapshot construction threw an exception")
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
        assertThat(checkNotNull(result).layers).hasSize(5000)
        assertThat(result.rootLayer.identity).isEqualTo(fakeWindowIdentity)
        assertThat(result.layers.first().identity).isEqualTo(fakeViews.last().identity)
    }
}
