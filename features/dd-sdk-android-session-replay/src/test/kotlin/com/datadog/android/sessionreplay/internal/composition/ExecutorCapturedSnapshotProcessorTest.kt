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
import com.datadog.android.internal.sessionreplay.composition.CapturedWireframe
import com.datadog.android.internal.sessionreplay.composition.PixelResource
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.android.sessionreplay.internal.composition.mapper.CapturedViewData
import com.datadog.android.sessionreplay.internal.composition.mapper.CapturedViewMapperResult
import com.datadog.android.sessionreplay.internal.composition.mapper.PendingViewMapping
import com.datadog.android.sessionreplay.model.MobileSegment
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
import org.mockito.kotlin.verifyNoInteractions
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
    fun `M wait without occupying the worker W process() { asynchronous mapper }`() {
        // Given
        val fakeMapping = PendingViewMapping(true)
        val fakeImage = MobileSegment.Wireframe.ImageWireframe(1, 0, 0, 10, 10)
        fakeMapping.jobStarted()
        fakeMapping.mapped(listOf(fakeImage))
        val fakeInput = snapshot(fakeMapping).let { input ->
            input.copy(
                windows = input.windows.map {
                    it.copy(pendingMappings = listOf(fakeMapping))
                }
            )
        }
        val fakeQueue = mutableListOf<Runnable>()
        val fakeResults = mutableListOf<SnapshotProcessingResult>()
        val testedProcessor = ExecutorCapturedSnapshotProcessor(Executor(fakeQueue::add), mock())

        // When
        testedProcessor.process(SnapshotProcessingRequest(fakeContext, fakeInput), fakeResults::add)

        // Then
        assertThat(fakeQueue).isEmpty()
        val fakeCallbackThread = Thread {
            fakeImage.resourceId = "resolved"
            fakeImage.isEmpty = false
            fakeMapping.jobFinished()
        }
        fakeCallbackThread.start()
        fakeCallbackThread.join()
        assertThat(fakeQueue).hasSize(1)
        fakeQueue.single().run()
        val result = (fakeResults.single() as SnapshotProcessingResult.Completed).snapshot
        assertThat(result.wireframes.filterIsInstance<CapturedWireframe.Pixel>()).isNotEmpty().allMatch {
            it.resource == PixelResource.Resolved("resolved")
        }
    }

    @Test
    fun `M ignore late mapper callbacks W cancel() { construction waiting }`() {
        // Given
        val fakeMapping = PendingViewMapping(true)
        fakeMapping.jobStarted()
        fakeMapping.mapped(emptyList())
        val fakeInput = snapshot(fakeMapping).let { input ->
            input.copy(
                windows = input.windows.map {
                    it.copy(pendingMappings = listOf(fakeMapping))
                }
            )
        }
        val fakeQueue = mutableListOf<Runnable>()
        val fakeResults = mutableListOf<SnapshotProcessingResult>()
        val mockRecapture: RecaptureTrigger = mock()
        val testedProcessor = ExecutorCapturedSnapshotProcessor(
            Executor(fakeQueue::add),
            mock(),
            recaptureTrigger = mockRecapture
        )
        val work = testedProcessor.process(SnapshotProcessingRequest(fakeContext, fakeInput), fakeResults::add)

        // When
        work.cancel()
        fakeMapping.jobFinished()
        fakeMapping.jobFinished()

        // Then
        assertThat(fakeQueue).isEmpty()
        assertThat(fakeResults).isEmpty()
        assertThat(fakeContext.isActive()).isFalse()
        verifyNoInteractions(mockRecapture)
    }

    @Test
    fun `M bound late mapper retries W process() { reset after a successful capture }`() {
        // Given
        var fakeNow = 0L
        var fakeRecaptures = 0
        val testedProcessor = ExecutorCapturedSnapshotProcessor(
            Executor(Runnable::run),
            mock(),
            recaptureTrigger = RecaptureTrigger { fakeRecaptures++ }
        )
        val fakeResults = mutableListOf<SnapshotProcessingResult>()
        fun completeLate() {
            fakeNow = 0L
            val fakeGeneration = CaptureGenerationContext(1L, 0L, 100L, CaptureTimeProvider { fakeNow })
            val fakeMapping = PendingViewMapping(true)
            fakeMapping.jobStarted()
            fakeMapping.mapped(emptyList())
            val fakeInput = snapshot().let { input ->
                input.copy(windows = input.windows.map { it.copy(pendingMappings = listOf(fakeMapping)) })
            }
            testedProcessor.process(SnapshotProcessingRequest(fakeGeneration, fakeInput), fakeResults::add)
            fakeNow = 100L
            fakeMapping.jobFinished()
            fakeMapping.jobFinished()
        }

        // When / Then
        repeat(3) { completeLate() }
        assertThat(fakeRecaptures).isEqualTo(1)
        assertThat(fakeResults).isEmpty()
        testedProcessor.process(SnapshotProcessingRequest(fakeContext, snapshot()), fakeResults::add)
        assertThat(fakeResults).hasSize(1)
        completeLate()
        assertThat(fakeRecaptures).isEqualTo(2)
    }

    @Test
    fun `M contain linkage errors W process() { copied mapper is incompatible }`() {
        // Given
        val mockMapping: CapturedViewData = mock()
        whenever(mockMapping.map(any())).thenThrow(NoClassDefFoundError("Extension dependency"))
        val fakeResults = mutableListOf<SnapshotProcessingResult>()
        val testedProcessor = ExecutorCapturedSnapshotProcessor(Executor(Runnable::run), mock())

        // When
        testedProcessor.process(SnapshotProcessingRequest(fakeContext, snapshot(mockMapping)), fakeResults::add)

        // Then
        assertThat(fakeResults).containsExactly(SnapshotProcessingResult.Failed(fakeContext.id))
    }

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
