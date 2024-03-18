/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.anr

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.datadog.android.core.feature.event.ThreadDump
import com.datadog.android.core.internal.utils.loggableStackTrace
import com.datadog.android.rum.RumAttributes
import com.datadog.android.rum.RumErrorSource
import com.datadog.android.rum.utils.config.ApplicationContextTestConfiguration
import com.datadog.android.rum.utils.config.GlobalRumMonitorTestConfiguration
import com.datadog.android.rum.utils.config.MainLooperTestConfiguration
import com.datadog.android.rum.utils.forge.Configurator
import com.datadog.tools.unit.annotations.TestConfigurationsProvider
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import com.datadog.tools.unit.extensions.config.TestConfiguration
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.atLeast
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.isA
import org.mockito.kotlin.reset
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class),
    ExtendWith(TestConfigurationExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(value = Configurator::class)
internal class ANRDetectorRunnableTest {

    private lateinit var testedRunnable: ANRDetectorRunnable

    @Mock
    lateinit var mockHandler: Handler

    @Mock
    lateinit var mockLooper: Looper

    @BeforeEach
    fun `set up`() {
        whenever(mockHandler.post(any())) doReturn true
        whenever(mockHandler.looper) doReturn mockLooper
        whenever(mockLooper.thread) doReturn Thread.currentThread()

        testedRunnable = ANRDetectorRunnable(
            rumMonitor.mockSdkCore,
            mockHandler,
            TEST_ANR_THRESHOLD_MS,
            TEST_ANR_TEST_DELAY_MS
        )
    }

    @Test
    fun `𝕄 report RUM error 𝕎 run() {ANR detected}`() {
        // When
        Thread(testedRunnable).start()
        Thread.sleep(TEST_ANR_TEST_DELAY_MS)

        // Then
        Thread.sleep(TEST_ANR_THRESHOLD_MS)
        argumentCaptor<Map<String, Any?>> {
            val anrExceptionCaptor = argumentCaptor<Throwable>()
            verify(rumMonitor.mockInstance).addError(
                message = eq("Application Not Responding"),
                source = eq(RumErrorSource.SOURCE),
                throwable = anrExceptionCaptor.capture(),
                attributes = capture()
            )
            assertThat(anrExceptionCaptor.lastValue).isInstanceOf(ANRException::class.java)

            assertThat(lastValue).containsOnlyKeys(RumAttributes.INTERNAL_ALL_THREADS)
            @Suppress("UNCHECKED_CAST")
            val allThreads = lastValue[RumAttributes.INTERNAL_ALL_THREADS] as List<ThreadDump>
            assertThat(allThreads.all { !it.crashed }).isTrue()

            val anrThread = allThreads.firstOrNull { it.name == Thread.currentThread().name }
            check(anrThread != null)
            assertThat(anrThread.stack).isEqualTo(anrExceptionCaptor.lastValue.loggableStackTrace())
        }

        argumentCaptor<Runnable> {
            verify(mockHandler).post(capture())
            testedRunnable.stop()
            lastValue.run()
        }
    }

    @Test
    fun `𝕄 not report RUM error 𝕎 run() {no ANR}`() {
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
        verifyNoInteractions(rumMonitor.mockInstance)
    }

    @Test
    fun `𝕄 wait ANR resolution before scheduling next runnable 𝕎 run()`() {
        // When
        Thread(testedRunnable).start()
        Thread.sleep(TEST_ANR_TEST_DELAY_MS)

        // Then
        argumentCaptor<Runnable> {
            verify(mockHandler).post(capture())
            Thread.sleep(TEST_ANR_THRESHOLD_MS)
            verify(mockHandler).looper
            verifyNoMoreInteractions(mockHandler)
            reset(mockHandler)

            lastValue.run()
            // +10 is to remove flakiness, otherwise it seems current thread can resume execution
            // before ANR test thread
            Thread.sleep(TEST_ANR_TEST_DELAY_MS + 10)
            verify(mockHandler).post(capture())
            lastValue.run()
        }
    }

    @Test
    fun `𝕄 not report RUM error 𝕎 stop() + run()`() {
        // When
        testedRunnable.stop()
        Thread(testedRunnable).start()
        Thread.sleep(TEST_ANR_TEST_DELAY_MS)

        // Then
        verifyNoInteractions(rumMonitor.mockInstance, mockHandler)
    }

    @Test
    fun `𝕄 not do anything 𝕎 run() {handler returns false}`() {
        // Given
        whenever(mockHandler.post(any())) doReturn false

        // When
        val thread = Thread(testedRunnable)
        thread.start()
        Thread.sleep(TEST_ANR_THRESHOLD_MS)
        Thread.sleep(TEST_ANR_TEST_DELAY_MS)

        // Then
        verify(mockHandler).post(any())
        verifyNoInteractions(rumMonitor.mockInstance)
        assertThat(thread.isAlive).isFalse()
    }

    @Test
    fun `𝕄 schedule runnable regularly 𝕎 run()`(
        @IntForgery(1, 10) repeatCount: Int
    ) {
        // Given
        whenever(mockHandler.post(any())) doAnswer {
            Thread(it.getArgument(0) as Runnable).start()
            true
        }

        // When
        Thread(testedRunnable).start()
        repeat(repeatCount) {
            Thread.sleep(TEST_ANR_TEST_DELAY_MS)
        }
        Thread.sleep(TEST_ANR_TEST_DELAY_MS / 2)
        testedRunnable.stop()

        // Then
        verifyNoInteractions(rumMonitor.mockInstance)
        verify(
            mockHandler,
            atLeast(repeatCount)
        ).post(isA<ANRDetectorRunnable.CallbackRunnable>())
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
