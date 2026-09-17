/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.prelaunch

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import com.datadog.android.rum.DdRumContentProvider
import com.datadog.android.rum.internal.startup.PreLaunchRumAppStartupDetector
import com.datadog.android.rum.internal.startup.RumAppStartupDetector
import com.datadog.tools.unit.setFieldValue
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.whenever
import org.mockito.kotlin.any
import org.mockito.kotlin.times
import org.mockito.kotlin.verify

@ExtendWith(MockitoExtension::class)
internal class AppLaunchCollectorProviderTest {
    @Mock lateinit var context: Context
    @Mock lateinit var application: Application
    private lateinit var provider: AppLaunchCollectorProvider

    @BeforeEach
    fun setUp() {
        resetDetector()
        DdRumContentProvider.processImportance =
            ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
        provider = AppLaunchCollectorProvider()
        provider.setFieldValue("mContext", context)
        whenever(context.applicationContext).thenReturn(application)
    }

    @AfterEach
    fun tearDown() {
        resetDetector()
        DdRumContentProvider.processImportance = 0
    }

    @Test
    fun `M install detector and return true W onCreate { foreground process }`() {
        assertThat(provider.onCreate()).isTrue()
        assertThat(PreLaunchRumAppStartupDetector.isInstalled).isTrue()
    }

    @Test
    fun `M install detector once W onCreate { called twice }`() {
        assertThat(provider.onCreate()).isTrue()
        assertThat(provider.onCreate()).isTrue()

        verify(application, times(1)).registerActivityLifecycleCallbacks(any())
    }

    @Test
    fun `M return false W onCreate { background process }`() {
        DdRumContentProvider.processImportance =
            ActivityManager.RunningAppProcessInfo.IMPORTANCE_CACHED

        assertThat(provider.onCreate()).isFalse()
        assertThat(PreLaunchRumAppStartupDetector.isInstalled).isFalse()
    }

    @Test
    fun `M return false W onCreate { application context is not Application }`() {
        whenever(context.applicationContext).thenReturn(context)

        assertThat(provider.onCreate()).isFalse()
        assertThat(PreLaunchRumAppStartupDetector.isInstalled).isFalse()
    }

    private fun resetDetector() {
        val detectorField = PreLaunchRumAppStartupDetector::class.java.getDeclaredField("detector")
        detectorField.isAccessible = true
        (detectorField.get(PreLaunchRumAppStartupDetector) as? RumAppStartupDetector)?.destroy()
        detectorField.set(PreLaunchRumAppStartupDetector, null)

        val listenerField = PreLaunchRumAppStartupDetector::class.java.getDeclaredField("listener")
        listenerField.isAccessible = true
        listenerField.set(PreLaunchRumAppStartupDetector, null)

        val eventsField = PreLaunchRumAppStartupDetector::class.java.getDeclaredField("pendingEvents")
        eventsField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        (eventsField.get(PreLaunchRumAppStartupDetector) as MutableList<Any>).clear()
    }
}
