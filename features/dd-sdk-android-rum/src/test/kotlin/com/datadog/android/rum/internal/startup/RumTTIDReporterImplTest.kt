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
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.lang.IllegalStateException
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
class RumTTIDReporterImplTest {

    private var currentTime: Duration = 0.seconds

    @Mock
    private lateinit var windowCallbackRegistry: RumWindowCallbacksRegistry

    @Mock
    private lateinit var handler: Handler

    @Mock
    private lateinit var listener: RumTTIDReporter.Listener

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

    private lateinit var reporter: RumTTIDReporterImpl

    private lateinit var weakActivity: WeakReference<Activity>

    private lateinit var scenario: RumStartupScenario

    @BeforeEach
    fun `set up`() {
        weakActivity = WeakReference(activity)

        scenario = RumStartupScenario.Cold(
            initialTimeNanos = 0,
            hasSavedInstanceStateBundle = true,
            activity = weakActivity,
            gap = 0.seconds
        )

        reporter = RumTTIDReporterImpl(
            internalLogger = internalLogger,
            timeProviderNanos = { currentTime.inWholeNanoseconds },
            windowCallbacksRegistry = windowCallbackRegistry,
            handler = handler,
            listener = listener
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
    }

    @Test
    fun `M call onTTIDCalculated W RumTTIDReporter { decorView doesn't exist yet }`() {
        // Given
        currentTime += 1.seconds

        // When
        reporter.onAppStartupDetected(
            scenario
        )

        // Then
        inOrder(windowCallbackRegistry, listener, viewTreeObserver) {
            verify(windowCallbackRegistry).addListener(eq(activity), any())
            verify(windowCallbackRegistry).removeListener(eq(activity), any())
            verify(viewTreeObserver).isAlive

            argumentCaptor<ViewTreeObserver.OnDrawListener> {
                verify(viewTreeObserver).addOnDrawListener(capture())
                firstValue.onDraw()
            }

            verify(listener).onTTIDCalculated(RumTTIDInfo(scenario = scenario, duration = 1.seconds))
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
        reporter.onAppStartupDetected(
            scenario
        )

        // Then
        inOrder(windowCallbackRegistry, listener, viewTreeObserver) {
            verify(viewTreeObserver).isAlive

            argumentCaptor<ViewTreeObserver.OnDrawListener> {
                verify(viewTreeObserver).addOnDrawListener(capture())
                firstValue.onDraw()
            }

            verify(listener).onTTIDCalculated(RumTTIDInfo(scenario = scenario, duration = 1.seconds))
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
        reporter.onAppStartupDetected(
            scenario
        )

        // Then
        verifyNoInteractions(listener)
    }

    @Test
    fun `M call onTTIDCalculated only once W RumTTIDReporter { onDraw is called twice }`() {
        // Given
        currentTime += 1.seconds

        // When
        reporter.onAppStartupDetected(
            scenario
        )

        // Then
        inOrder(listener, viewTreeObserver) {
            verify(viewTreeObserver).isAlive

            argumentCaptor<ViewTreeObserver.OnDrawListener> {
                verify(viewTreeObserver).addOnDrawListener(capture())
                firstValue.onDraw()
                firstValue.onDraw()
            }

            verify(listener).onTTIDCalculated(RumTTIDInfo(scenario = scenario, duration = 1.seconds))
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
        reporter.onAppStartupDetected(
            scenario
        )

        // Then
        verifyNoInteractions(listener)

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
        reporter.onAppStartupDetected(
            scenario
        )

        // Then
        verifyNoInteractions(listener)

        inOrder(listener, viewTreeObserver, internalLogger) {
            verify(viewTreeObserver).isAlive

            argumentCaptor<ViewTreeObserver.OnDrawListener> {
                verify(viewTreeObserver).addOnDrawListener(capture())
                firstValue.onDraw()
            }

            verify(listener).onTTIDCalculated(RumTTIDInfo(scenario = scenario, duration = 1.seconds))

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
