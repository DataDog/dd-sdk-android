/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition

import android.app.Activity
import android.app.Application
import android.content.res.Resources
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.view.View
import android.view.ViewTreeObserver
import android.view.Window
import com.datadog.android.api.InternalLogger
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.sessionreplay.SessionReplayInternalCallback
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.android.sessionreplay.internal.TouchPrivacyManager
import com.datadog.android.sessionreplay.internal.recorder.Recorder
import com.datadog.android.sessionreplay.internal.recorder.TimeBank
import com.datadog.android.sessionreplay.internal.storage.RecordWriter
import com.datadog.android.sessionreplay.internal.utils.RumContextProvider
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.Mockito.mockConstruction
import org.mockito.Mockito.mockStatic
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.util.concurrent.ExecutorService

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(ForgeConfigurator::class)
internal class CompositionPipelineFactoryTest {

    private lateinit var mockSdkCore: FeatureSdkCore

    @Mock
    lateinit var mockInternalCallback: SessionReplayInternalCallback

    @Mock
    lateinit var mockApplication: Application

    @BeforeEach
    fun `set up`() {
        val mockLogger = mock<InternalLogger>()
        val mockClock = mock<com.datadog.android.internal.time.TimeProvider>()
        val mockExecutor = mock<ExecutorService>()
        val mockScheduledExecutor = mock<java.util.concurrent.ScheduledExecutorService>()
        mockSdkCore = mock {
            on { internalLogger } doReturn mockLogger
            on { timeProvider } doReturn mockClock
            on { createSingleThreadExecutorService(any()) } doReturn mockExecutor
            on { createScheduledExecutorService(any()) } doReturn mockScheduledExecutor
        }
    }

    @Test
    fun `M build a composition pipeline W create`() {
        // Given
        val testedFactory = createFactory(dynamicOptimizationEnabled = false)

        // When
        val result = testedFactory.create(mock<RecordWriter>(), mock<RumContextProvider>(), mockApplication)

        // Then
        assertThat(result).isInstanceOf(CompositionCapturePipeline::class.java)
    }

    @Test
    fun `M create dedicated executors W create`() {
        // Given
        val testedFactory = createFactory(dynamicOptimizationEnabled = false)

        // When
        testedFactory.create(mock<RecordWriter>(), mock<RumContextProvider>(), mockApplication)

        // Then
        verify(mockSdkCore).createSingleThreadExecutorService("sr-composition-construction")
        verify(mockSdkCore).createSingleThreadExecutorService("sr-composition-processing")
        verify(mockSdkCore).createScheduledExecutorService("sr-composition-expiry")
    }

    @Test
    fun `M queue construction before completion W resumeRecorders()`(
        @Forgery fakeSnapshot: CapturedNativeSnapshot
    ) {
        // Given
        val fakeFixture = ConstructionIsolationFixture(fakeSnapshot)
        fakeFixture.withPipeline { testedPipeline ->
            // When
            testedPipeline.resumeRecorders()
            fakeFixture.runCapture()

            // Then
            assertThat(fakeFixture.fakeConstructionTasks).hasSize(1)
            assertThat(fakeFixture.fakeCompletionTasks).isEmpty()
        }
    }

    @Test
    fun `M queue completion W run() { initial construction finishes }`(
        @Forgery fakeSnapshot: CapturedNativeSnapshot
    ) {
        // Given
        val fakeFixture = ConstructionIsolationFixture(fakeSnapshot)
        fakeFixture.withPipeline { testedPipeline ->
            testedPipeline.resumeRecorders()
            fakeFixture.runCapture()
            val fakeConstructionTask = fakeFixture.fakeConstructionTasks.removeFirst()

            // When
            fakeConstructionTask.run()

            // Then
            assertThat(fakeFixture.fakeCompletionTasks).hasSize(1)
        }
    }

    @Test
    fun `M queue construction W onDraw() { completion remains queued }`(
        @Forgery fakeSnapshot: CapturedNativeSnapshot
    ) {
        // Given
        val fakeFixture = ConstructionIsolationFixture(fakeSnapshot)
        fakeFixture.withPipeline { testedPipeline ->
            fakeFixture.queueFirstCompletion(testedPipeline)
            val fakeDrawListener = fakeFixture.drawListener()

            // When
            fakeDrawListener.onDraw()
            fakeFixture.runCapture()

            // Then
            assertThat(fakeFixture.fakeConstructionTasks).hasSize(1)
            assertThat(fakeFixture.fakeCompletionTasks).hasSize(1)
        }
    }

