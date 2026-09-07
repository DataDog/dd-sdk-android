/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition

import android.app.Application
import android.os.Handler
import android.view.View
import android.view.ViewTreeObserver
import android.view.Window
import com.datadog.android.api.InternalLogger
import com.datadog.android.internal.time.TimeProvider
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.android.utils.verifyLog
import fr.xgouchet.elmyr.annotation.LongForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@Extensions(
    ExtendWith(ForgeExtension::class)
)
@ForgeConfiguration(ForgeConfigurator::class)
internal class AndroidSnapshotCaptureLifecycleTest {

    @Test
    fun `M signal source window W onDraw`() {
        // Given
        val view = mock<View>()
        val changedWindows = mutableListOf<List<View>>()
        val listener = CompositionOnDrawListener(view) { changedWindows += it }

        // When
        listener.onDraw()

        // Then
        assertThat(changedWindows).containsExactly(listOf(view))
    }

    @Test
    fun `M update active windows and listener registrations W intercept`() {
        // Given
        val observer = mock<ViewTreeObserver>()
        whenever(observer.isAlive).thenReturn(true)
        val view = mock<View>()
        whenever(view.viewTreeObserver).thenReturn(observer)
        val changedWindows = mutableListOf<List<View>>()
        val source = ActiveWindowSource()
        val interceptor = CompositionViewOnDrawInterceptor(source, { changedWindows += it }, mock())

        // When
        interceptor.intercept(listOf(view))
        val addedListener = argumentCaptor<ViewTreeObserver.OnDrawListener>()
        verify(observer).addOnDrawListener(addedListener.capture())
        interceptor.intercept(emptyList())

        // Then
        verify(observer).removeOnDrawListener(addedListener.firstValue)
        assertThat(source.currentWindows()).isEmpty()
        assertThat(changedWindows).containsExactly(listOf(view), emptyList())
    }

    @Test
    fun `M skip listener registration W intercept { viewTreeObserver is not alive }`() {
        // Given
        val mockObserver = mock<ViewTreeObserver>()
        whenever(mockObserver.isAlive).thenReturn(false)
        val mockView = mock<View>()
        whenever(mockView.viewTreeObserver).thenReturn(mockObserver)
        val source = ActiveWindowSource()
        val testedInterceptor = CompositionViewOnDrawInterceptor(source, { }, mock())

        // When
        testedInterceptor.intercept(listOf(mockView))

        // Then
        verify(mockObserver, never()).addOnDrawListener(any())
        assertThat(source.currentWindows()).containsExactly(mockView)
    }

    @Test
    fun `M report a maintainer warning W intercept { addOnDrawListener throws }`() {
        // Given
        val mockObserver = mock<ViewTreeObserver>()
        whenever(mockObserver.isAlive).thenReturn(true)
        val fakeError = IllegalStateException("addOnDrawListener failed")
        whenever(mockObserver.addOnDrawListener(any())).doThrow(fakeError)
        val mockView = mock<View>()
        whenever(mockView.viewTreeObserver).thenReturn(mockObserver)
        val mockInternalLogger = mock<InternalLogger>()
        val testedInterceptor = CompositionViewOnDrawInterceptor(ActiveWindowSource(), { }, mockInternalLogger)

        // When
        testedInterceptor.intercept(listOf(mockView))

        // Then
        mockInternalLogger.verifyLog(
            InternalLogger.Level.WARN,
            InternalLogger.Target.TELEMETRY,
            "Unable to add composition onDrawListener on viewTreeObserver",
            fakeError
        )
    }

    @Test
    fun `M skip listener removal W stop { viewTreeObserver is not alive }`() {
        // Given
        val mockObserver = mock<ViewTreeObserver>()
        whenever(mockObserver.isAlive).thenReturn(true)
        val mockView = mock<View>()
        whenever(mockView.viewTreeObserver).thenReturn(mockObserver)
        val testedInterceptor = CompositionViewOnDrawInterceptor(ActiveWindowSource(), { }, mock())
        testedInterceptor.intercept(listOf(mockView))

        // When
        whenever(mockObserver.isAlive).thenReturn(false)
        testedInterceptor.stop()

        // Then
        verify(mockObserver, never()).removeOnDrawListener(any())
    }

