/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition

import android.app.Activity
import android.app.Application
import android.os.Handler
import android.view.View
import android.view.ViewTreeObserver
import android.view.Window
import com.datadog.tools.unit.getFieldValue
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.reset
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import java.lang.ref.Reference
import java.lang.ref.WeakReference

internal class CompositionDrawListenerRetentionTest {

    @Test
    fun `M release source and ignore callback W onDraw { source collected }`() {
        // Given
        val mockChanges: CompositionChangeListener = mock()
        val (testedListener, fakeReferences) = listenerWithUnownedView(mockChanges)

        // When: the listener stays alive after its source loses all external owners.
        assertCollected(fakeReferences)
        testedListener.onDraw()

        // Then
        verifyNoInteractions(mockChanges)
    }

    @Test
    fun `M ignore callback W onDraw { source reference cleared }`() {
        // Given: deterministic coverage of the collected-reference branch, independent of GC timing.
        val mockRoot: View = mock()
        val mockChanges: CompositionChangeListener = mock()
        val testedListener = CompositionOnDrawListener(mockRoot, mockChanges)
        val fakeReference: WeakReference<View> = testedListener.getFieldValue("viewReference")
        assertThat(fakeReference.get()).isSameAs(mockRoot)
        fakeReference.clear()

        // When
        testedListener.onDraw()

        // Then
        verifyNoInteractions(mockChanges)
    }

    @ParameterizedTest
    @EnumSource(Cleanup::class)
    fun `M release detached root and original observer W cleanup`(fakeCleanup: Cleanup) {
        // Given: the observer also owns another listener that strongly captures the root.
        val fixture = Fixture()
        val fakeReferences = fixture.showWindow()

        // When
        fixture.reportedViews = emptyList()
        when (fakeCleanup) {
            Cleanup.REFRESH -> fixture.refresh()
            Cleanup.STOP -> fixture.lifecycle.stop()
        }

        // Then: keep the recorder-side owners alive while checking the detached graph.
        assertThat(fixture.source.currentWindows()).isEmpty()
        assertThat(fixture.registrations()).isEmpty()
        clearObserverHistory(fakeReferences.last())
        try {
            assertCollected(fakeReferences)
        } finally {
            Reference.reachabilityFence(fixture)
        }
    }

