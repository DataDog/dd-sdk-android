/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition

import android.app.Application
import android.os.Handler
import android.view.Display
import android.view.View
import android.view.ViewTreeObserver
import android.view.Window
import com.datadog.android.api.InternalLogger
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.android.utils.verifyLog
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.clearInvocations
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.isA
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@ForgeConfiguration(ForgeConfigurator::class)
@MockitoSettings(strictness = Strictness.LENIENT)
internal class AndroidSnapshotCaptureLifecycleTest {

    @Test
    fun `M signal source window W onDraw() { repeated draws }`() {
        // Given
        val mockView = mock<View>()
        val fakeChangedWindows = mutableListOf<List<View>>()
        val testedListener = CompositionOnDrawListener(mockView) { fakeChangedWindows += it }

        // When
        repeat(2) { testedListener.onDraw() }

        // Then
        assertThat(fakeChangedWindows).containsExactly(listOf(mockView), listOf(mockView))
    }

    @Test
    fun `M register an active window W intercept() { new root }`() {
        // Given
        val fakeFixture = DrawFixture()

        // When
        fakeFixture.testedInterceptor.intercept(listOf(fakeFixture.mockRoot))

        // Then
        verify(fakeFixture.mockObserver).addOnDrawListener(isA())
        assertThat(fakeFixture.fakeSource.currentWindows()).containsExactly(fakeFixture.mockRoot)
        assertThat(fakeFixture.fakeChangedWindows).containsExactly(listOf(fakeFixture.mockRoot))
    }

    @Test
    fun `M remove an active window W intercept() { root removed }`() {
        // Given
        val fakeFixture = DrawFixture()
        fakeFixture.testedInterceptor.intercept(listOf(fakeFixture.mockRoot))
        val fakeListener = argumentCaptor<ViewTreeObserver.OnDrawListener>()
        verify(fakeFixture.mockObserver).addOnDrawListener(fakeListener.capture())

        // When
        fakeFixture.testedInterceptor.intercept(emptyList())

        // Then
        verify(fakeFixture.mockObserver).removeOnDrawListener(fakeListener.firstValue)
        assertThat(fakeFixture.fakeSource.currentWindows()).isEmpty()
        assertThat(fakeFixture.fakeChangedWindows).containsExactly(listOf(fakeFixture.mockRoot), emptyList())
    }

    @Test
    fun `M avoid duplicate capture W intercept() { ordered roots unchanged }`() {
        // Given
        val fakeViews = List(2) {
            val mockObserver: ViewTreeObserver = mock()
            whenever(mockObserver.isAlive).thenReturn(true)
            mock<View>().apply { whenever(viewTreeObserver).thenReturn(mockObserver) }
        }
        val fakeChangedWindows = mutableListOf<List<View>>()
        val fakeSource = ActiveWindowSource()
        val testedInterceptor = CompositionViewOnDrawInterceptor(fakeSource, { fakeChangedWindows += it }, mock())
        testedInterceptor.intercept(fakeViews)

        // When
        testedInterceptor.intercept(fakeViews.toList())

        // Then
        assertThat(fakeChangedWindows).containsExactly(fakeViews)
        fakeViews.forEach { verify(it.viewTreeObserver).addOnDrawListener(isA()) }
    }

    @Test
    fun `M request capture W intercept() { root order changes }`() {
        // Given
        val fakeViews = List(2) {
            val mockObserver: ViewTreeObserver = mock()
            whenever(mockObserver.isAlive).thenReturn(true)
            mock<View>().apply { whenever(viewTreeObserver).thenReturn(mockObserver) }
        }
        val fakeChangedWindows = mutableListOf<List<View>>()
        val fakeSource = ActiveWindowSource()
        val testedInterceptor = CompositionViewOnDrawInterceptor(fakeSource, { fakeChangedWindows += it }, mock())
        testedInterceptor.intercept(fakeViews)

        // When
        testedInterceptor.intercept(fakeViews.reversed())

        // Then
        assertThat(fakeChangedWindows).containsExactly(fakeViews, fakeViews.reversed())
        assertThat(fakeSource.currentWindows()).containsExactlyElementsOf(fakeViews.reversed())
        fakeViews.forEach { verify(it.viewTreeObserver).addOnDrawListener(isA()) }
    }

