/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.timeseries

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.api.storage.DataWriter
import com.datadog.android.internal.time.TimeProvider
import com.datadog.android.rum.RumSessionType
import com.datadog.android.rum.internal.domain.InfoProvider
import com.datadog.android.rum.internal.domain.battery.BatteryInfo
import com.datadog.android.rum.internal.domain.display.DisplayInfo
import com.datadog.android.rum.internal.instrumentation.insights.InsightsCollector
import com.datadog.android.rum.internal.timeseries.factory.CpuEventFactory
import com.datadog.android.rum.internal.timeseries.factory.MemoryEventFactory
import com.datadog.android.rum.internal.timeseries.provider.CpuDatapointReader
import com.datadog.android.rum.internal.timeseries.provider.VitalReaderWrapper
import com.datadog.android.rum.timeseries.TimeseriesType
import com.datadog.android.rum.utils.forge.Configurator
import com.datadog.android.utils.verifyLog
import com.datadog.tools.unit.getFieldValue
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.LongForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class PipelineFactoryTest {

    @Mock
    lateinit var mockSdkCore: FeatureSdkCore

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @Mock
    lateinit var mockTimeProvider: TimeProvider

    @Mock
    lateinit var mockDataWriter: DataWriter<Any>

    @Mock
    lateinit var mockInsightsCollector: InsightsCollector

    @Mock
    lateinit var mockBatteryInfoProvider: InfoProvider<BatteryInfo>

    @Mock
    lateinit var mockDisplayInfoProvider: InfoProvider<DisplayInfo>

    @Forgery
    lateinit var fakeSessionType: RumSessionType

    @LongForgery(min = 1L)
    var fakeTotalRamBytes: Long = 0L

    @BeforeEach
    fun `set up`() {
        whenever(mockSdkCore.internalLogger) doReturn mockInternalLogger
        whenever(mockSdkCore.timeProvider) doReturn mockTimeProvider
    }

    private fun createTestedFactory(
        totalRamBytes: Long = fakeTotalRamBytes,
        enabledTypes: Set<TimeseriesType> = setOf(TimeseriesType.CPU, TimeseriesType.MEMORY)
    ) = PipelineFactory(
        totalRamBytes = totalRamBytes,
        sdkCore = mockSdkCore,
        dataWriter = mockDataWriter,
        insightsCollector = mockInsightsCollector,
        enabledTypes = enabledTypes,
        batteryInfoProvider = mockBatteryInfoProvider,
        displayInfoProvider = mockDisplayInfoProvider
    )

    private fun Pipeline<*>.reader() = getFieldValue<Any, Pipeline<*>>("reader")
    private fun Pipeline<*>.eventFactory() = getFieldValue<Any, Pipeline<*>>("eventFactory")

    @Test
    fun `M create no pipeline W create() { no type enabled }`() {
        // Given
        val testedFactory = createTestedFactory(enabledTypes = emptySet())

        // When
        val pipelines = testedFactory.create(fakeSessionType)

        // Then
        assertThat(pipelines).isEmpty()
    }

    @Test
    fun `M create a cpu pipeline W create() { CPU enabled }`() {
        // Given
        val testedFactory = createTestedFactory(enabledTypes = setOf(TimeseriesType.CPU))

        // When
        val pipelines = testedFactory.create(fakeSessionType)

        // Then
        assertThat(pipelines).hasSize(1)
        assertThat(pipelines[0].reader()).isInstanceOf(CpuDatapointReader::class.java)
        assertThat(pipelines[0].eventFactory()).isInstanceOf(CpuEventFactory::class.java)
    }

    @Test
    fun `M create a memory pipeline W create() { MEMORY enabled, total ram known }`() {
        // Given
        val testedFactory = createTestedFactory(
            totalRamBytes = fakeTotalRamBytes,
            enabledTypes = setOf(TimeseriesType.MEMORY)
        )

        // When
        val pipelines = testedFactory.create(fakeSessionType)

        // Then
        assertThat(pipelines).hasSize(1)
        assertThat(pipelines[0].reader()).isInstanceOf(VitalReaderWrapper::class.java)
        assertThat(pipelines[0].eventFactory()).isInstanceOf(MemoryEventFactory::class.java)
    }

    @ParameterizedTest
    @ValueSource(longs = [0L, -1L])
    fun `M skip memory pipeline and warn once W create() { MEMORY enabled, total ram unknown }`(
        invalidTotalRamBytes: Long
    ) {
        // Given
        val testedFactory = createTestedFactory(
            totalRamBytes = invalidTotalRamBytes,
            enabledTypes = setOf(TimeseriesType.MEMORY)
        )

        // When
        val pipelines = testedFactory.create(fakeSessionType)

        // Then
        assertThat(pipelines).isEmpty()
        mockInternalLogger.verifyLog(
            InternalLogger.Level.WARN,
            InternalLogger.Target.USER,
            PipelineFactory.MEMORY_TIMESERIES_DISABLED_MESSAGE,
            onlyOnce = true
        )
    }

    @Test
    fun `M create both pipelines W create() { CPU and MEMORY enabled }`() {
        // Given
        val testedFactory = createTestedFactory(
            enabledTypes = setOf(TimeseriesType.CPU, TimeseriesType.MEMORY)
        )

        // When
        val pipelines = testedFactory.create(fakeSessionType)

        // Then
        assertThat(pipelines).hasSize(2)
        assertThat(pipelines.map { it.reader()::class.java })
            .containsExactlyInAnyOrder(CpuDatapointReader::class.java, VitalReaderWrapper::class.java)
    }

    @Test
    fun `M create a new pipeline list W create() { called twice }`() {
        // Given
        val testedFactory = createTestedFactory(enabledTypes = setOf(TimeseriesType.CPU))

        // When
        val firstCall = testedFactory.create(fakeSessionType)
        val secondCall = testedFactory.create(fakeSessionType)

        // Then
        assertThat(firstCall).isNotSameAs(secondCall)
        assertThat(firstCall[0]).isNotSameAs(secondCall[0])
    }
}