    @ParameterizedTest
    @CsvSource(
        "REFRESH, ALIVE",
        "REFRESH, DEAD",
        "REFRESH, REMOVAL_THROWS",
        "STOP, ALIVE",
        "STOP, DEAD",
        "STOP, REMOVAL_THROWS"
    )
    fun `M drop registration even when observer cannot unregister W cleanup`(
        fakeCleanup: Cleanup,
        fakeState: ObserverState
    ) {
        // Given
        val mockObserver: ViewTreeObserver = mock()
        whenever(mockObserver.isAlive).thenReturn(true)
        val mockRoot: View = mock()
        whenever(mockRoot.viewTreeObserver).thenReturn(mockObserver)
        val fakeSource = ActiveWindowSource()
        val testedInterceptor = CompositionViewOnDrawInterceptor(fakeSource, {}, mock())
        testedInterceptor.intercept(listOf(mockRoot))
        val fakeListener = argumentCaptor<ViewTreeObserver.OnDrawListener>()
        verify(mockObserver).addOnDrawListener(fakeListener.capture())
        whenever(mockRoot.viewTreeObserver).thenThrow(AssertionError("Must use the original observer"))
        when (fakeState) {
            ObserverState.ALIVE -> Unit
            ObserverState.DEAD -> whenever(mockObserver.isAlive).thenReturn(false)
            ObserverState.REMOVAL_THROWS -> whenever(mockObserver.removeOnDrawListener(any()))
                .doThrow(IllegalStateException("Observer died during removal"))
        }

        // When
        when (fakeCleanup) {
            Cleanup.REFRESH -> testedInterceptor.intercept(emptyList())
            Cleanup.STOP -> testedInterceptor.stop()
        }

        // Then: a removal failure must not leave either the listener or observer in the map.
        val fakeRegistrations: Map<View, *> = testedInterceptor.getFieldValue("interceptedViews")
        assertThat(fakeRegistrations).isEmpty()
        assertThat(fakeSource.currentWindows()).isEmpty()
        if (fakeState == ObserverState.DEAD) {
            verify(mockObserver, never()).removeOnDrawListener(any())
        } else {
            verify(mockObserver).removeOnDrawListener(fakeListener.firstValue)
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `M release excluded root W activity pauses while not recording`(fakePreviouslyRecording: Boolean) {
        // Given: callbacks remain registered while SessionReplayFeature has stopped recording.
        val fixture = Fixture()
        if (fakePreviouslyRecording) {
            fixture.lifecycle.start()
            fixture.lifecycle.stop()
        }

        // When: the activity subsequently pauses and disappears, with no refresh loop running.
        val fakeReferences = fixture.removeWindowWhileStopped()

        // Then
        try {
            assertCollected(fakeReferences)
        } finally {
            Reference.reachabilityFence(fixture)
        }
    }

    @Test
    fun `M preserve live window exclusion W recording restarts after activity pauses`() {
        // Given: a paused split-screen window is still owned by the window manager.
        val fixture = Fixture()
        fixture.lifecycle.start()
        fixture.lifecycle.stop()
        val mockRoot: View = mock()
        val mockWindow: Window = mock()
        whenever(mockWindow.peekDecorView()).thenReturn(mockRoot)
        fixture.reportedViews = listOf(mockRoot)
        fixture.lifecycle.onWindowsRemoved(listOf(mockWindow))

        // When
        fixture.lifecycle.start()

        // Then: weak exclusions still prevent reclassification as an untracked dialog.
        assertThat(fixture.source.currentWindows()).isEmpty()
        assertThat(fixture.registrations()).isEmpty()
    }

    private class Fixture {
        val source = ActiveWindowSource()
        private val interceptor = CompositionViewOnDrawInterceptor(source, {}, mock())
        private val mockApplication: Application = mock()
        var reportedViews = emptyList<View>()
        private val mockHandler: Handler = mock<Handler>().also { handler ->
            doAnswer {
                it.getArgument<Runnable>(0).run()
                true
            }.whenever(handler).post(any<Runnable>())
        }
        val lifecycle = AndroidSnapshotCaptureLifecycle(
            application = mockApplication,
            interceptor = interceptor,
            touchInterceptor = mock(),
            internalLogger = mock(),
            uiHandler = mockHandler,
            windowProvider = { reportedViews },
            windowFromDecorView = { null },
            isMainThreadWindow = { true }
        )

        // Separate stack frames prevent the test itself from keeping these objects alive.
        fun showWindow(): List<WeakReference<*>> {
            val fakeActivity = Activity()
            val mockObserver: ViewTreeObserver = mock()
            whenever(mockObserver.isAlive).thenReturn(true)
            val fakeRoot = RetentionView(fakeActivity, mockObserver)
            val fakeOtherListener = ViewTreeObserver.OnGlobalLayoutListener { fakeRoot.invalidate() }
            // Model an observer's unrelated callback retaining the view. Mockito stores arguments.
            mockObserver.addOnGlobalLayoutListener(fakeOtherListener)
            reportedViews = listOf(fakeRoot)
            lifecycle.start()
            return listOf(WeakReference(fakeRoot), WeakReference(fakeActivity), WeakReference(mockObserver))
        }

        fun removeWindowWhileStopped(): List<WeakReference<*>> {
            lifecycle.registerCallbacks()
            val fakeCallbacks = argumentCaptor<Application.ActivityLifecycleCallbacks>()
            verify(mockApplication).registerActivityLifecycleCallbacks(fakeCallbacks.capture())
            val mockActivity: Activity = mock()
            val fakeRoot = RetentionView(mockActivity, mock())
            val mockWindow: Window = mock()
            whenever(mockWindow.peekDecorView()).thenReturn(fakeRoot)
            whenever(mockActivity.window).thenReturn(mockWindow)
            fakeCallbacks.firstValue.onActivityResumed(mockActivity)
            fakeCallbacks.firstValue.onActivityPaused(mockActivity)
            fakeCallbacks.firstValue.onActivityDestroyed(mockActivity)
            // Drop Mockito's stubbing graph; the lifecycle must not remain an owner either.
            reset(mockWindow, mockActivity)
            return listOf(WeakReference(fakeRoot), WeakReference(mockActivity))
        }

        fun refresh() {
            val fakeRefresh = argumentCaptor<Runnable>()
            verify(mockHandler).postDelayed(fakeRefresh.capture(), eq(1_000L))
            fakeRefresh.firstValue.run()
        }

        fun registrations(): Map<View, *> = interceptor.getFieldValue("interceptedViews")
    }

    // Plain Android stubs do not store the constructor context. Model that ownership explicitly,
    // without mock invocation histories retaining a root or Activity outside the tested graph.
    private class RetentionView(
        private val fakeActivity: Activity,
        private val fakeObserver: ViewTreeObserver
    ) : View(fakeActivity) {
        override fun getViewTreeObserver(): ViewTreeObserver = fakeObserver
    }

    private fun assertCollected(references: List<WeakReference<*>>) {
        // GC is asynchronous; allow bounded retries instead of relying on one System.gc() call.
        repeat(100) {
            System.gc()
            if (references.all { reference -> reference.get() == null }) return
            Thread.sleep(20)
        }
        assertThat(references.map { it.get() }).containsOnlyNulls()
    }

    private fun listenerWithUnownedView(
        onWindowsChanged: CompositionChangeListener
    ): Pair<CompositionOnDrawListener, List<WeakReference<*>>> {
        val fakeActivity = Activity()
        val fakeRoot = RetentionView(fakeActivity, mock())
        return CompositionOnDrawListener(fakeRoot, onWindowsChanged) to
            listOf(WeakReference(fakeRoot), WeakReference(fakeActivity))
    }

    private fun clearObserverHistory(reference: WeakReference<*>) {
        // Mockito's invocation history is not part of the Android object graph. It otherwise
        // keeps even successfully removed listeners reachable through its inline mock registry.
        reference.get()?.let { reset(it) }
    }

    enum class Cleanup { REFRESH, STOP }
    enum class ObserverState { ALIVE, DEAD, REMOVAL_THROWS }
}