    @Test
    fun `M skip listener registration W intercept { viewTreeObserver is not alive }`() {
        // Given
        val mockObserver = mock<ViewTreeObserver>()
        whenever(mockObserver.isAlive).thenReturn(false)
        val mockView = mock<View>()
        whenever(mockView.viewTreeObserver).thenReturn(mockObserver)
        val fakeSource = ActiveWindowSource()
        val testedInterceptor = CompositionViewOnDrawInterceptor(fakeSource, { }, mock())

        // When
        testedInterceptor.intercept(listOf(mockView))

        // Then
        verify(mockObserver, never()).addOnDrawListener(any())
        assertThat(fakeSource.currentWindows()).containsExactly(mockView)
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
            InternalLogger.Target.MAINTAINER,
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

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `M remove transferred listener W stop() or intercept() { window attaches }`(fakeStop: Boolean) {
        // Given
        val mockOriginalObserver = mock<ViewTreeObserver>()
        val mockAttachedObserver = mock<ViewTreeObserver>()
        whenever(mockOriginalObserver.isAlive).thenReturn(true)
        whenever(mockAttachedObserver.isAlive).thenReturn(true)
        val mockView = mock<View>()
        whenever(mockView.viewTreeObserver).thenReturn(mockOriginalObserver)
        val testedInterceptor = CompositionViewOnDrawInterceptor(ActiveWindowSource(), { }, mock())
        testedInterceptor.intercept(listOf(mockView))
        val fakeAddedListener = argumentCaptor<ViewTreeObserver.OnDrawListener>()
        verify(mockOriginalObserver).addOnDrawListener(fakeAddedListener.capture())
        whenever(mockOriginalObserver.isAlive).thenReturn(false)
        whenever(mockView.viewTreeObserver).thenReturn(mockAttachedObserver)

        // When
        if (fakeStop) testedInterceptor.stop() else testedInterceptor.intercept(emptyList())

        // Then
        verify(mockOriginalObserver, never()).removeOnDrawListener(any())
        verify(mockAttachedObserver).removeOnDrawListener(fakeAddedListener.firstValue)
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
            InternalLogger.Target.MAINTAINER,
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
        val fakeSource = ActiveWindowSource()
        val testedInterceptor = CompositionViewOnDrawInterceptor(fakeSource, { }, mock())
        testedInterceptor.intercept(listOf(mockView))
        val fakeAddedListener = argumentCaptor<ViewTreeObserver.OnDrawListener>()
        verify(mockObserver).addOnDrawListener(fakeAddedListener.capture())

        // When
        testedInterceptor.stop()

        // Then
        verify(mockObserver).removeOnDrawListener(fakeAddedListener.firstValue)
        assertThat(fakeSource.currentWindows()).isEmpty()
    }

    @Test
    fun `M register activity callbacks W registerCallbacks()`() {
        // Given
        val fakeFixture = LifecycleFixture()

        // When
        fakeFixture.testedLifecycle.registerCallbacks()

        // Then
        verify(fakeFixture.mockApplication).registerActivityLifecycleCallbacks(isA())
    }

    @Test
    fun `M unregister activity callbacks W unregisterCallbacks()`() {
        // Given
        val fakeFixture = LifecycleFixture()
        fakeFixture.testedLifecycle.registerCallbacks()
        val fakeCallbacks = argumentCaptor<Application.ActivityLifecycleCallbacks>()
        verify(fakeFixture.mockApplication).registerActivityLifecycleCallbacks(fakeCallbacks.capture())

        // When
        fakeFixture.testedLifecycle.unregisterCallbacks()

        // Then
        verify(fakeFixture.mockApplication).unregisterActivityLifecycleCallbacks(fakeCallbacks.firstValue)
    }

    @Test
    fun `M discover current roots W start()`() {
        // Given
        val fakeFixture = LifecycleFixture()

        // When
        fakeFixture.testedLifecycle.start()

        // Then
        verify(fakeFixture.mockDrawInterceptor).intercept(fakeFixture.fakeViews)
    }

    @Test
    fun `M refresh current roots W onWindowsAdded() { recording }`() {
        // Given
        val fakeFixture = LifecycleFixture()
        fakeFixture.testedLifecycle.start()
        clearInvocations(fakeFixture.mockDrawInterceptor)

        // When
        fakeFixture.testedLifecycle.onWindowsAdded(listOf(mock<Window>()))

        // Then
        verify(fakeFixture.mockDrawInterceptor).intercept(fakeFixture.fakeViews)
    }

    @Test
    fun `M stop draw interception W stop()`() {
        // Given
        val fakeFixture = LifecycleFixture()
        fakeFixture.testedLifecycle.start()

        // When
        fakeFixture.testedLifecycle.stop()

        // Then
        verify(fakeFixture.mockDrawInterceptor).stop()
    }

    @Test
    fun `M ignore removed windows W onWindowsRemoved() { recording stopped }`() {
        // Given
        val fakeFixture = LifecycleFixture()
        fakeFixture.testedLifecycle.start()
        fakeFixture.testedLifecycle.stop()
        clearInvocations(fakeFixture.mockDrawInterceptor)

        // When
        fakeFixture.testedLifecycle.onWindowsRemoved(listOf(mock<Window>()))

        // Then
        verifyNoInteractions(fakeFixture.mockDrawInterceptor)
    }

    @Test
    fun `M intercept the activity decor view W start { window manager reports no windows }`() {
        // Given
        val mockDecorView = mock<View>()
        val mockInterceptor = mock<CompositionViewOnDrawInterceptor>()
        val testedLifecycle = AndroidSnapshotCaptureLifecycle(
            isMainThreadWindow = { true },
            application = mock(),
            interceptor = mockInterceptor,
            touchInterceptor = mock(),
            internalLogger = mock(),
            currentActivity = mockActivityShowing(mockDecorView),
            uiHandler = mockImmediateHandler(),
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
            isMainThreadWindow = { true },
            application = mock(),
            interceptor = mockInterceptor,
            touchInterceptor = mock(),
            internalLogger = mock(),
            currentActivity = mockActivityShowing(mockActivityDecorView),
            uiHandler = mockImmediateHandler(),
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
            isMainThreadWindow = { true },
            application = mock(),
            interceptor = mockInterceptor,
            touchInterceptor = mock(),
            internalLogger = mock(),
            currentActivity = mockActivityShowing(mockDecorView),
            uiHandler = mockImmediateHandler(),
            windowProvider = { listOf(mockDecorView) }
        )

        // When
        testedLifecycle.start()

        // Then
        verify(mockInterceptor).intercept(listOf(mockDecorView))
    }

    @Test
    fun `M exclude paused window W onWindowsRemoved() { window manager still reports it }`() {
        // Given
        val mockDecorView = mock<View>()
        val mockWindow = mock<Window>()
        whenever(mockWindow.peekDecorView()).thenReturn(mockDecorView)
        val mockInterceptor = mock<CompositionViewOnDrawInterceptor>()
        val testedLifecycle = AndroidSnapshotCaptureLifecycle(
            isMainThreadWindow = { true },
            application = mock(),
            interceptor = mockInterceptor,
            touchInterceptor = mock(),
            internalLogger = mock(),
            uiHandler = mockImmediateHandler(),
            windowProvider = { listOf(mockDecorView) },
            windowFromDecorView = { null }
        )
        testedLifecycle.start()

        // When
        testedLifecycle.onWindowsRemoved(listOf(mockWindow))

        // Then
        verify(mockInterceptor).intercept(emptyList())
    }

    @Test
    fun `M re-include a window W onWindowsAdded() { it is tracked again after being excluded }`() {
        // Given
        val mockDecorView = mock<View>()
        val mockWindow = mock<Window>()
        whenever(mockWindow.peekDecorView()).thenReturn(mockDecorView)
        val mockInterceptor = mock<CompositionViewOnDrawInterceptor>()
        val testedLifecycle = AndroidSnapshotCaptureLifecycle(
            isMainThreadWindow = { true },
            application = mock(),
            interceptor = mockInterceptor,
            touchInterceptor = mock(),
            internalLogger = mock(),
            uiHandler = mockImmediateHandler(),
            windowProvider = { listOf(mockDecorView) },
            windowFromDecorView = { null }
        )
        testedLifecycle.start()
        testedLifecycle.onWindowsRemoved(listOf(mockWindow))

        // When
        testedLifecycle.onWindowsAdded(listOf(mockWindow))

        // Then
        verify(mockInterceptor, org.mockito.kotlin.times(2)).intercept(listOf(mockDecorView))
    }

    @Test
    fun `M forget an excluded window W onWindowsAdded() { it disappears from the window manager }`() {
        // Given
        val mockDecorView = mock<View>()
        val mockWindow = mock<Window>()
        whenever(mockWindow.peekDecorView()).thenReturn(mockDecorView)
        val mockInterceptor = mock<CompositionViewOnDrawInterceptor>()
        var fakeReportedDecorViews = listOf(mockDecorView)
        val testedLifecycle = AndroidSnapshotCaptureLifecycle(
            isMainThreadWindow = { true },
            application = mock(),
            interceptor = mockInterceptor,
            touchInterceptor = mock(),
            internalLogger = mock(),
            uiHandler = mockImmediateHandler(),
            windowProvider = { fakeReportedDecorViews },
            windowFromDecorView = { null }
        )
        testedLifecycle.start()
        testedLifecycle.onWindowsRemoved(listOf(mockWindow))

        // When
        fakeReportedDecorViews = emptyList()
        testedLifecycle.onWindowsAdded(emptyList())
        fakeReportedDecorViews = listOf(mockDecorView)
        testedLifecycle.onWindowsAdded(emptyList())

        // Then
        verify(mockInterceptor, org.mockito.kotlin.times(2)).intercept(listOf(mockDecorView))
    }

    @Test
    fun `M intercept resumed window W onActivityResumed() { no window manager roots }`() {
        // Given
        val mockDecorView = mock<View>()
        val mockApplication = mock<Application>()
        val mockInterceptor = mock<CompositionViewOnDrawInterceptor>()
        val testedLifecycle = AndroidSnapshotCaptureLifecycle(
            isMainThreadWindow = { true },
            application = mockApplication,
            interceptor = mockInterceptor,
            touchInterceptor = mock(),
            internalLogger = mock(),
            uiHandler = mockImmediateHandler(),
            windowProvider = { emptyList() }
        )
        testedLifecycle.registerCallbacks()
        testedLifecycle.start()
        val fakeRegisteredCallbacks = argumentCaptor<Application.ActivityLifecycleCallbacks>()
        verify(mockApplication).registerActivityLifecycleCallbacks(fakeRegisteredCallbacks.capture())

        // When
        fakeRegisteredCallbacks.firstValue.onActivityResumed(mockActivityShowing(mockDecorView))

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
            isMainThreadWindow = { true },
            application = mock(),
            interceptor = mock(),
            touchInterceptor = mockTouchInterceptor,
            internalLogger = mock(),
            currentActivity = mockActivity,
            uiHandler = mockImmediateHandler(),
            windowProvider = { listOf(mockDialogDecorView) },
            windowFromDecorView = { if (it === mockDialogDecorView) mockDialogWindow else null }
        )

        // When
        testedLifecycle.start()

        // Then
        verify(mockTouchInterceptor).intercept(listOf(mockActivityWindow, mockDialogWindow))
    }

