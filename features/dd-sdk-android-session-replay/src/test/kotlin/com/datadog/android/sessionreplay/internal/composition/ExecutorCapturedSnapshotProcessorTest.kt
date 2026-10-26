/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition

import com.datadog.android.api.InternalLogger
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.android.sessionreplay.internal.composition.mapper.CapturedViewData
import com.datadog.android.sessionreplay.internal.composition.mapper.CapturedViewMapperResult
import com.datadog.android.utils.verifyLog
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.util.concurrent.Executor
import java.util.concurrent.RejectedExecutionException

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@ForgeConfiguration(ForgeConfigurator::class)
@MockitoSettings(strictness = Strictness.LENIENT)
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
    fun `M defer snapshot construction W process() { executor task queued }`() {
        // Given
        val fakeQueue = mutableListOf<Runnable>()
        val testedProcessor = ExecutorCapturedSnapshotProcessor(Executor(fakeQueue::add), mock())
        val fakeResults = mutableListOf<SnapshotProcessingResult>()

        // When
        testedProcessor.process(SnapshotProcessingRequest(fakeContext, snapshot()), fakeResults::add)

        // Then
        assertThat(fakeResults).isEmpty()
    }

    @Test
    fun `M construct the queued snapshot W run() { executor starts processing }`() {
        // Given
        val fakeQueue = mutableListOf<Runnable>()
        val testedProcessor = ExecutorCapturedSnapshotProcessor(Executor(fakeQueue::add), mock())
        val fakeResults = mutableListOf<SnapshotProcessingResult>()

        testedProcessor.process(SnapshotProcessingRequest(fakeContext, snapshot()), fakeResults::add)

        // When
        fakeQueue.single().run()
        val snapshot = (fakeResults.single() as SnapshotProcessingResult.Completed).snapshot

        // Then
        assertThat(snapshot.timestamp).isEqualTo(fakeSnapshot.timestamp)
        assertThat(DefaultCapturedTreeValidator().validate(snapshot)).isEqualTo(CaptureValidationResult.Valid)
    }

    @Test
    fun `M pass through a completed tree W process`(@Forgery fakeWindow: CapturedWindow) {
        // Given
        val fakeFactory = DefaultCapturedIdentityFactory(fakeWindow.rootLayer.identity.scope)
        val fakeTree = CapturedFullSnapshot(
            fakeSnapshot.timestamp,
            fakeFactory.scope,
            CapturedLayer(
                fakeFactory.screenRoot(),
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
    fun `M retain completed generation W process() + cancel() { callback completes before process returns }`() {
        // Given
        val fakeResults = mutableListOf<SnapshotProcessingResult>()
        val testedProcessor = ExecutorCapturedSnapshotProcessor(Executor(Runnable::run), mock())

        // When
        val work = testedProcessor.process(SnapshotProcessingRequest(fakeContext, snapshot()), fakeResults::add)
        work.cancel()

        // Then
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
        val testedWork = testedProcessor.process(SnapshotProcessingRequest(fakeContext, snapshot()), fakeResults::add)

        // When
        testedWork.cancel()
        fakeQueue.single().run()

        // Then
        assertThat(fakeContext.isActive()).isFalse()
        assertThat(fakeResults).isEmpty()
    }

    @Test
    fun `M stop construction W run() { cancel during mapping }`() {
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
    fun `M report failure W process() { executor rejects work }`() {
        // Given
        val fakeResults = mutableListOf<SnapshotProcessingResult>()
        val fakeError = RejectedExecutionException()
        val mockLogger: InternalLogger = mock()
        val testedProcessor = ExecutorCapturedSnapshotProcessor(Executor { throw fakeError }, mockLogger)

        // When
        testedProcessor.process(SnapshotProcessingRequest(fakeContext, snapshot()), fakeResults::add)

        // Then
        assertThat(fakeResults).containsExactly(SnapshotProcessingResult.Failed(fakeContext.id))
        mockLogger.verifyLog(
            InternalLogger.Level.ERROR,
            InternalLogger.Target.TELEMETRY,
            "Unable to schedule composition snapshot construction",
            throwable = fakeError,
            onlyOnce = true
        )
    }

    @Test
    fun `M contain mapper failure W process() { copied mapper throws }`() {
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
        mockLogger.verifyLog(
            InternalLogger.Level.ERROR,
            InternalLogger.Target.TELEMETRY,
            "Composition snapshot construction threw an exception",
            throwable = fakeError,
            onlyOnce = true
        )
    }

    @Test
    fun `M construct deep hierarchy without recursion W buildWindow()`() {
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
        val window = checkNotNull(result) { "Expected the deep hierarchy to be built" }
        assertThat(window.layers).hasSize(5000)
        assertThat(window.rootLayer.identity).isEqualTo(fakeWindowIdentity)
        assertThat(window.layers.first().identity).isEqualTo(fakeViews.last().identity)
    }
}
