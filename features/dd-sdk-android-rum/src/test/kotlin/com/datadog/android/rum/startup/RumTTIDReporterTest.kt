/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.startup

import android.app.Activity
import android.os.Handler
import android.os.Message
import android.view.View
import android.view.ViewTreeObserver
import android.view.Window
import com.datadog.android.rum.internal.startup.RumStartupScenario
import com.datadog.android.rum.internal.startup.RumTTIDReporterImpl
import com.datadog.android.rum.internal.startup.RumTTIDReporterListener
import com.datadog.android.rum.internal.utils.RumWindowCallbackListener
import com.datadog.android.rum.internal.utils.RumWindowCallbacksRegistry
import com.datadog.android.rum.utils.forge.Configurator
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mockito.mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.verify
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
class RumTTIDReporterTest {
    @Test
    fun testOne() {
        var currentTime: Duration = 0.seconds
        val windowCallbackRegistry = mock<RumWindowCallbacksRegistry>()
        val handler = mock<Handler>()
        val listener = mock<RumTTIDReporterListener>()
        val activity = mock<Activity>()
        val window = mock<Window>()
        val decorView = mock<View>()
        val viewTreeObserver = mock<ViewTreeObserver>()

        val scenario1 = RumStartupScenario.Cold(
            initialTimeNanos = 0,
            hasSavedInstanceStateBundle = true,
            activity = activity,
        )

        whenever(activity.window) doReturn window
        whenever(window.peekDecorView()) doReturn null
        whenever(window.decorView) doReturn decorView

        whenever(decorView.viewTreeObserver) doReturn viewTreeObserver

        whenever(windowCallbackRegistry.addListener(any(), any())).doAnswer {
            val argListener = it.getArgument<RumWindowCallbackListener>(1)
            argListener.onContentChanged()
        }

        whenever(viewTreeObserver.addOnDrawListener(any())).doAnswer {
            val argListener = it.getArgument<ViewTreeObserver.OnDrawListener>(0)
            argListener.onDraw()
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

        val reporter = RumTTIDReporterImpl(
            timeProviderNanos = { currentTime.inWholeNanoseconds },
            windowCallbacksRegistry = windowCallbackRegistry,
            handler = handler,
            listener = listener
        )

        reporter.onAppStartupDetected(
            scenario1
        )

        verify(listener).onTTID(scenario1, 0)
    }
}
