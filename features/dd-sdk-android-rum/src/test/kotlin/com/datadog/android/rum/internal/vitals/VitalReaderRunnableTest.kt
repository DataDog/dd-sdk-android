/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.vitals

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.rum.internal.domain.RumContext
import com.datadog.android.rum.internal.domain.scope.RumViewType
import com.datadog.android.rum.utils.forge.Configurator
import com.datadog.android.api.verifyLog
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import com.datadog.tools.unit.forge.exhaustiveAttributes
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.DoubleForgery
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
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

    private lateinit var testedRunnable: VitalReaderRunnable

    @Mock
    lateinit var mockReader: VitalReader

    @Mock
    lateinit var mockExecutor: ScheduledExecutorService

    @Mock
    lateinit var mockObserver: VitalObserver

    @Mock
    lateinit var mockSdkCore: FeatureSdkCore

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @Forgery
    lateinit var fakeRumContext: RumContext

    @DoubleForgery
    var fakeValue: Double = 0.0

    @BeforeEach
    fun `set up`() {
        fakeRumContext = fakeRumContext
            .copy(viewType = RumViewType.FOREGROUND)
        whenever(mockSdkCore.internalLogger) doReturn mockInternalLogger
        testedRunnable = VitalReaderRunnable(
            mockSdkCore,
            mockReader,
            mockObserver,
            mockExecutor,
            TEST_PERIOD_MS
        )
        testedRunnable.onContextUpdate(Feature.RUM_FEATURE_NAME, fakeRumContext.toMap())
    }

    @Test
    fun `M read data, notify observer and schedule W run { viewType == FOREGROUND }()`() {
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
    fun `M accept only RUM context W onContextUpdate()`(
        @StringForgery fakeFeatureName: String,
        forge: Forge
    ) {
        // Given
        val fakeContext = forge.exhaustiveAttributes()

        // When
        testedRunnable.onContextUpdate(fakeFeatureName, fakeContext)

        // Then
        assertThat(testedRunnable.currentRumContext).isEqualTo(fakeRumContext)
    }

    @ParameterizedTest
    @EnumSource(
        value = RumViewType::class,
        names = ["FOREGROUND"],
        mode = EnumSource.Mode.EXCLUDE
    )
    fun `M not read data, not notify observer but schedule W run { viewType != FOREGROUND }()`(
        viewType: RumViewType
    ) {
        // Given
        fakeRumContext = fakeRumContext
            .copy(viewType = viewType)
        testedRunnable.onContextUpdate(Feature.RUM_FEATURE_NAME, fakeRumContext.toMap())

        // When
        testedRunnable.run()

        // Then
        verifyNoInteractions(mockReader)
        verifyNoInteractions(mockObserver)
        verify(mockExecutor).schedule(testedRunnable, TEST_PERIOD_MS, TimeUnit.MILLISECONDS)
    }

    @Test
    fun `M read data and schedule W run() {data is null}`() {
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
    fun `M log error W run() { rejected by executor }`() {
        // Given
        val exception = RejectedExecutionException()
        whenever(mockExecutor.schedule(eq(testedRunnable), any(), any())) doThrow exception

        // When
        testedRunnable.run()

        // Then
        mockInternalLogger.verifyLog(
            InternalLogger.Level.ERROR,
            targets = listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
            "Unable to schedule Vitals monitoring task on the executor",
            exception
        )
    }

    companion object {
        private const val TEST_PERIOD_MS = 50L
    }
}