    @Test
    fun `M retain one completion task W run() { another construction finishes while completion remains queued }`(
        @Forgery fakeSnapshot: CapturedNativeSnapshot
    ) {
        // Given
        val fakeFixture = ConstructionIsolationFixture(fakeSnapshot)
        fakeFixture.withPipeline { testedPipeline ->
            fakeFixture.queueFirstCompletion(testedPipeline)
            fakeFixture.drawListener().onDraw()
            fakeFixture.runCapture()
            val fakeConstructionTask = fakeFixture.fakeConstructionTasks.removeFirst()

            // When
            fakeConstructionTask.run()

            // Then
            assertThat(fakeFixture.fakeCompletionTasks).hasSize(1)
        }
    }

    @Test
    fun `M shut down construction and completion W stopProcessingRecords`() {
        // Given
        val mockConstructionExecutor = mock<ExecutorService>()
        val mockCompletionExecutor = mock<ExecutorService>()
        whenever(mockSdkCore.createSingleThreadExecutorService("sr-composition-construction"))
            .thenReturn(mockConstructionExecutor)
        whenever(mockSdkCore.createSingleThreadExecutorService("sr-composition-processing"))
            .thenReturn(mockCompletionExecutor)
        val testedPipeline = createFactory(dynamicOptimizationEnabled = false)
            .create(mock<RecordWriter>(), mock<RumContextProvider>(), mockApplication)

        // When
        testedPipeline.stopProcessingRecords()

        // Then
        verify(mockConstructionExecutor).shutdownNow()
        verify(mockCompletionExecutor).shutdownNow()
    }

    @Test
    fun `M give the producer the shared window source and RUM context provider W create`() {
        // Given
        val fakeWindowSources = mutableListOf<ActiveWindowSource>()
        val fakeRumContextProviders = mutableListOf<RumContextProvider>()
        val mockRumContextProvider = mock<RumContextProvider>()
        val testedFactory = createFactory(
            dynamicOptimizationEnabled = false,
            snapshotProducerFactory = { windowSource, rumContextProvider ->
                fakeWindowSources += windowSource
                fakeRumContextProviders += rumContextProvider
                NoOpCapturedSnapshotProducer()
            }
        )

        // When
        testedFactory.create(mock<RecordWriter>(), mockRumContextProvider, mockApplication)

        // Then
        assertThat(fakeWindowSources).hasSize(1)
        assertThat(fakeRumContextProviders).containsExactly(mockRumContextProvider)
    }

    @Test
    fun `M not create a recording time bank W create { dynamic optimization disabled }`() {
        // Given
        var fakeTimeBankConstructions = 0
        val testedFactory = createFactory(
            dynamicOptimizationEnabled = false,
            recordingTimeBankFactory = {
                fakeTimeBankConstructions++
                mock<TimeBank>()
            }
        )

        // When
        testedFactory.create(mock<RecordWriter>(), mock<RumContextProvider>(), mockApplication)

        // Then
        assertThat(fakeTimeBankConstructions).isZero()
    }

    @Test
    fun `M create a recording time bank W create { dynamic optimization enabled }`() {
        // Given
        var fakeTimeBankConstructions = 0
        val testedFactory = createFactory(
            dynamicOptimizationEnabled = true,
            recordingTimeBankFactory = {
                fakeTimeBankConstructions++
                mock<TimeBank>()
            }
        )

        // When
        testedFactory.create(mock<RecordWriter>(), mock<RumContextProvider>(), mockApplication)

        // Then
        assertThat(fakeTimeBankConstructions).isEqualTo(1)
    }

    @Test
    fun `M read the current activity W create`() {
        // Given
        whenever(mockInternalCallback.getCurrentActivity()).thenReturn(mock<Activity>())
        val testedFactory = createFactory(dynamicOptimizationEnabled = false)

        // When
        testedFactory.create(mock<RecordWriter>(), mock<RumContextProvider>(), mockApplication)

        // Then
        verify(mockInternalCallback).getCurrentActivity()
    }

    @Test
    fun `M capture nothing W capture() { the placeholder producer runs }`(forge: Forge) {
        // Given
        val fakeGeneration = CaptureGenerationContext(
            id = forge.aLong(min = 1L, max = 1_000L),
            startedAtNs = 0L,
            deadlineNs = Long.MAX_VALUE,
            timeProvider = { 0L }
        )

        // When
        val result = NoOpCapturedSnapshotProducer().capture(fakeGeneration)

        // Then
        assertThat(result).isNull()
    }