    @Test
    fun `M report a maintainer warning W stop { removeOnDrawListener throws }`() {
        // Given
        val mockObserver = mock<ViewTreeObserver>()
        whenever(mockObserver.isAlive).thenReturn(true)
        val mockView = mock<View>()
        whenever(mockView.viewTreeObserver).thenReturn(mockObserver)
        val mockInternalLogger = mock<InternalLogger>()
        val testedInterceptor = CompositionViewOnDrawInterceptor(ActiveWindowSource(), { }, mockInternalLogger)
        testedInterceptor.intercept(listOf(mockView))
        val fakeError = IllegalStateException("removeOnDrawListener failed")
        whenever(mockObserver.removeOnDrawListener(any())).doThrow(fakeError)

        // When
        testedInterceptor.stop()

        // Then
        mockInternalLogger.verifyLog(
            InternalLogger.Level.WARN,
            InternalLogger.Target.TELEMETRY,
            "Unable to remove composition onDrawListener on viewTreeObserver",
            fakeError
        )
    }

    @Test
    fun `M remove every listener and clear the window source W stop`() {
        // Given
        val mockObserver = mock<ViewTreeObserver>()
        whenever(mockObserver.isAlive).thenReturn(true)
        val mockView = mock<View>()
        whenever(mockView.viewTreeObserver).thenReturn(mockObserver)
        val source = ActiveWindowSource()
        val testedInterceptor = CompositionViewOnDrawInterceptor(source, { }, mock())
        testedInterceptor.intercept(listOf(mockView))
        val addedListener = argumentCaptor<ViewTreeObserver.OnDrawListener>()
        verify(mockObserver).addOnDrawListener(addedListener.capture())

        // When
        testedInterceptor.stop()

        // Then
        verify(mockObserver).removeOnDrawListener(addedListener.firstValue)
        assertThat(source.currentWindows()).isEmpty()
    }

    @Test
    fun `M refresh interception W lifecycle starts and windows change`() {
        // Given
        val application = mock<Application>()
        val interceptor = mock<CompositionViewOnDrawInterceptor>()
        val handler = immediateHandler()
        val views = listOf(mock<View>())
        val lifecycle = AndroidSnapshotCaptureLifecycle(
            application = application,
            interceptor = interceptor,
            touchInterceptor = mock(),
            internalLogger = mock(),
            uiHandler = handler,
            windowProvider = { views }
        )

        // When
        lifecycle.registerCallbacks()
        lifecycle.start()
        lifecycle.onWindowsAdded(listOf(mock<Window>()))
        lifecycle.stop()
        lifecycle.onWindowsRemoved(listOf(mock<Window>()))
        lifecycle.unregisterCallbacks()

        // Then
        verify(application).registerActivityLifecycleCallbacks(any())
        verify(interceptor, org.mockito.kotlin.times(2)).intercept(views)
        verify(interceptor).stop()
        verify(application).unregisterActivityLifecycleCallbacks(any())
    }

    @Test
    fun `M intercept the activity decor view W start { window manager reports no windows }`() {
        // Given
        // ActivityThread registers an activity's decor view with the window manager only after it
        // has dispatched onActivityResumed, so the window manager legitimately reports nothing at
        // the moment the pipeline starts. The tracked window must still be intercepted.
        val mockDecorView = mock<View>()
        val mockInterceptor = mock<CompositionViewOnDrawInterceptor>()
        val testedLifecycle = AndroidSnapshotCaptureLifecycle(
            application = mock(),
            interceptor = mockInterceptor,
            touchInterceptor = mock(),
            internalLogger = mock(),
            currentActivity = activityShowing(mockDecorView),
            uiHandler = immediateHandler(),
            windowProvider = { emptyList() }
        )

        // When
        testedLifecycle.start()

        // Then
        verify(mockInterceptor).intercept(listOf(mockDecorView))
    }

    @Test
    fun `M intercept both sources W start { window manager reports an unreported window }`() {
        // Given
        val mockActivityDecorView = mock<View>()
        val mockDialogDecorView = mock<View>()
        val mockInterceptor = mock<CompositionViewOnDrawInterceptor>()
        val testedLifecycle = AndroidSnapshotCaptureLifecycle(
            application = mock(),
            interceptor = mockInterceptor,
            touchInterceptor = mock(),
            internalLogger = mock(),
            currentActivity = activityShowing(mockActivityDecorView),
            uiHandler = immediateHandler(),
            windowProvider = { listOf(mockDialogDecorView) }
        )

        // When
        testedLifecycle.start()

        // Then
        verify(mockInterceptor).intercept(listOf(mockActivityDecorView, mockDialogDecorView))
    }

