/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.vitals

import android.util.Log
import com.datadog.android.utils.config.LoggerTestConfiguration
import com.datadog.android.utils.forge.Configurator
import com.datadog.tools.unit.annotations.TestConfigurationsProvider
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import com.datadog.tools.unit.extensions.config.TestConfiguration
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.inOrder
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import fr.xgouchet.elmyr.annotation.DoubleForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
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
internal class VitalReaderRunnableTest {

    lateinit var testedRunnable: VitalReaderRunnable

    @Mock
    lateinit var mockReader: VitalReader

    @Mock
    lateinit var mockExecutor: ScheduledExecutorService

    @Mock
    lateinit var mockObserver: VitalObserver

    @DoubleForgery
    var fakeValue: Double = 0.0

    @BeforeEach
    fun `set up`() {
        testedRunnable = VitalReaderRunnable(
            mockReader,
            mockObserver,
            mockExecutor,
            TEST_PERIOD_MS
        )
    }

    @Test
    fun `𝕄 read data, notify observer and schedule 𝕎 run()`() {
        // Given
        whenever(mockReader.readVitalData()) doReturn fakeValue

        // When
        testedRunnable.run()

        // Then
        inOrder(mockObserver, mockExecutor) {
            verify(mockObserver).onNewSample(fakeValue)
            verify(mockExecutor).schedule(testedRunnable, TEST_PERIOD_MS, TimeUnit.MILLISECONDS)
        }
    }

    @Test
    fun `𝕄 read data and schedule 𝕎 run() {data is null}`() {
        // Given
        whenever(mockReader.readVitalData()) doReturn null

        // When
        testedRunnable.run()

        // Then
        inOrder(mockObserver, mockExecutor) {
            verify(mockObserver, never()).onNewSample(any())
            verify(mockExecutor).schedule(testedRunnable, TEST_PERIOD_MS, TimeUnit.MILLISECONDS)
        }
    }

    @Test
    fun `𝕄 log error 𝕎 run() { rejected by executor }`() {
        // Given
        val exception = RejectedExecutionException()
        whenever(mockExecutor.schedule(eq(testedRunnable), any(), any())) doThrow exception

        // When
        testedRunnable.run()

        // Then
        verify(logger.mockSdkLogHandler).handleLog(
            Log.ERROR,
            "Unable to schedule Vitals monitoring task on the executor",
            exception
        )
    }

    companion object {
        private const val TEST_PERIOD_MS = 50L

        val logger = LoggerTestConfiguration()

        @TestConfigurationsProvider
        @JvmStatic
        fun getTestConfigurations(): List<TestConfiguration> {
            return listOf(logger)
        }
    }
}
