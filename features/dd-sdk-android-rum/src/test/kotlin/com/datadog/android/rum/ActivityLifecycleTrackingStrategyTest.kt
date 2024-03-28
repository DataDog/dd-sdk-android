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
import android.os.Bundle
import android.view.Window
import com.datadog.android.rum.internal.monitor.AdvancedRumMonitor
import com.datadog.android.rum.internal.tracking.AndroidXFragmentLifecycleCallbacks.Companion.STOP_VIEW_DELAY_MS
import com.datadog.android.rum.tracking.ActivityLifecycleTrackingStrategy
import com.datadog.android.rum.utils.config.GlobalRumMonitorTestConfiguration
import com.datadog.android.rum.utils.forge.Configurator
import com.datadog.tools.unit.ObjectTest
import com.datadog.tools.unit.annotations.TestConfigurationsProvider
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import com.datadog.tools.unit.extensions.config.TestConfiguration
import com.datadog.tools.unit.forge.anException
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class),
    ExtendWith(TestConfigurationExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal abstract class ActivityLifecycleTrackingStrategyTest<T> : ObjectTest<T>()
    where T : ActivityLifecycleTrackingStrategy {

    lateinit var testedStrategy: T

    @Mock
    lateinit var mockIntent: Intent

    @Mock
    lateinit var mockActivity: Activity

    @Mock
    lateinit var mockWindow: Window

    @Mock
    lateinit var mockAppContext: Application

    @Mock
    lateinit var mockBadContext: Context

    @Mock
    lateinit var mockScheduledExecutorService: ScheduledExecutorService

    @BeforeEach
    open fun `set up`(forge: Forge) {
        whenever(mockActivity.intent).thenReturn(mockIntent)
        whenever(mockActivity.window).thenReturn(mockWindow)
        whenever(rumMonitor.mockSdkCore.internalLogger) doReturn mock()
        whenever(rumMonitor.mockSdkCore.createScheduledExecutorService()) doReturn mockScheduledExecutorService
        whenever(
            mockScheduledExecutorService.schedule(any(), eq(STOP_VIEW_DELAY_MS), eq(TimeUnit.MILLISECONDS))
        ) doAnswer { invocationOnMock ->
            (invocationOnMock.arguments[0] as Runnable).run()
            null
        }
    }

    @Test
    fun `when register it will register as lifecycle callback`() {
        // When
        testedStrategy.register(rumMonitor.mockSdkCore, mockAppContext)

        // verify
        verify(mockAppContext).registerActivityLifecycleCallbacks(testedStrategy)
    }

    @Test
    fun `when unregister it will remove itself  as lifecycle callback`() {
        // When
        testedStrategy.unregister(mockAppContext)

        // verify
        verify(mockAppContext).unregisterActivityLifecycleCallbacks(testedStrategy)
    }

    @Test
    fun `when register called with non application context will do nothing`() {
        // When
        testedStrategy.register(rumMonitor.mockSdkCore, mockBadContext)

        // verify
        verifyNoInteractions(mockBadContext)
    }

    @Test
    fun `when unregister called with non application context will do nothing`() {
        // When
        testedStrategy.unregister(mockBadContext)

        // verify
        verifyNoInteractions(mockBadContext)
    }

    @Test
    fun `M track synthetics info W onActivityCreated()`(
        @StringForgery testId: String,
        @StringForgery resultId: String
    ) {
        // Given
        testedStrategy.register(rumMonitor.mockSdkCore, mockAppContext)
        val mockIntent = mock<Intent>()
        val mockActivity = mock<Activity>()
        val fakeExtras = Bundle(2).apply {
            putString(ActivityLifecycleTrackingStrategy.EXTRA_SYNTHETICS_TEST_ID, testId)
            putString(ActivityLifecycleTrackingStrategy.EXTRA_SYNTHETICS_RESULT_ID, resultId)
        }
        whenever(mockActivity.intent) doReturn mockIntent
        whenever(mockIntent.extras) doReturn fakeExtras

        // When
        testedStrategy.onActivityCreated(mockActivity, null)

        // verify
        val mockRumMonitor = rumMonitor.mockInstance as AdvancedRumMonitor
        verify(mockRumMonitor).setSyntheticsAttribute(testId, resultId)
    }

    @Test
    fun `M do nothing W onActivityCreated() { getting intent extras throws }`(
        forge: Forge
    ) {
        // Given
        testedStrategy.register(rumMonitor.mockSdkCore, mockAppContext)
        val mockIntent = mock<Intent>()
        val mockActivity = mock<Activity>()
        whenever(mockActivity.intent) doReturn mockIntent
        whenever(mockIntent.extras) doThrow forge.anException()

        // When
        testedStrategy.onActivityCreated(mockActivity, null)

        // verify
        val mockRumMonitor = rumMonitor.mockInstance as AdvancedRumMonitor
        verifyNoInteractions(mockRumMonitor)
    }

    @Test
    fun `M do nothing W onActivityCreated() { no synthetics extra }`() {
        // Given
        testedStrategy.register(rumMonitor.mockSdkCore, mockAppContext)
        val mockIntent = mock<Intent>()
        val mockActivity = mock<Activity>()
        val fakeExtras = Bundle()
        whenever(mockActivity.intent) doReturn mockIntent
        whenever(mockIntent.extras) doReturn fakeExtras

        // When
        testedStrategy.onActivityCreated(mockActivity, null)

        // verify
        val mockRumMonitor = rumMonitor.mockInstance as AdvancedRumMonitor
        verifyNoInteractions(mockRumMonitor)
    }

    companion object {
        val rumMonitor = GlobalRumMonitorTestConfiguration()

        @TestConfigurationsProvider
        @JvmStatic
        fun getTestConfigurations(): List<TestConfiguration> {
            return listOf(rumMonitor)
        }
    }
}
