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
import com.datadog.android.internal.time.TimeProvider
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
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
    fun `M refresh interception W lifecycle starts and windows change`() {
        // Given
        val application = mock<Application>()
        val interceptor = mock<CompositionViewOnDrawInterceptor>()
        val handler = immediateHandler()
        val views = listOf(mock<View>())
        val lifecycle = AndroidSnapshotCaptureLifecycle(
            application = application,
            interceptor = interceptor,
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
    fun `M defer window query W onWindowsAdded {window not yet attached}`() {
        // Given
        val application = mock<Application>()
        val interceptor = mock<CompositionViewOnDrawInterceptor>()
        val posted = mutableListOf<Runnable>()
        val handler = mock<Handler>()
        doAnswer {
            posted += it.getArgument<Runnable>(0)
            true
        }.whenever(handler).post(any<Runnable>())
        val views = listOf(mock<View>())
        val lifecycle = AndroidSnapshotCaptureLifecycle(
            application = application,
            interceptor = interceptor,
            internalLogger = mock(),
            uiHandler = handler,
            windowProvider = { views }
        )

        // When
        lifecycle.start()
        posted.removeAt(0).run() // the start() post itself
        lifecycle.onWindowsAdded(listOf(mock<Window>()))

        // Then: the new window isn't attached to WindowManagerGlobal yet at the point
        // onWindowsAdded is dispatched, so the query must not run synchronously - only the
        // start() post (already run above) has resolved into an intercept() call so far.
        verify(interceptor, org.mockito.kotlin.times(1)).intercept(views)

        // When the deferred query actually runs (posted to the next main-thread message)
        posted.single().run()

        // Then
        verify(interceptor, org.mockito.kotlin.times(2)).intercept(views)
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

    private fun immediateHandler(): Handler = mock<Handler>().also { handler ->
        doAnswer {
            it.getArgument<Runnable>(0).run()
            true
        }.whenever(handler).post(any<Runnable>())
    }
}
