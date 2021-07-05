/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.anr

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.datadog.android.rum.RumErrorSource
import com.datadog.android.utils.config.ApplicationContextTestConfiguration
import com.datadog.android.utils.config.GlobalRumMonitorTestConfiguration
import com.datadog.android.utils.config.MainLooperTestConfiguration
import com.datadog.android.utils.forge.Configurator
import com.datadog.tools.unit.annotations.TestConfigurationsProvider
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import com.datadog.tools.unit.extensions.config.TestConfiguration
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyZeroInteractions
import com.nhaarman.mockitokotlin2.whenever
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
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
    ExtendWith(ForgeExtension::class),
    ExtendWith(TestConfigurationExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class ANRDetectorRunnableTest {

    lateinit var testedRunnable: ANRDetectorRunnable

    @Mock
    lateinit var mockHandler: Handler

    @Mock
    lateinit var mockLooper: Looper

    @BeforeEach
    fun `set up`() {
        testedRunnable = ANRDetectorRunnable(
            mockHandler,
            TEST_ANR_THRESHOLD_MS,
            TEST_ANR_TEST_DELAY_MS
        )
    }

    @Test
    fun `𝕄 report RUM error 𝕎 run() {ANR detected}`() {
        // Given
        whenever(mockHandler.post(any())) doReturn true
        whenever(mockHandler.looper) doReturn mockLooper
        whenever(mockLooper.thread) doReturn Thread.currentThread()

        // When
        Thread(testedRunnable).start()
        Thread.sleep(TEST_ANR_TEST_DELAY_MS)

        // Then
        Thread.sleep(TEST_ANR_THRESHOLD_MS)
        verify(rumMonitor.mockInstance).addError(
            eq("Application Not Responding"),
            eq(RumErrorSource.SOURCE),
            any(),
            eq(emptyMap())
        )

        argumentCaptor<Runnable> {
            verify(mockHandler).post(capture())
            testedRunnable.stop()
            lastValue.run()
        }
    }

    @Test
    fun `𝕄 not report RUM error 𝕎 run() {no ANR}`() {
        // Given
        whenever(mockHandler.post(any())) doReturn true

        // When
        Thread(testedRunnable).start()
        Thread.sleep(TEST_ANR_TEST_DELAY_MS)

        // Then
        argumentCaptor<Runnable> {
            verify(mockHandler).post(capture())
            testedRunnable.stop()
            lastValue.run()
        }
        Thread.sleep(TEST_ANR_THRESHOLD_MS)
        verifyZeroInteractions(rumMonitor.mockInstance)
    }

    @Test
    fun `𝕄 not report RUM error 𝕎 stop() + run()`() {
        // Given
        whenever(mockHandler.post(any())) doReturn true

        // When
        testedRunnable.stop()
        Thread(testedRunnable).start()
        Thread.sleep(TEST_ANR_TEST_DELAY_MS)

        // Then
        verifyZeroInteractions(rumMonitor.mockInstance, mockHandler)
    }

    companion object {
        private const val TEST_ANR_THRESHOLD_MS = 500L
        private const val TEST_ANR_TEST_DELAY_MS = 50L

        val appContext = ApplicationContextTestConfiguration(Context::class.java)
        val rumMonitor = GlobalRumMonitorTestConfiguration()
        val mainLooper = MainLooperTestConfiguration()

        @TestConfigurationsProvider
        @JvmStatic
        fun getTestConfigurations(): List<TestConfiguration> {
            return listOf(appContext, rumMonitor, mainLooper)
        }
    }
}
