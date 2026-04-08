/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.startup

import android.app.Activity
import android.os.Handler
import android.os.Message
import android.view.View
import android.view.ViewTreeObserver
import android.view.Window
import com.datadog.android.api.InternalLogger
import com.datadog.android.rum.internal.utils.window.RumWindowCallbackListener
import com.datadog.android.rum.internal.utils.window.RumWindowCallbacksRegistry
import com.datadog.android.rum.utils.forge.Configurator
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class),
    ExtendWith(TestConfigurationExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
class RumFirstDrawTimeReporterHandleImplTest {

    private var currentTime: Duration = 0.seconds

    @Mock
    private lateinit var windowCallbackRegistry: RumWindowCallbacksRegistry

    @Mock
    private lateinit var handler: Handler

    @Mock
    private lateinit var callback: RumFirstDrawTimeReporter.Callback

    @Mock
    private lateinit var activity: Activity

    @Mock
    private lateinit var window: Window

    @Mock
    private lateinit var decorView: View

    @Mock
    private lateinit var viewTreeObserver: ViewTreeObserver

    @Mock
    private lateinit var internalLogger: InternalLogger

    @BeforeEach
    fun `set up`() {
        whenever(activity.window) doReturn window
        whenever(window.peekDecorView()) doReturn null
        whenever(window.decorView) doReturn decorView

        whenever(decorView.viewTreeObserver) doReturn viewTreeObserver

        whenever(windowCallbackRegistry.addListener(any(), any())).doAnswer {
            val argListener = it.getArgument<RumWindowCallbackListener>(1)
            argListener.onContentChanged()
        }

        whenever(viewTreeObserver.isAlive) doReturn true

        whenever(handler.post(any())).doAnswer {
            val argRunnable = it.getArgument<Runnable>(0)
            argRunnable.run()
            true
        }
        whenever(handler.sendMessageAtFrontOfQueue(any())).doAnswer {
            val argMessage = it.getArgument<Message>(0)
            argMessage.callback.run()
            true
        }
        whenever(decorView.isAttachedToWindow) doReturn true
    }

    // region first frame detection

    @Test
    fun `M call onFirstFrameDrawn W decorView doesn't exist yet`() {
        // Given
        currentTime += 1.seconds

        // When
        createHandle()

        // Then
        inOrder(windowCallbackRegistry, callback, viewTreeObserver) {
            verify(windowCallbackRegistry).addListener(eq(activity), any())
            verify(windowCallbackRegistry).removeListener(eq(activity), any())
            verify(viewTreeObserver).isAlive

            argumentCaptor<ViewTreeObserver.OnDrawListener> {
                verify(viewTreeObserver).addOnDrawListener(capture())
                firstValue.onDraw()
            }

            verify(callback).onFirstFrameDrawn(1.seconds.inWholeNanoseconds)
            verify(viewTreeObserver).isAlive
            verify(viewTreeObserver).removeOnDrawListener(any())
            verifyNoMoreInteractions()
        }
    }

    @Test
    fun `M call onFirstFrameDrawn W decorView exists`() {
        // Given
        whenever(window.peekDecorView()) doReturn decorView
        currentTime += 1.seconds

        // When
        createHandle()

        // Then
        inOrder(windowCallbackRegistry, callback, viewTreeObserver) {
            verify(viewTreeObserver).isAlive

            argumentCaptor<ViewTreeObserver.OnDrawListener> {
                verify(viewTreeObserver).addOnDrawListener(capture())
                firstValue.onDraw()
            }

            verify(callback).onFirstFrameDrawn(1.seconds.inWholeNanoseconds)
            verify(viewTreeObserver).isAlive
            verify(viewTreeObserver).removeOnDrawListener(any())
            verifyNoMoreInteractions()
        }
    }

    @Test
    fun `M call onFirstFrameDrawn W decorView exists but not attached to window`() {
        // Given
        whenever(window.peekDecorView()) doReturn decorView
        whenever(decorView.isAttachedToWindow) doReturn false
        currentTime += 1.seconds

        // When
        createHandle()

        // Then
        inOrder(windowCallbackRegistry, callback, viewTreeObserver, decorView) {
            argumentCaptor<View.OnAttachStateChangeListener> {
                verify(decorView).addOnAttachStateChangeListener(capture())
                firstValue.onViewAttachedToWindow(decorView)
            }

            verify(viewTreeObserver).isAlive

            argumentCaptor<ViewTreeObserver.OnDrawListener> {
                verify(viewTreeObserver).addOnDrawListener(capture())
                firstValue.onDraw()
            }

            verify(callback).onFirstFrameDrawn(1.seconds.inWholeNanoseconds)
            verify(viewTreeObserver).isAlive
            verify(viewTreeObserver).removeOnDrawListener(any())
            verifyNoMoreInteractions()
        }
    }

    @Test
    fun `M not call onFirstFrameDrawn W viewTreeObserver is not alive`() {
        // Given
        whenever(viewTreeObserver.isAlive) doReturn false

        // When
        createHandle()

        // Then
        verifyNoInteractions(callback)
    }

    @Test
    fun `M call onFirstFrameDrawn only once W onDraw is called twice`() {
        // Given
        currentTime += 1.seconds

        // When
        createHandle()

        // Then
        inOrder(callback, viewTreeObserver) {
            verify(viewTreeObserver).isAlive

            argumentCaptor<ViewTreeObserver.OnDrawListener> {
                verify(viewTreeObserver).addOnDrawListener(capture())
                firstValue.onDraw()
                firstValue.onDraw()
            }

            verify(callback).onFirstFrameDrawn(1.seconds.inWholeNanoseconds)
            verify(viewTreeObserver).isAlive
            verify(viewTreeObserver).removeOnDrawListener(any())
            verifyNoMoreInteractions()
        }
    }

    @Test
    fun `M not add listener to registry W decorView exists`() {
        // Given
        whenever(window.peekDecorView()) doReturn decorView

        // When
        createHandle()

        // Then
        verify(windowCallbackRegistry, never()).addListener(any(), any())
    }

    // endregion

    // region error handling

    @Test
    fun `M call internalLogger W addOnDrawListener throws IllegalStateException`() {
        // Given
        val illegalStateException = IllegalStateException()
        whenever(viewTreeObserver.addOnDrawListener(any())) doThrow illegalStateException

        // When
        createHandle()

        // Then
        verifyNoInteractions(callback)

        inOrder(viewTreeObserver, internalLogger) {
            verify(viewTreeObserver).isAlive
            verify(viewTreeObserver).addOnDrawListener(any())

            verify(internalLogger).log(
                level = eq(InternalLogger.Level.WARN),
                target = eq(InternalLogger.Target.TELEMETRY),
                messageBuilder = any(),
                throwable = eq(illegalStateException),
                onlyOnce = eq(false),
                additionalProperties = anyOrNull()
            )

            verifyNoMoreInteractions()
        }
    }

    @Test
    fun `M call internalLogger W removeOnDrawListener throws IllegalStateException`() {
        // Given
        val illegalStateException = IllegalStateException()
        whenever(viewTreeObserver.removeOnDrawListener(any())) doThrow illegalStateException
        currentTime += 1.seconds

        // When
        createHandle()

        // Then
        verifyNoInteractions(callback)

        inOrder(callback, viewTreeObserver, internalLogger) {
            verify(viewTreeObserver).isAlive

            argumentCaptor<ViewTreeObserver.OnDrawListener> {
                verify(viewTreeObserver).addOnDrawListener(capture())
                firstValue.onDraw()
            }

            verify(callback).onFirstFrameDrawn(1.seconds.inWholeNanoseconds)

            verify(viewTreeObserver).isAlive
            verify(viewTreeObserver).removeOnDrawListener(any())

            verify(internalLogger).log(
                level = eq(InternalLogger.Level.WARN),
                target = eq(InternalLogger.Target.TELEMETRY),
                messageBuilder = any(),
                throwable = eq(illegalStateException),
                onlyOnce = eq(false),
                additionalProperties = anyOrNull()
            )

            verifyNoMoreInteractions()
        }
    }

    // endregion

    // region unsubscribe

    @Test
    fun `M remove listener from registry W unsubscribe called`() {
        // Given - subscribe without auto-triggering onContentChanged
        whenever(windowCallbackRegistry.addListener(any(), any())).then { }

        val handle = createHandle()

        // When
        handle.unsubscribe()

        // Then
        verify(windowCallbackRegistry).removeListener(eq(activity), any())
    }

    @Test
    fun `M call removeListener once W unsubscribe called twice`() {
        // Given - subscribe without auto-triggering onContentChanged
        whenever(windowCallbackRegistry.addListener(any(), any())).then { }

        val handle = createHandle()

        // When
        handle.unsubscribe()
        handle.unsubscribe()

        // Then
        inOrder(windowCallbackRegistry) {
            verify(windowCallbackRegistry).addListener(eq(activity), any())
            verify(windowCallbackRegistry).removeListener(eq(activity), any())
        }
        verifyNoMoreInteractions(windowCallbackRegistry)
    }

    @Test
    fun `M not register drawListener W unsubscribe called before onContentChanged`() {
        // Given
        whenever(windowCallbackRegistry.addListener(any(), any())).doAnswer {
            val argHandle = it.getArgument<RumFirstDrawTimeReporterHandleImpl>(1)
            argHandle.unsubscribe()
            argHandle.onContentChanged()
        }

        // When
        createHandle()

        // Then
        inOrder(windowCallbackRegistry, callback, viewTreeObserver) {
            verify(windowCallbackRegistry).addListener(eq(activity), any())
            verify(windowCallbackRegistry, times(2)).removeListener(eq(activity), any())

            verifyNoMoreInteractions()
        }
    }

    @Test
    fun `M not call callback W unsubscribe called after drawListener registered`() {
        // Given
        val handle = createHandle()

        // Then
        inOrder(windowCallbackRegistry, callback, viewTreeObserver) {
            verify(windowCallbackRegistry).addListener(eq(activity), any())
            verify(windowCallbackRegistry).removeListener(eq(activity), any())
            verify(viewTreeObserver).isAlive

            argumentCaptor<ViewTreeObserver.OnDrawListener> {
                verify(viewTreeObserver).addOnDrawListener(capture())
                handle.unsubscribe()
                firstValue.onDraw()
            }

            verify(viewTreeObserver).isAlive
            verify(viewTreeObserver).removeOnDrawListener(any())
            verifyNoMoreInteractions()
        }
    }

    // endregion

    private fun createHandle(): RumFirstDrawTimeReporterHandleImpl {
        return RumFirstDrawTimeReporterHandleImpl(
            callback = callback,
            activity = activity,
            internalLogger = internalLogger,
            timeProviderNs = { currentTime.inWholeNanoseconds },
            windowCallbacksRegistry = windowCallbackRegistry,
            handler = handler
        )
    }
}
