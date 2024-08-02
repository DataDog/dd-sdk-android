/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.lifecycle

import android.app.Activity
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.quality.Strictness
import java.util.concurrent.CountDownLatch

@Extensions(
    ExtendWith(MockitoExtension::class)
)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
internal class ProcessLifecycleMonitorTest {

    lateinit var testedMonitor: ProcessLifecycleMonitor

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
        testedMonitor = ProcessLifecycleMonitor(mockCallback)
    }

    @Test
    fun `triggers onStarted when process starts`() {
        // When
        testedMonitor.onActivityStarted(mockActivity1)

        // Then
        verify(mockCallback).onStarted()
        verifyNoMoreInteractions(mockCallback)
    }

    @Test
    fun `triggers onStarted only once when several activities are started`() {
        // When
        testedMonitor.onActivityStarted(mockActivity1)
        testedMonitor.onActivityStarted(mockActivity2)
        testedMonitor.onActivityStarted(mockActivity3)

        // Then
        verify(mockCallback, times(1)).onStarted()
        verifyNoMoreInteractions(mockCallback)
    }

    @Test
    fun `triggers onResume when process resumes`() {
        // When
        testedMonitor.onActivityStarted(mockActivity1)
        testedMonitor.onActivityResumed(mockActivity1)

        // Then
        val inOrder = inOrder(mockCallback)
        inOrder.verify(mockCallback).onStarted()
        inOrder.verify(mockCallback).onResumed()
        inOrder.verifyNoMoreInteractions()
    }

    @Test
    fun `triggers onResume only once when several activities are resumed`() {
        // When
        testedMonitor.onActivityStarted(mockActivity1)
        testedMonitor.onActivityResumed(mockActivity1)
        testedMonitor.onActivityStarted(mockActivity2)
        testedMonitor.onActivityResumed(mockActivity2)
        testedMonitor.onActivityStarted(mockActivity3)
        testedMonitor.onActivityResumed(mockActivity3)

        // Then
        val inOrder = inOrder(mockCallback)
        inOrder.verify(mockCallback).onStarted()
        inOrder.verify(mockCallback).onResumed()
        inOrder.verifyNoMoreInteractions()
    }

    @Test
    fun `triggers onPaused when process pauses`() {
        // Given
        testedMonitor.onActivityStarted(mockActivity1)
        testedMonitor.onActivityResumed(mockActivity1)

        // When
        testedMonitor.onActivityPaused(mockActivity1)

        // Then
        verify(mockCallback).onResumed()
        verify(mockCallback).onStarted()
        verify(mockCallback).onPaused()
        verifyNoMoreInteractions(mockCallback)
    }

    @Test
    fun `triggers onStopped when process stops`() {
        // Given
        testedMonitor.onActivityStarted(mockActivity1)
        testedMonitor.onActivityResumed(mockActivity1)

        // When
        testedMonitor.onActivityPaused(mockActivity1)
        testedMonitor.onActivityStopped(mockActivity1)

        // Then
        val inOrder = inOrder(mockCallback)
        inOrder.verify(mockCallback).onPaused()
        inOrder.verify(mockCallback).onStopped()
        inOrder.verifyNoMoreInteractions()
    }

    @Test
    fun `triggers onStopped onPaused only once when several activities stop`() {
        // Given
        testedMonitor.onActivityStarted(mockActivity1)
        testedMonitor.onActivityResumed(mockActivity1)
        testedMonitor.onActivityStarted(mockActivity2)
        testedMonitor.onActivityResumed(mockActivity2)
        testedMonitor.onActivityStarted(mockActivity3)
        testedMonitor.onActivityResumed(mockActivity3)
        testedMonitor.onActivityPaused(mockActivity1)

        // When
        testedMonitor.onActivityStopped(mockActivity1)
        testedMonitor.onActivityPaused(mockActivity2)
        testedMonitor.onActivityStopped(mockActivity2)
        testedMonitor.onActivityPaused(mockActivity3)
        testedMonitor.onActivityStopped(mockActivity3)

        // Then
        val inOrder = inOrder(mockCallback)
        inOrder.verify(mockCallback).onPaused()
        inOrder.verify(mockCallback).onStopped()
        inOrder.verifyNoMoreInteractions()
    }

    @Test
    fun `does not call onStopped onPaused if the activity was not resumed`() {
        testedMonitor.onActivityPaused(mockActivity1)
        // When
        testedMonitor.onActivityStopped(mockActivity1)

        // Then
        verifyNoInteractions(mockCallback)
    }

    @Test
    fun `does not call onStopped if onPause was not call upfront`() {
        // Given
        testedMonitor.onActivityStarted(mockActivity1)
        testedMonitor.onActivityResumed(mockActivity1)

        // When
        testedMonitor.onActivityStopped(mockActivity1)

        // Then
        verify(mockCallback).onStarted()
        verify(mockCallback).onResumed()
        verify(mockCallback, never()).onStopped()
        verifyNoMoreInteractions(mockCallback)
    }

    @Test
    fun `when starting activities from 2 different threads will only call onResumed once`() {
        // Given
        val countDownLatch = CountDownLatch(2)

        // When
        Thread {
            testedMonitor.onActivityStarted(mockActivity1)
            testedMonitor.onActivityResumed(mockActivity1)
            countDownLatch.countDown()
        }.start()
        Thread {
            testedMonitor.onActivityStarted(mockActivity2)
            testedMonitor.onActivityResumed(mockActivity2)
            countDownLatch.countDown()
        }.start()

        countDownLatch.await()

        // Then
        verify(mockCallback).onStarted()
        verify(mockCallback).onResumed()
        verifyNoMoreInteractions(mockCallback)
    }

    @Test
    fun `when stopped from 2 different threads will only call onStooped once`() {
        // Given
        testedMonitor.onActivityStarted(mockActivity1)
        testedMonitor.onActivityResumed(mockActivity1)
        testedMonitor.onActivityStarted(mockActivity2)
        testedMonitor.onActivityResumed(mockActivity2)
        val countDownLatch = CountDownLatch(2)

        // When
        Thread {
            testedMonitor.onActivityPaused(mockActivity1)
            testedMonitor.onActivityStopped(mockActivity1)
            countDownLatch.countDown()
        }.start()
        Thread {
            testedMonitor.onActivityPaused(mockActivity2)
            testedMonitor.onActivityStopped(mockActivity2)
            countDownLatch.countDown()
        }.start()

        countDownLatch.await()

        // Then
        verify(mockCallback).onStarted()
        verify(mockCallback).onResumed()
        verify(mockCallback).onPaused()
        verify(mockCallback).onStopped()
        verifyNoMoreInteractions(mockCallback)
    }
}