    @ParameterizedTest
    @CsvSource("0, true", "-1, true", "none, true", "1, false", "7, false", nullValues = ["none"])
    fun `M apply traversal display policy to both interceptors W start`(
        fakeDisplayId: Int?,
        fakeSupported: Boolean
    ) {
        // Given
        val fakeFixture = DisplayPolicyFixture(fakeDisplayId)

        // When
        fakeFixture.testedLifecycle.start()

        // Then
        val fakeExpectedRoots = if (fakeSupported) {
            listOf(fakeFixture.mockActivityRoot, fakeFixture.mockDialogRoot, fakeFixture.mockDefaultRoot)
        } else {
            listOf(fakeFixture.mockDefaultRoot)
        }
        val fakeExpectedWindows = if (fakeSupported) {
            listOf(fakeFixture.mockActivity.window, fakeFixture.mockDialogWindow, fakeFixture.mockDefaultWindow)
        } else {
            listOf(fakeFixture.mockDefaultWindow)
        }
        verify(fakeFixture.mockDrawInterceptor).intercept(fakeExpectedRoots)
        verify(fakeFixture.mockTouchInterceptor).intercept(fakeExpectedWindows)
    }

    @Test
    fun `M preserve global order W start() { tracked and untracked windows are interleaved }`() {
        // Given
        val mockApplication = mock<Application>()
        val fakeRoots = List(4) { mock<View>() }
        val fakeActivities = fakeRoots.map(::mockActivityShowing)
        val fakeWindows = fakeActivities.map { it.window }
        val mockDrawInterceptor = mock<CompositionViewOnDrawInterceptor>()
        val mockTouchInterceptor = mock<CompositionWindowTouchInterceptor>()
        val testedLifecycle = AndroidSnapshotCaptureLifecycle(
            application = mockApplication,
            interceptor = mockDrawInterceptor,
            touchInterceptor = mockTouchInterceptor,
            internalLogger = mock(),
            uiHandler = mockImmediateHandler(),
            windowProvider = { fakeRoots },
            windowFromDecorView = { fakeWindows[fakeRoots.indexOf(it)] },
            isMainThreadWindow = { true }
        )
        testedLifecycle.registerCallbacks()
        val fakeCallbacks = argumentCaptor<Application.ActivityLifecycleCallbacks>()
        verify(mockApplication).registerActivityLifecycleCallbacks(fakeCallbacks.capture())
        fakeCallbacks.firstValue.onActivityResumed(fakeActivities[3])
        fakeCallbacks.firstValue.onActivityResumed(fakeActivities[1])

        // When
        testedLifecycle.start()

        // Then
        verify(mockDrawInterceptor).intercept(fakeRoots)
        verify(mockTouchInterceptor).intercept(fakeWindows)
    }

