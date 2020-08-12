/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.view.Window
import com.datadog.android.rum.internal.monitor.AdvancedRumMonitor
import com.datadog.android.rum.tracking.ActivityLifecycleTrackingStrategy
import com.datadog.android.utils.forge.Configurator
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyZeroInteractions
import com.nhaarman.mockitokotlin2.whenever
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal abstract class ActivityLifecycleTrackingStrategyTest {

    lateinit var testedStrategy: ActivityLifecycleTrackingStrategy

    @Mock
    lateinit var mockIntent: Intent

    @Mock
    lateinit var mockRumMonitor: AdvancedRumMonitor

    @Mock
    lateinit var mockActivity: Activity

    @Mock
    lateinit var mockWindow: Window

    @Mock
    lateinit var mockAppContext: Application

    @Mock
    lateinit var mockBadContext: Context

    @BeforeEach
    open fun `set up`(forge: Forge) {
        GlobalRum.registerIfAbsent(mockRumMonitor)
        whenever(mockActivity.intent).thenReturn(mockIntent)
        whenever(mockActivity.window).thenReturn(mockWindow)
    }

    @AfterEach
    open fun `tear down`() {
        GlobalRum.monitor = NoOpRumMonitor()
        GlobalRum.isRegistered.set(false)
    }

    @Test
    fun `when register it will register as lifecycle callback`() {
        // when
        testedStrategy.register(mockAppContext)

        // verify
        verify(mockAppContext).registerActivityLifecycleCallbacks(testedStrategy)
    }

    @Test
    fun `when unregister it will remove itself  as lifecycle callback`() {
        // when
        testedStrategy.unregister(mockAppContext)

        // verify
        verify(mockAppContext).unregisterActivityLifecycleCallbacks(testedStrategy)
    }

    @Test
    fun `when register called with non application context will do nothing`() {
        // when
        testedStrategy.register(mockBadContext)

        // verify
        verifyZeroInteractions(mockBadContext)
    }

    @Test
    fun `when unregister called with non application context will do nothing`() {
        // when
        testedStrategy.unregister(mockBadContext)

        // verify
        verifyZeroInteractions(mockBadContext)
    }
}
