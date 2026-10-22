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
import org.mockito.kotlin.mock
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

    @Mock
    lateinit var mockSdkCore: FeatureSdkCore

    @Mock
    lateinit var mockInternalCallback: SessionReplayInternalCallback

    @Mock
    lateinit var mockApplication: Application

    @BeforeEach
    fun `set up`() {
        whenever(mockSdkCore.internalLogger).thenReturn(mock<InternalLogger>())
        whenever(mockSdkCore.timeProvider).thenReturn(mock())
        whenever(mockSdkCore.createSingleThreadExecutorService(any())).thenReturn(mock())
        whenever(mockSdkCore.createScheduledExecutorService(any())).thenReturn(mock())
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
    fun `M dispatch construction independently W create { completion remains queued }`(
        @Forgery fakeSnapshot: CapturedNativeSnapshot
    ) {
        // Given
        val mockConstructionExecutor = mock<ExecutorService>()
        val mockCompletionExecutor = mock<ExecutorService>()
        whenever(mockSdkCore.createSingleThreadExecutorService("sr-composition-construction"))
            .thenReturn(mockConstructionExecutor)
        whenever(mockSdkCore.createSingleThreadExecutorService("sr-composition-processing"))
            .thenReturn(mockCompletionExecutor)
        val fakeConstructionTasks = ArrayDeque<Runnable>()
        val fakeCompletionTasks = ArrayDeque<Runnable>()
        val fakeCaptureTasks = ArrayDeque<Runnable>()
        doAnswer { fakeConstructionTasks.addLast(it.getArgument(0)); null }
            .whenever(mockConstructionExecutor).execute(any())
        doAnswer { fakeCompletionTasks.addLast(it.getArgument(0)); null }
            .whenever(mockCompletionExecutor).execute(any())
        val mockResources: Resources = mock()
        whenever(mockResources.displayMetrics).thenReturn(DisplayMetrics().apply { density = 1f })
        whenever(mockApplication.resources).thenReturn(mockResources)
        val mockMainLooper: Looper = mock()
        val mockRoot: View = mock()
        val mockObserver: ViewTreeObserver = mock()
        val mockWindow: Window = mock()
        val mockActivity: Activity = mock()
        whenever(mockObserver.isAlive).thenReturn(true)
        whenever(mockRoot.viewTreeObserver).thenReturn(mockObserver)
        whenever(mockWindow.peekDecorView()).thenReturn(mockRoot)
        whenever(mockActivity.window).thenReturn(mockWindow)
        whenever(mockInternalCallback.getCurrentActivity()).thenReturn(mockActivity)
        val testedFactory = createFactory(
            dynamicOptimizationEnabled = false,
            snapshotProducerFactory = { _, _ -> CapturedSnapshotProducer { fakeSnapshot } }
        )
        mockStatic(Looper::class.java).use { fakeLooper ->
            fakeLooper.`when`<Looper> { Looper.getMainLooper() }.thenReturn(mockMainLooper)
            mockConstruction(Handler::class.java) { handler, _ ->
                whenever(handler.looper).thenReturn(mockMainLooper)
                doAnswer { it.getArgument<Runnable>(0).run(); true }.whenever(handler).post(any())
                doAnswer {
                    // Leave periodic discovery refreshes queued; only advance capture timers.
                    if (it.getArgument<Long>(1) < 1_000L) fakeCaptureTasks.addLast(it.getArgument(0))
                    true
                }.whenever(handler).postDelayed(any<Runnable>(), any())
            }.use { fakeHandlers ->
                val testedPipeline = testedFactory.create(mock(), mock(), mockApplication)
                whenever(mockRoot.handler).thenReturn(fakeHandlers.constructed().first())
                testedPipeline.resumeRecorders()
                fakeCaptureTasks.removeFirst().run()
                assertThat(fakeConstructionTasks).hasSize(1)
                assertThat(fakeCompletionTasks).isEmpty()
                fakeConstructionTasks.removeFirst().run()
                assertThat(fakeCompletionTasks).hasSize(1)
                val fakeDrawListener = argumentCaptor<ViewTreeObserver.OnDrawListener>()
                verify(mockObserver).addOnDrawListener(fakeDrawListener.capture())

                // When: another draw arrives with the previous completion deliberately undrained.
                fakeDrawListener.firstValue.onDraw()
                fakeCaptureTasks.removeFirst().run()

                // Then: this fails if the factory wires construction to the completion executor.
                assertThat(fakeConstructionTasks).hasSize(1)
                assertThat(fakeCompletionTasks).hasSize(1)
                fakeConstructionTasks.removeFirst().run()
                assertThat(fakeCompletionTasks).hasSize(1)
                testedPipeline.stopRecorders()
                testedPipeline.stopProcessingRecords()
            }
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
        val windowSources = mutableListOf<ActiveWindowSource>()
        val rumContextProviders = mutableListOf<RumContextProvider>()
        val mockRumContextProvider = mock<RumContextProvider>()
        val testedFactory = createFactory(
            dynamicOptimizationEnabled = false,
            snapshotProducerFactory = { windowSource, rumContextProvider ->
                windowSources += windowSource
                rumContextProviders += rumContextProvider
                NoOpCapturedSnapshotProducer()
            }
        )

        // When
        testedFactory.create(mock<RecordWriter>(), mockRumContextProvider, mockApplication)

        // Then
        assertThat(windowSources).hasSize(1)
        assertThat(rumContextProviders).containsExactly(mockRumContextProvider)
    }

    @Test
    fun `M not create a recording time bank W create { dynamic optimization disabled }`() {
        // Given
        var timeBankConstructions = 0
        val testedFactory = createFactory(
            dynamicOptimizationEnabled = false,
            recordingTimeBankFactory = {
                timeBankConstructions++
                mock<TimeBank>()
            }
        )

        // When
        testedFactory.create(mock<RecordWriter>(), mock<RumContextProvider>(), mockApplication)

        // Then
        assertThat(timeBankConstructions).isZero()
    }

    @Test
    fun `M create a recording time bank W create { dynamic optimization enabled }`() {
        // Given
        var timeBankConstructions = 0
        val testedFactory = createFactory(
            dynamicOptimizationEnabled = true,
            recordingTimeBankFactory = {
                timeBankConstructions++
                mock<TimeBank>()
            }
        )

        // When
        testedFactory.create(mock<RecordWriter>(), mock<RumContextProvider>(), mockApplication)

        // Then
        assertThat(timeBankConstructions).isEqualTo(1)
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
    fun `M capture nothing W the placeholder producer runs`(forge: Forge) {
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
