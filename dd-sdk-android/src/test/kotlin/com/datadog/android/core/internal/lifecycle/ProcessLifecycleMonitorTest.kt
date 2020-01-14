package com.datadog.android.core.internal.lifecycle

import android.app.Activity
import com.nhaarman.mockitokotlin2.inOrder
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyNoMoreInteractions
import com.nhaarman.mockitokotlin2.verifyZeroInteractions
import java.util.concurrent.CountDownLatch
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class)
)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
internal class ProcessLifecycleMonitorTest {

    lateinit var underTest: ProcessLifecycleMonitor
    @Mock
    lateinit var mockCallback: ProcessLifecycleMonitor.Callback
    @Mock
    lateinit var mockActivity1: Activity
    @Mock
    lateinit var mockActivity2: Activity
    @Mock
    lateinit var mockActivity3: Activity

    @BeforeEach
    fun `set up`() {
        underTest = ProcessLifecycleMonitor(mockCallback)
    }

    @Test
    fun `triggers onStarted when process starts`() {
        // when
        underTest.onActivityStarted(mockActivity1)

        // then
        verify(mockCallback).onStarted()
        verifyNoMoreInteractions(mockCallback)
    }

    @Test
    fun `triggers onStarted only once when several activities are started`() {
        // when
        underTest.onActivityStarted(mockActivity1)
        underTest.onActivityStarted(mockActivity2)
        underTest.onActivityStarted(mockActivity3)

        // then
        verify(mockCallback, times(1)).onStarted()
    }

    @Test
    fun `triggers onResume when process resumes`() {
        // when
        underTest.onActivityStarted(mockActivity1)
        underTest.onActivityResumed(mockActivity1)

        // then
        val inOrder = inOrder(mockCallback)
        inOrder.verify(mockCallback).onStarted()
        inOrder.verify(mockCallback).onResumed()
        inOrder.verifyNoMoreInteractions()
    }

    @Test
    fun `triggers onResume only once when several activities are resumed`() {
        // when
        underTest.onActivityStarted(mockActivity1)
        underTest.onActivityResumed(mockActivity1)
        underTest.onActivityStarted(mockActivity2)
        underTest.onActivityResumed(mockActivity2)
        underTest.onActivityStarted(mockActivity3)
        underTest.onActivityResumed(mockActivity3)

        // then
        val inOrder = inOrder(mockCallback)
        inOrder.verify(mockCallback).onStarted()
        inOrder.verify(mockCallback).onResumed()
    }

    @Test
    fun `triggers onPaused when process pauses`() {
        // given
        underTest.onActivityStarted(mockActivity1)
        underTest.onActivityResumed(mockActivity1)

        // when
        underTest.onActivityPaused(mockActivity1)

        // then
        verify(mockCallback).onResumed()
        verify(mockCallback).onStarted()
        verify(mockCallback).onPaused()
        verifyNoMoreInteractions(mockCallback)
    }

    @Test
    fun `triggers onStopped when process stops`() {
        // given
        underTest.onActivityStarted(mockActivity1)
        underTest.onActivityResumed(mockActivity1)

        // when
        underTest.onActivityPaused(mockActivity1)
        underTest.onActivityStopped(mockActivity1)

        // then
        val inOrder = inOrder(mockCallback)
        inOrder.verify(mockCallback).onPaused()
        inOrder.verify(mockCallback).onStopped()
        inOrder.verifyNoMoreInteractions()
    }

    @Test
    fun `triggers onStopped onPaused only once when several activities stop`() {
        // given
        underTest.onActivityStarted(mockActivity1)
        underTest.onActivityResumed(mockActivity1)
        underTest.onActivityStarted(mockActivity2)
        underTest.onActivityResumed(mockActivity2)
        underTest.onActivityStarted(mockActivity3)
        underTest.onActivityResumed(mockActivity3)
        underTest.onActivityPaused(mockActivity1)

        // when
        underTest.onActivityStopped(mockActivity1)
        underTest.onActivityPaused(mockActivity2)
        underTest.onActivityStopped(mockActivity2)
        underTest.onActivityPaused(mockActivity3)
        underTest.onActivityStopped(mockActivity3)

        // then
        val inOrder = inOrder(mockCallback)
        inOrder.verify(mockCallback).onPaused()
        inOrder.verify(mockCallback).onStopped()
    }

    @Test
    fun `does not call onStopped onPaused if the activity was not resumed`() {
        underTest.onActivityPaused(mockActivity1)
        // when
        underTest.onActivityStopped(mockActivity1)

        // then
        verifyZeroInteractions(mockCallback)
    }

    @Test
    fun `does not call onStopped if onPause was not call upfront`() {
        // given
        underTest.onActivityStarted(mockActivity1)
        underTest.onActivityResumed(mockActivity1)

        // when
        underTest.onActivityStopped(mockActivity1)

        // then
        verify(mockCallback, never()).onStopped()
    }

    @Test
    fun `when starting activities from 2 different threads will only call onResumed once`() {

        // given
        val countDownLatch = CountDownLatch(2)

        // when
        Thread {
            underTest.onActivityStarted(mockActivity1)
            underTest.onActivityResumed(mockActivity1)
            countDownLatch.countDown()
        }.start()
        Thread {
            underTest.onActivityStarted(mockActivity2)
            underTest.onActivityResumed(mockActivity2)
            countDownLatch.countDown()
        }.start()

        countDownLatch.await()

        // then
        verify(mockCallback).onStarted()
        verify(mockCallback).onResumed()
        verifyNoMoreInteractions(mockCallback)
    }

    @Test
    fun `when stopped from 2 different threads will only call onStooped once`() {

        // given
        underTest.onActivityStarted(mockActivity1)
        underTest.onActivityResumed(mockActivity1)
        underTest.onActivityStarted(mockActivity2)
        underTest.onActivityResumed(mockActivity2)
        val countDownLatch = CountDownLatch(2)

        // when
        Thread {
            underTest.onActivityPaused(mockActivity1)
            underTest.onActivityStopped(mockActivity1)
            countDownLatch.countDown()
        }.start()
        Thread {
            underTest.onActivityPaused(mockActivity2)
            underTest.onActivityStopped(mockActivity2)
            countDownLatch.countDown()
        }.start()

        countDownLatch.await()

        // then
        verify(mockCallback).onStarted()
        verify(mockCallback).onResumed()
        verify(mockCallback).onPaused()
        verify(mockCallback).onStopped()
        verifyNoMoreInteractions(mockCallback)
    }
}