    private inner class ConstructionIsolationFixture(private val fakeSnapshot: CapturedNativeSnapshot) {
        val fakeConstructionTasks = ArrayDeque<Runnable>()
        val fakeCompletionTasks = ArrayDeque<Runnable>()
        private val fakeCaptureTasks = ArrayDeque<Runnable>()
        private val mockConstructionExecutor: ExecutorService = mock()
        private val mockCompletionExecutor: ExecutorService = mock()
        private val mockMainLooper: Looper = mock()
        private val mockObserver: ViewTreeObserver = mock { on { isAlive } doReturn true }
        private val mockRoot: View = mock { on { viewTreeObserver } doReturn mockObserver }
        private val mockWindow: Window = mock { on { peekDecorView() } doReturn mockRoot }
        private val mockActivity: Activity = mock { on { window } doReturn mockWindow }

        init {
            whenever(mockSdkCore.createSingleThreadExecutorService("sr-composition-construction"))
                .thenReturn(mockConstructionExecutor)
            whenever(mockSdkCore.createSingleThreadExecutorService("sr-composition-processing"))
                .thenReturn(mockCompletionExecutor)
            doAnswer { fakeConstructionTasks.addLast(it.getArgument(0)); null }
                .whenever(mockConstructionExecutor).execute(any())
            doAnswer { fakeCompletionTasks.addLast(it.getArgument(0)); null }
                .whenever(mockCompletionExecutor).execute(any())
            val fakeMetrics = DisplayMetrics().apply { density = 1f }
            val mockResources: Resources = mock { on { displayMetrics } doReturn fakeMetrics }
            whenever(mockApplication.resources).thenReturn(mockResources)
            whenever(mockInternalCallback.getCurrentActivity()).thenReturn(mockActivity)
        }

        fun withPipeline(block: (Recorder) -> Unit) {
            val testedFactory = createFactory(
                dynamicOptimizationEnabled = false,
                snapshotProducerFactory = { _, _ -> CapturedSnapshotProducer { fakeSnapshot } }
            )
            mockStatic(Looper::class.java).use { fakeLooper ->
                fakeLooper.`when`<Looper> { Looper.getMainLooper() }.thenReturn(mockMainLooper)
                mockConstruction(Handler::class.java) { mockHandler, _ ->
                    mockHandler.stub {
                        on { looper } doReturn mockMainLooper
                        on { post(any()) } doAnswer { invocation ->
                            invocation.getArgument<Runnable>(0).run()
                            true
                        }
                        on { postDelayed(any<Runnable>(), any()) } doAnswer { invocation ->
                            if (invocation.getArgument<Long>(1) < 1_000L) {
                                fakeCaptureTasks.addLast(invocation.getArgument(0))
                            }
                            true
                        }
                    }
                }.use { fakeHandlers ->
                    val testedPipeline = testedFactory.create(mock(), mock(), mockApplication)
                    whenever(mockRoot.handler).thenReturn(fakeHandlers.constructed().first())
                    try {
                        block(testedPipeline)
                    } finally {
                        testedPipeline.stopRecorders()
                        testedPipeline.stopProcessingRecords()
                    }
                }
            }
        }

        fun queueFirstCompletion(testedPipeline: Recorder) {
            testedPipeline.resumeRecorders()
            runCapture()
            fakeConstructionTasks.removeFirst().run()
        }

        fun runCapture() = fakeCaptureTasks.removeFirst().run()

        fun drawListener(): ViewTreeObserver.OnDrawListener {
            val fakeDrawListener = argumentCaptor<ViewTreeObserver.OnDrawListener>()
            verify(mockObserver).addOnDrawListener(fakeDrawListener.capture())
            return fakeDrawListener.firstValue
        }
    }

    private fun createFactory(
        dynamicOptimizationEnabled: Boolean,
        snapshotProducerFactory: (ActiveWindowSource, RumContextProvider) -> CapturedSnapshotProducer = { _, _ ->
            NoOpCapturedSnapshotProducer()
        },
        recordingTimeBankFactory: () -> TimeBank = { mock() }
    ) = DefaultCompositionPipelineFactory(
        sdkCore = mockSdkCore,
        internalCallback = mockInternalCallback,
        touchPrivacyManager = mock<TouchPrivacyManager>(),
        dynamicOptimizationEnabled = dynamicOptimizationEnabled,
        snapshotProducerFactory = snapshotProducerFactory,
        recordingTimeBankFactory = recordingTimeBankFactory
    )
}
