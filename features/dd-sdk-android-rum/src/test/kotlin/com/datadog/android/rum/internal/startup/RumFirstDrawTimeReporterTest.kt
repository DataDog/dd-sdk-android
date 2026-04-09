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

    // region RUMS-5469 reproduction: TTID not emitted when launch Activity finishes before first draw

    /**
     * Reproduces RUMS-5469: When the launch Activity (e.g. AuthenticationActivity) calls finish()
     * before its decor view completes a single draw pass, the [View.OnAttachStateChangeListener]
     * registered in [RumFirstDrawTimeReporterImpl.onDecorViewReady] receives
     * [View.OnAttachStateChangeListener.onViewDetachedFromWindow] instead of
     * [View.OnAttachStateChangeListener.onViewAttachedToWindow]. The current implementation has an
     * empty body for [View.OnAttachStateChangeListener.onViewDetachedFromWindow] — there is no
     * fallback and [RumFirstDrawTimeReporter.Callback.onFirstFrameDrawn] is never called, so the
     * TTID event is silently dropped.
     *
     * Expected (correct) behaviour: the SDK should handle the detach-before-draw case gracefully,
     * e.g. by invoking the callback with a best-effort timestamp so that TTID is still emitted.
     *
     * This test asserts the CORRECT behaviour and therefore FAILS against the buggy code, proving
     * the bug exists.
     */
    @Test
    fun `M call onFirstFrameDrawn W subscribeToFirstFrameDrawn { decorView detaches before first draw - RUMS-5469 }`() {
        // Given — decor view is not yet attached (fast-finishing interstitial Activity)
        whenever(window.peekDecorView()) doReturn decorView
        whenever(decorView.isAttachedToWindow) doReturn false

        currentTime += 1.seconds

        // When
        reporter.subscribeToFirstFrameDrawn(activity, callback)

        // Capture the OnAttachStateChangeListener that was registered on the decor view
        val attachListenerCaptor = argumentCaptor<View.OnAttachStateChangeListener>()
        org.mockito.kotlin.verify(decorView).addOnAttachStateChangeListener(attachListenerCaptor.capture())
        val attachListener = attachListenerCaptor.firstValue

        // Simulate the Activity finishing before the first draw — onViewDetachedFromWindow fires
        // without onViewAttachedToWindow ever being called (the no-op bug path)
        attachListener.onViewDetachedFromWindow(decorView)

        // Then — the SDK should have invoked the callback with a fallback timestamp so that
        // a TTID event is still emitted. In the buggy code onViewDetachedFromWindow is an empty
        // body, so this assertion FAILS — proving the missing fallback.
        org.mockito.kotlin.verify(callback).onFirstFrameDrawn(org.mockito.kotlin.any())
    }

    // endregion

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
}