    @Test
    fun `M intercept a decor view once W start { reported by both sources }`() {
        // Given
        val mockDecorView = mock<View>()
        val mockInterceptor = mock<CompositionViewOnDrawInterceptor>()
        val testedLifecycle = AndroidSnapshotCaptureLifecycle(
            application = mock(),
            interceptor = mockInterceptor,
            touchInterceptor = mock(),
            internalLogger = mock(),
            currentActivity = activityShowing(mockDecorView),
            uiHandler = immediateHandler(),
            windowProvider = { listOf(mockDecorView) }
        )

        // When
        testedLifecycle.start()

        // Then
        verify(mockInterceptor).intercept(listOf(mockDecorView))
    }

    @Test
    fun `M intercept the resumed window W activity resumes { window manager reports no windows }`() {
        // Given
        // Drives the real Android path: the activity lifecycle callback the pipeline registers is
        // dispatched before the decor view reaches the window manager.
        val mockDecorView = mock<View>()
        val mockApplication = mock<Application>()
        val mockInterceptor = mock<CompositionViewOnDrawInterceptor>()
        val testedLifecycle = AndroidSnapshotCaptureLifecycle(
            application = mockApplication,
            interceptor = mockInterceptor,
            touchInterceptor = mock(),
            internalLogger = mock(),
            uiHandler = immediateHandler(),
            windowProvider = { emptyList() }
        )
        testedLifecycle.registerCallbacks()
        testedLifecycle.start()
        val registeredCallbacks = argumentCaptor<Application.ActivityLifecycleCallbacks>()
        verify(mockApplication).registerActivityLifecycleCallbacks(registeredCallbacks.capture())

        // When
        registeredCallbacks.firstValue.onActivityResumed(activityShowing(mockDecorView))

        // Then
        verify(mockInterceptor).intercept(listOf(mockDecorView))
    }

    @Test
    fun `M intercept both sources W start touch { window manager reports an unreported window }`() {
        // Given
        val mockActivityDecorView = mock<View>()
        val mockActivityWindow = mock<Window>()
        whenever(mockActivityWindow.peekDecorView()).thenReturn(mockActivityDecorView)
        val mockActivity = mock<android.app.Activity>().also { whenever(it.window).thenReturn(mockActivityWindow) }
        val mockDialogDecorView = mock<View>()
        val mockDialogWindow = mock<Window>()
        val mockTouchInterceptor = mock<CompositionWindowTouchInterceptor>()
        val testedLifecycle = AndroidSnapshotCaptureLifecycle(
            application = mock(),
            interceptor = mock(),
            touchInterceptor = mockTouchInterceptor,
            internalLogger = mock(),
            currentActivity = mockActivity,
            uiHandler = immediateHandler(),
            windowProvider = { listOf(mockDialogDecorView) },
            windowFromDecorView = { if (it === mockDialogDecorView) mockDialogWindow else null }
        )

        // When
        testedLifecycle.start()

        // Then
        verify(mockTouchInterceptor).intercept(listOf(mockActivityWindow, mockDialogWindow))
    }

    @Test
    fun `M skip an untracked window W start touch { window cannot be resolved }`() {
        // Given
        val mockDialogDecorView = mock<View>()
        val mockTouchInterceptor = mock<CompositionWindowTouchInterceptor>()
        val testedLifecycle = AndroidSnapshotCaptureLifecycle(
            application = mock(),
            interceptor = mock(),
            touchInterceptor = mockTouchInterceptor,
            internalLogger = mock(),
            uiHandler = immediateHandler(),
            windowProvider = { listOf(mockDialogDecorView) },
            windowFromDecorView = { null }
        )

        // When
        testedLifecycle.start()

        // Then
        verify(mockTouchInterceptor).intercept(emptyList())
    }

    @Test
    fun `M stop the touch interceptor W stop`() {
        // Given
        val mockTouchInterceptor = mock<CompositionWindowTouchInterceptor>()
        val testedLifecycle = AndroidSnapshotCaptureLifecycle(
            application = mock(),
            interceptor = mock(),
            touchInterceptor = mockTouchInterceptor,
            internalLogger = mock(),
            uiHandler = immediateHandler(),
            windowProvider = { emptyList() }
        )
        testedLifecycle.start()

        // When
        testedLifecycle.stop()

        // Then
        verify(mockTouchInterceptor).stop()
    }

    @Test
    fun `M schedule a periodic refresh W start`() {
        // Given
        val handler = immediateHandler()
        val lifecycle = AndroidSnapshotCaptureLifecycle(
            application = mock(),
            interceptor = mock(),
            touchInterceptor = mock(),
            internalLogger = mock(),
            uiHandler = handler,
            windowProvider = { emptyList() }
        )

        // When
        lifecycle.start()

        // Then
        verify(handler).postDelayed(any<Runnable>(), org.mockito.kotlin.eq(1_000L))
    }