    @Test
    fun `M skip an untracked window W start touch { window cannot be resolved }`() {
        // Given
        val mockDialogDecorView = mock<View>()
        val mockTouchInterceptor = mock<CompositionWindowTouchInterceptor>()
        val testedLifecycle = AndroidSnapshotCaptureLifecycle(
            isMainThreadWindow = { true },
            application = mock(),
            interceptor = mock(),
            touchInterceptor = mockTouchInterceptor,
            internalLogger = mock(),
            uiHandler = mockImmediateHandler(),
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
            isMainThreadWindow = { true },
            application = mock(),
            interceptor = mock(),
            touchInterceptor = mockTouchInterceptor,
            internalLogger = mock(),
            uiHandler = mockImmediateHandler(),
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
        val mockHandler = mockImmediateHandler()
        val testedLifecycle = AndroidSnapshotCaptureLifecycle(
            isMainThreadWindow = { true },
            application = mock(),
            interceptor = mock(),
            touchInterceptor = mock(),
            internalLogger = mock(),
            uiHandler = mockHandler,
            windowProvider = { emptyList() }
        )

        // When
        testedLifecycle.start()

        // Then
        verify(mockHandler).postDelayed(isA<Runnable>(), org.mockito.kotlin.eq(1_000L))
    }

    @Test
    fun `M pick up an untracked window W run() { periodic refresh fires }`() {
        // Given
        val mockInterceptor = mock<CompositionViewOnDrawInterceptor>()
        val mockHandler = mockImmediateHandler()
        val fakeDelayedRunnable = argumentCaptor<Runnable>()
        var fakeWindows = emptyList<View>()
        val testedLifecycle = AndroidSnapshotCaptureLifecycle(
            isMainThreadWindow = { true },
            application = mock(),
            interceptor = mockInterceptor,
            touchInterceptor = mock(),
            internalLogger = mock(),
            uiHandler = mockHandler,
            windowProvider = { fakeWindows }
        )
        testedLifecycle.start()
        verify(mockHandler).postDelayed(fakeDelayedRunnable.capture(), eq(1_000L))

        // When
        val mockDialogDecorView = mock<View>()
        fakeWindows = listOf(mockDialogDecorView)
        fakeDelayedRunnable.firstValue.run()

        // Then
        verify(mockInterceptor).intercept(listOf(mockDialogDecorView))
    }

    @Test
    fun `M reschedule itself W run() { periodic refresh fires, still running }`() {
        // Given
        val mockHandler = mockImmediateHandler()
        val fakeDelayedRunnable = argumentCaptor<Runnable>()
        val testedLifecycle = AndroidSnapshotCaptureLifecycle(
            isMainThreadWindow = { true },
            application = mock(),
            interceptor = mock(),
            touchInterceptor = mock(),
            internalLogger = mock(),
            uiHandler = mockHandler,
            windowProvider = { emptyList() }
        )
        testedLifecycle.start()
        verify(mockHandler).postDelayed(fakeDelayedRunnable.capture(), eq(1_000L))

        // When
        fakeDelayedRunnable.firstValue.run()

        // Then
        verify(mockHandler, org.mockito.kotlin.times(2)).postDelayed(isA<Runnable>(), eq(1_000L))
    }

    @Test
    fun `M stop refreshing and rescheduling W run() { periodic refresh fires, stopped }`() {
        // Given
        val mockInterceptor = mock<CompositionViewOnDrawInterceptor>()
        val mockHandler = mockImmediateHandler()
        val fakeDelayedRunnable = argumentCaptor<Runnable>()
        val testedLifecycle = AndroidSnapshotCaptureLifecycle(
            isMainThreadWindow = { true },
            application = mock(),
            interceptor = mockInterceptor,
            touchInterceptor = mock(),
            internalLogger = mock(),
            uiHandler = mockHandler,
            windowProvider = { emptyList() }
        )
        testedLifecycle.start()
        verify(mockHandler).postDelayed(fakeDelayedRunnable.capture(), eq(1_000L))
        testedLifecycle.stop()
        org.mockito.kotlin.clearInvocations(mockInterceptor, mockHandler)

        // When
        fakeDelayedRunnable.firstValue.run()

        // Then
        verifyNoInteractions(mockInterceptor, mockHandler)
    }

    @Test
    fun `M post delayed task W schedule() { handler scheduler }`() {
        // Given
        val mockHandler: Handler = mock()
        whenever(mockHandler.postDelayed(any<Runnable>(), any())).thenReturn(true)
        val testedScheduler = HandlerCaptureTaskScheduler(mockHandler)

        // When
        testedScheduler.schedule(1L) {}

        // Then
        verify(mockHandler).postDelayed(isA<Runnable>(), eq(1L))
        verify(mockHandler, never()).post(any<Runnable>())
    }

    @Test
    fun `M remove delayed callback W cancel() { handler scheduler }`() {
        // Given
        val mockHandler: Handler = mock()
        val fakeRunnable = argumentCaptor<Runnable>()
        whenever(mockHandler.postDelayed(fakeRunnable.capture(), eq(1L))).thenReturn(true)
        val testedScheduler = HandlerCaptureTaskScheduler(mockHandler)
        val fakeWork = testedScheduler.schedule(1L) {}

        // When
        fakeWork.cancel()

        // Then
        verify(mockHandler).removeCallbacks(fakeRunnable.firstValue)
    }

    private inner class DisplayPolicyFixture(fakeDisplayId: Int?) {
        private val mockDisplay = fakeDisplayId?.let { displayId ->
            mock<Display> { on { this.displayId } doReturn displayId }
        }
        val mockActivityRoot: View = mock { on { display } doReturn mockDisplay }
        val mockDialogRoot: View = mock { on { display } doReturn mockDisplay }
        private val mockDefaultDisplay: Display = mock { on { displayId } doReturn Display.DEFAULT_DISPLAY }
        val mockDefaultRoot: View = mock { on { display } doReturn mockDefaultDisplay }
        val mockDialogWindow: Window = mock()
        val mockDefaultWindow: Window = mock()
        val mockActivity = mockActivityShowing(mockActivityRoot)
        val mockDrawInterceptor: CompositionViewOnDrawInterceptor = mock()
        val mockTouchInterceptor: CompositionWindowTouchInterceptor = mock()
        private val fakeUntrackedWindows = mapOf(
            mockDialogRoot to mockDialogWindow,
            mockDefaultRoot to mockDefaultWindow
        )
        val testedLifecycle = AndroidSnapshotCaptureLifecycle(
            application = mock(),
            interceptor = mockDrawInterceptor,
            touchInterceptor = mockTouchInterceptor,
            internalLogger = mock(),
            currentActivity = mockActivity,
            uiHandler = mockImmediateHandler(),
            windowProvider = { fakeUntrackedWindows.keys.toList() },
            windowFromDecorView = { fakeUntrackedWindows[it] },
            isMainThreadWindow = { true }
        )
    }

    private class DrawFixture {
        val mockObserver: ViewTreeObserver = mock { on { isAlive } doReturn true }
        val mockRoot: View = mock { on { viewTreeObserver } doReturn mockObserver }
        val fakeChangedWindows = mutableListOf<List<View>>()
        val fakeSource = ActiveWindowSource()
        val testedInterceptor = CompositionViewOnDrawInterceptor(fakeSource, { fakeChangedWindows += it }, mock())
    }

    private inner class LifecycleFixture {
        val mockApplication: Application = mock()
        val mockDrawInterceptor: CompositionViewOnDrawInterceptor = mock()
        val fakeViews = listOf(mock<View>())
        val testedLifecycle = AndroidSnapshotCaptureLifecycle(
            isMainThreadWindow = { true },
            application = mockApplication,
            interceptor = mockDrawInterceptor,
            touchInterceptor = mock(),
            internalLogger = mock(),
            uiHandler = mockImmediateHandler(),
            windowProvider = { fakeViews }
        )
    }

    private fun mockActivityShowing(decorView: View): android.app.Activity {
        val mockWindow = mock<Window>()
        whenever(mockWindow.peekDecorView()).thenReturn(decorView)
        return mock<android.app.Activity>().also { whenever(it.window).thenReturn(mockWindow) }
    }

    private fun mockImmediateHandler(): Handler = mock<Handler>().also { mockHandler ->
        doAnswer {
            it.getArgument<Runnable>(0).run()
            true
        }.whenever(mockHandler).post(any<Runnable>())
    }
}
