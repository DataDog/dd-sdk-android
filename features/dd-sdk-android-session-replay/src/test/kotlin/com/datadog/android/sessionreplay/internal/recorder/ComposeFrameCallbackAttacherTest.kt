/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder

import android.view.Choreographer
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.android.sessionreplay.internal.recorder.listener.WindowsOnDrawListener
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mockito
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(ForgeConfigurator::class)
internal class ComposeFrameCallbackAttacherTest {

    private lateinit var testedAttacher: ComposeFrameCallbackAttacher

    @BeforeEach
    fun setUp() {
        testedAttacher = ComposeFrameCallbackAttacher()
    }

    @Test
    fun `M start Choreographer loop W onListenerCreated()`() {
        Mockito.mockStatic(Choreographer::class.java).use { mockedChoreographer ->
            val mockChoreographer: Choreographer = mock()
            mockedChoreographer.`when`<Choreographer> { Choreographer.getInstance() } doReturn mockChoreographer
            val mockListener: WindowsOnDrawListener = mock()

            testedAttacher.onListenerCreated(mockListener)

            verify(mockChoreographer).postFrameCallback(any())
        }
    }

    @Test
    fun `M drive listener onDraw and repost itself W frameCallback fires`() {
        Mockito.mockStatic(Choreographer::class.java).use { mockedChoreographer ->
            val mockChoreographer: Choreographer = mock()
            mockedChoreographer.`when`<Choreographer> { Choreographer.getInstance() } doReturn mockChoreographer
            val mockListener: WindowsOnDrawListener = mock()
            val callbackCaptor = argumentCaptor<Choreographer.FrameCallback>()

            testedAttacher.onListenerCreated(mockListener)
            verify(mockChoreographer).postFrameCallback(callbackCaptor.capture())

            callbackCaptor.firstValue.doFrame(0L)

            verify(mockListener).onDraw()
            verify(mockChoreographer, times(2)).postFrameCallback(any())
        }
    }

    @Test
    fun `M stop the previous listener's loop W onListenerCreated() called again`() {
        Mockito.mockStatic(Choreographer::class.java).use { mockedChoreographer ->
            val mockChoreographer: Choreographer = mock()
            mockedChoreographer.`when`<Choreographer> { Choreographer.getInstance() } doReturn mockChoreographer
            val firstListener: WindowsOnDrawListener = mock()
            val secondListener: WindowsOnDrawListener = mock()

            testedAttacher.onListenerCreated(firstListener)
            testedAttacher.onListenerCreated(secondListener)

            verify(mockChoreographer).removeFrameCallback(any())
            verify(mockChoreographer, times(2)).postFrameCallback(any())
        }
    }

    @Test
    fun `M stop the loop W stopAll()`() {
        Mockito.mockStatic(Choreographer::class.java).use { mockedChoreographer ->
            val mockChoreographer: Choreographer = mock()
            mockedChoreographer.`when`<Choreographer> { Choreographer.getInstance() } doReturn mockChoreographer
            val mockListener: WindowsOnDrawListener = mock()

            testedAttacher.onListenerCreated(mockListener)
            testedAttacher.stopAll()

            verify(mockChoreographer).removeFrameCallback(any())
        }
    }

    @Test
    fun `M never touch Choreographer W stopAll() { nothing was ever started }`() {
        Mockito.mockStatic(Choreographer::class.java).use { mockedChoreographer ->
            testedAttacher.stopAll()

            mockedChoreographer.verify({ Choreographer.getInstance() }, never())
        }
    }

    @Test
    fun `M stop the loop W pause()`() {
        Mockito.mockStatic(Choreographer::class.java).use { mockedChoreographer ->
            val mockChoreographer: Choreographer = mock()
            mockedChoreographer.`when`<Choreographer> { Choreographer.getInstance() } doReturn mockChoreographer
            val mockListener: WindowsOnDrawListener = mock()

            testedAttacher.onListenerCreated(mockListener)
            testedAttacher.pause()

            verify(mockChoreographer).removeFrameCallback(any())
        }
    }

    @Test
    fun `M restart the loop for the same listener W resume() { after pause() }`() {
        Mockito.mockStatic(Choreographer::class.java).use { mockedChoreographer ->
            val mockChoreographer: Choreographer = mock()
            mockedChoreographer.`when`<Choreographer> { Choreographer.getInstance() } doReturn mockChoreographer
            val mockListener: WindowsOnDrawListener = mock()

            testedAttacher.onListenerCreated(mockListener)
            testedAttacher.pause()
            testedAttacher.resume()

            verify(mockChoreographer, times(2)).postFrameCallback(any())
        }
    }

    @Test
    fun `M not start a new loop W onListenerCreated() { paused by lifecycle }`() {
        Mockito.mockStatic(Choreographer::class.java).use { mockedChoreographer ->
            val mockChoreographer: Choreographer = mock()
            mockedChoreographer.`when`<Choreographer> { Choreographer.getInstance() } doReturn mockChoreographer
            val firstListener: WindowsOnDrawListener = mock()
            val secondListener: WindowsOnDrawListener = mock()

            testedAttacher.onListenerCreated(firstListener)
            testedAttacher.pause()
            // A window-refresh event mid-background (e.g. a dialog appearing) still creates a
            // fresh listener — it must not start its own Choreographer loop while paused.
            testedAttacher.onListenerCreated(secondListener)

            verify(mockChoreographer, times(1)).postFrameCallback(any())
        }
    }

    @Test
    fun `M do nothing W resume() { nothing was ever started }`() {
        Mockito.mockStatic(Choreographer::class.java).use { mockedChoreographer ->
            testedAttacher.resume()

            mockedChoreographer.verify({ Choreographer.getInstance() }, never())
        }
    }
}