    @Test
    fun `M pick up an untracked window W periodic refresh fires`() {
        // Given
        val mockInterceptor = mock<CompositionViewOnDrawInterceptor>()
        val handler = immediateHandler()
        val delayedRunnable = argumentCaptor<Runnable>()
        var windows = emptyList<View>()
        val lifecycle = AndroidSnapshotCaptureLifecycle(
            application = mock(),
            interceptor = mockInterceptor,
            touchInterceptor = mock(),
            internalLogger = mock(),
            uiHandler = handler,
            windowProvider = { windows }
        )
        lifecycle.start()
        verify(handler).postDelayed(delayedRunnable.capture(), any())

        // When
        val mockDialogDecorView = mock<View>()
        windows = listOf(mockDialogDecorView)
        delayedRunnable.firstValue.run()

        // Then
        verify(mockInterceptor).intercept(listOf(mockDialogDecorView))
    }

    @Test
    fun `M reschedule itself W periodic refresh fires { still running }`() {
        // Given
        val handler = immediateHandler()
        val delayedRunnable = argumentCaptor<Runnable>()
        val lifecycle = AndroidSnapshotCaptureLifecycle(
            application = mock(),
            interceptor = mock(),
            touchInterceptor = mock(),
            internalLogger = mock(),
            uiHandler = handler,
            windowProvider = { emptyList() }
        )
        lifecycle.start()
        verify(handler).postDelayed(delayedRunnable.capture(), any())

        // When
        delayedRunnable.firstValue.run()

        // Then
        verify(handler, org.mockito.kotlin.times(2)).postDelayed(any<Runnable>(), any())
    }

    @Test
    fun `M stop refreshing and rescheduling W periodic refresh fires { stopped }`() {
        // Given
        val mockInterceptor = mock<CompositionViewOnDrawInterceptor>()
        val handler = immediateHandler()
        val delayedRunnable = argumentCaptor<Runnable>()
        val lifecycle = AndroidSnapshotCaptureLifecycle(
            application = mock(),
            interceptor = mockInterceptor,
            touchInterceptor = mock(),
            internalLogger = mock(),
            uiHandler = handler,
            windowProvider = { emptyList() }
        )
        lifecycle.start()
        verify(handler).postDelayed(delayedRunnable.capture(), any())
        lifecycle.stop()
        org.mockito.kotlin.clearInvocations(mockInterceptor, handler)

        // When
        delayedRunnable.firstValue.run()

        // Then
        verify(mockInterceptor, never()).intercept(any())
        verify(handler, never()).postDelayed(any<Runnable>(), any())
    }

    @Test
    fun `M schedule and cancel delayed task W use handler scheduler`() {
        // Given
        val handler = mock<Handler>()
        whenever(handler.postDelayed(any<Runnable>(), any())).thenReturn(true)
        val scheduler = HandlerCaptureTaskScheduler(handler)

        // When
        val work = scheduler.schedule(1) {}
        work.cancel()

        // Then
        val runnable = argumentCaptor<Runnable>()
        verify(handler).postDelayed(runnable.capture(), org.mockito.kotlin.eq(1L))
        verify(handler).removeCallbacks(runnable.firstValue)
        verify(handler, never()).post(any<Runnable>())
    }

    @Test
    fun `M use device elapsed time W read capture clock`(
        @LongForgery fakeElapsedTimeNs: Long
    ) {
        // Given
        val timeProvider = mock<TimeProvider>()
        whenever(timeProvider.getDeviceElapsedTimeNanos()).thenReturn(fakeElapsedTimeNs)

        // When
        val result = TimeProviderCaptureTimeProvider(timeProvider).elapsedRealtimeNanos()

        // Then
        assertThat(result).isEqualTo(fakeElapsedTimeNs)
    }

    private fun activityShowing(decorView: View): android.app.Activity {
        val mockWindow = mock<Window>()
        whenever(mockWindow.peekDecorView()).thenReturn(decorView)
        return mock<android.app.Activity>().also { whenever(it.window).thenReturn(mockWindow) }
    }

    private fun immediateHandler(): Handler = mock<Handler>().also { handler ->
        doAnswer {
            it.getArgument<Runnable>(0).run()
            true
        }.whenever(handler).post(any<Runnable>())
    }
}
