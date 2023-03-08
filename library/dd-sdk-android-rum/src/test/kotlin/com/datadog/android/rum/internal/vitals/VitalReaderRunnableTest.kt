/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.vitals

import com.datadog.android.rum.internal.domain.scope.RumViewScope
import com.datadog.android.rum.utils.config.InternalLoggerTestConfiguration
import com.datadog.android.rum.utils.forge.Configurator
import com.datadog.android.v2.api.Feature
import com.datadog.android.v2.api.InternalLogger
import com.datadog.android.v2.api.SdkCore
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
import com.nhaarman.mockitokotlin2.verifyZeroInteractions
import com.nhaarman.mockitokotlin2.whenever
import fr.xgouchet.elmyr.annotation.DoubleForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

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

    @Mock
    lateinit var mockSdkCore: SdkCore

    @DoubleForgery
    var fakeValue: Double = 0.0

    @BeforeEach
    fun `set up`() {
        val rumContext = mapOf<String, Any?>(
            "view_type" to RumViewScope.RumViewType.FOREGROUND
        )
        whenever(mockSdkCore.getFeatureContext(Feature.RUM_FEATURE_NAME)) doReturn rumContext
        testedRunnable = VitalReaderRunnable(
            mockSdkCore,
            mockReader,
            mockObserver,
            mockExecutor,
            TEST_PERIOD_MS
        )
    }

    @Test
    fun `𝕄 read data, notify observer and schedule 𝕎 run { viewType == FOREGROUND }()`() {
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

    @ParameterizedTest
    @EnumSource(
        value = RumViewScope.RumViewType::class,
        names = ["FOREGROUND"],
        mode = EnumSource.Mode.EXCLUDE
    )
    fun `𝕄 not read data, not notify observer but schedule 𝕎 run { viewType != FOREGROUND }()`(
        viewType: RumViewScope.RumViewType
    ) {
        // Given
        val rumContext = mapOf<String, Any?>(
            "view_type" to viewType
        )
        whenever(mockSdkCore.getFeatureContext(Feature.RUM_FEATURE_NAME)) doReturn rumContext

        // When
        testedRunnable.run()

        // Then
        verifyZeroInteractions(mockReader)
        verifyZeroInteractions(mockObserver)
        verify(mockExecutor).schedule(testedRunnable, TEST_PERIOD_MS, TimeUnit.MILLISECONDS)
    }

    @Test
    fun `𝕄 not read data, not notify observer but schedule 𝕎 run { wrong type of viewType }()`() {
        // Given
        val rumContext = mapOf<String, Any?>(
            "view_type" to Any()
        )
        whenever(mockSdkCore.getFeatureContext(Feature.RUM_FEATURE_NAME)) doReturn rumContext

        // When
        testedRunnable.run()

        // Then
        verifyZeroInteractions(mockReader)
        verifyZeroInteractions(mockObserver)
        verify(mockExecutor).schedule(testedRunnable, TEST_PERIOD_MS, TimeUnit.MILLISECONDS)
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
        verify(logger.mockInternalLogger).log(
            InternalLogger.Level.ERROR,
            targets = listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
            "Unable to schedule Vitals monitoring task on the executor",
            exception
        )
    }

    companion object {
        private const val TEST_PERIOD_MS = 50L

        val logger = InternalLoggerTestConfiguration()

        @TestConfigurationsProvider
        @JvmStatic
        fun getTestConfigurations(): List<TestConfiguration> {
            return listOf(logger)
        }
    }
}
