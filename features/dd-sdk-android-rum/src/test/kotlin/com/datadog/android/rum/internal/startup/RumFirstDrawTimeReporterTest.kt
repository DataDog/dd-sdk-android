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
import com.datadog.android.rum.internal.domain.Time
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
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.lang.ref.WeakReference
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class),
    ExtendWith(TestConfigurationExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
class RumFirstDrawTimeReporterTest {

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

    private lateinit var reporter: RumFirstDrawTimeReporterImpl

    private lateinit var weakActivity: WeakReference<Activity>

    private lateinit var scenario: RumStartupScenario

    @BeforeEach
    fun `set up`() {
        weakActivity = WeakReference(activity)

        scenario = RumStartupScenario.Cold(
            initialTime = Time(0, 0),
            hasSavedInstanceStateBundle = true,
            activity = weakActivity,
            appStartActivityOnCreateGapNs = 0.seconds.inWholeNanoseconds
        )

        reporter = RumFirstDrawTimeReporterImpl(
            internalLogger = internalLogger,
            timeProviderNs = { currentTime.inWholeNanoseconds },
            windowCallbacksRegistry = windowCallbackRegistry,
            handler = handler
        )

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

    @Test
    fun `M call onTTIDCalculated W RumTTIDReporter { decorView doesn't exist yet }`() {
        // Given
        currentTime += 1.seconds

        // When
        reporter.subscribeToFirstFrameDrawn(activity, callback)

        // Then
        inOrder(windowCallbackRegistry, callback, viewTreeObserver) {
            verify(windowCallbackRegistry).addListener(eq(activity), any())
            verify(windowCallbackRegistry).removeListener(eq(activity), any())
            verify(viewTreeObserver).isAlive

            argumentCaptor<ViewTreeObserver.OnDrawListener> {
                verify(viewTreeObserver).addOnDrawListener(capture())
                firstValue.onDraw()
            }

            verify(
                callback
            ).onFirstFrameDrawn(1.seconds.inWholeNanoseconds)
            verify(viewTreeObserver).isAlive
            verify(viewTreeObserver).removeOnDrawListener(any())
            verifyNoMoreInteractions()
        }
    }

    @Test
    fun `M call onTTIDCalculated W RumTTIDReporter { decorView exists }`() {
        // Given
        whenever(window.peekDecorView()) doReturn decorView

        currentTime += 1.seconds

        // When
        reporter.subscribeToFirstFrameDrawn(activity, callback)

        // Then
        inOrder(windowCallbackRegistry, callback, viewTreeObserver) {
            verify(viewTreeObserver).isAlive

            argumentCaptor<ViewTreeObserver.OnDrawListener> {
                verify(viewTreeObserver).addOnDrawListener(capture())
                firstValue.onDraw()
            }

            verify(
                callback
            ).onFirstFrameDrawn(1.seconds.inWholeNanoseconds)
            verify(viewTreeObserver).isAlive
            verify(viewTreeObserver).removeOnDrawListener(any())
            verifyNoMoreInteractions()
        }
    }

    @Test
    fun `M call onTTIDCalculated W RumTTIDReporter { decorView exists but not attached to window }`() {
        // Given
        whenever(window.peekDecorView()) doReturn decorView
        whenever(decorView.isAttachedToWindow) doReturn false

        currentTime += 1.seconds

        // When
        reporter.subscribeToFirstFrameDrawn(activity, callback)

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

            verify(
                callback
            ).onFirstFrameDrawn(1.seconds.inWholeNanoseconds)
            verify(viewTreeObserver).isAlive
            verify(viewTreeObserver).removeOnDrawListener(any())
            verifyNoMoreInteractions()
        }
    }

    @Test
    fun `M not call onTTIDCalculated W RumTTIDReporter { viewTreeObserver is not alive }`() {
        // Given
        whenever(viewTreeObserver.isAlive) doReturn false

        // When
        reporter.subscribeToFirstFrameDrawn(activity, callback)

        // Then
        verifyNoInteractions(callback)
    }

    @Test
    fun `M call onTTIDCalculated only once W RumTTIDReporter { onDraw is called twice }`() {
        // Given
        currentTime += 1.seconds

        // When
        reporter.subscribeToFirstFrameDrawn(activity, callback)

        // Then
        inOrder(callback, viewTreeObserver) {
            verify(viewTreeObserver).isAlive

            argumentCaptor<ViewTreeObserver.OnDrawListener> {
                verify(viewTreeObserver).addOnDrawListener(capture())
                firstValue.onDraw()
                firstValue.onDraw()
            }

            verify(
                callback
            ).onFirstFrameDrawn(1.seconds.inWholeNanoseconds)
            verify(viewTreeObserver).isAlive
            verify(viewTreeObserver).removeOnDrawListener(any())
            verifyNoMoreInteractions()
        }
    }

    @Test
    fun `M call internalLogger W addOnDrawListener { if it throws IllegalStateException }`() {
        // Given
        val illegalStateException = IllegalStateException()
        whenever(viewTreeObserver.addOnDrawListener(any())) doThrow illegalStateException

        // When
        reporter.subscribeToFirstFrameDrawn(activity, callback)

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
    fun `M call internalLogger W removeOnDrawListener { if it throws IllegalStateException }`() {
        // Given
        val illegalStateException = IllegalStateException()
        whenever(viewTreeObserver.removeOnDrawListener(any())) doThrow illegalStateException

        currentTime += 1.seconds

        // When
        reporter.subscribeToFirstFrameDrawn(activity, callback)

        // Then
        verifyNoInteractions(callback)

        inOrder(callback, viewTreeObserver, internalLogger) {
            verify(viewTreeObserver).isAlive

            argumentCaptor<ViewTreeObserver.OnDrawListener> {
                verify(viewTreeObserver).addOnDrawListener(capture())
                firstValue.onDraw()
            }

            verify(
                callback
            ).onFirstFrameDrawn(1.seconds.inWholeNanoseconds)

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

    // region RUMS-5469: TTID not emitted when first Activity finishes before first draw

    /**
     * Regression test for RUMS-5469.
     *
     * When the first Activity calls finish() before its DecorView is ever drawn (e.g. an auth
     * screen that immediately redirects an already-authenticated user), the
     * [View.OnAttachStateChangeListener.onViewDetachedFromWindow] callback fires before
     * [android.view.ViewTreeObserver.OnDrawListener.onDraw] ever fires.
     *
     * Expected correct behaviour: the reporter should use the detach timestamp as a fallback and
     * invoke [RumFirstDrawTimeReporter.Callback.onFirstFrameDrawn] so that the TTID event is
     * still emitted.
     *
     * This test FAILS on current code because [RumFirstDrawTimeReporterImpl.onViewDetachedFromWindow]
     * has an empty body and never invokes the callback.
     */
    @Test
    fun `M call onFirstFrameDrawn W decorView detaches before first draw { RUMS-5469 }`() {
        // Given
        whenever(window.peekDecorView()) doReturn decorView
        whenever(decorView.isAttachedToWindow) doReturn false

        currentTime += 1.seconds

        // When
        reporter.subscribeToFirstFrameDrawn(activity, callback)

        // Simulate the Activity calling finish() — the DecorView detaches from the window
        // before the first draw pass ever fires.
        argumentCaptor<View.OnAttachStateChangeListener> {
            verify(decorView).addOnAttachStateChangeListener(capture())
            firstValue.onViewDetachedFromWindow(decorView)
        }

        // Then: the callback MUST be invoked with the current timestamp as the fallback TTID value.
        // This assertion FAILS on current code because onViewDetachedFromWindow has an empty body.
        verify(callback).onFirstFrameDrawn(1.seconds.inWholeNanoseconds)
    }

    /**
     * Regression test for RUMS-5469 — variant where the DecorView does not yet exist when
     * [RumFirstDrawTimeReporter.subscribeToFirstFrameDrawn] is called (peekDecorView returns null),
     * and the Activity calls finish() before the DecorView is ever attached.
     *
     * Expected correct behaviour: [RumFirstDrawTimeReporter.Callback.onFirstFrameDrawn] is still
     * invoked via the detach-fallback path so that the TTID event is emitted.
     *
     * This test FAILS on current code for the same reason as the sibling test above.
     */
    @Test
    fun `M call onFirstFrameDrawn W decorView not ready then detaches before first draw { RUMS-5469 }`() {
        // Given - peekDecorView returns null so we go through the windowCallbacksRegistry path.
        // The registry listener immediately calls onContentChanged, which triggers onDecorViewReady.
        // At that point decorView is not yet attached to the window.
        whenever(window.peekDecorView()) doReturn null
        whenever(decorView.isAttachedToWindow) doReturn false

        currentTime += 1.seconds

        // When
        reporter.subscribeToFirstFrameDrawn(activity, callback)

        // Simulate the Activity calling finish() — DecorView detaches without ever drawing.
        argumentCaptor<View.OnAttachStateChangeListener> {
            verify(decorView).addOnAttachStateChangeListener(capture())
            firstValue.onViewDetachedFromWindow(decorView)
        }

        // Then: the callback MUST be invoked with the current timestamp as the fallback TTID value.
        // This assertion FAILS on current code because onViewDetachedFromWindow has an empty body.
        verify(callback).onFirstFrameDrawn(1.seconds.inWholeNanoseconds)
    }

    // endregion
}
