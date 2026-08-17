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
import com.datadog.android.rum.ExperimentalRumApi
import com.datadog.android.rum.RumSessionType
import com.datadog.android.rum.internal.domain.InfoProvider
import com.datadog.android.rum.internal.domain.RumContext
import com.datadog.android.rum.internal.domain.battery.BatteryInfo
import com.datadog.android.rum.internal.domain.display.DisplayInfo
import com.datadog.android.rum.internal.instrumentation.insights.InsightsCollector
import com.datadog.android.rum.internal.timeseries.factory.CpuEventFactory
import com.datadog.android.rum.internal.timeseries.factory.MemoryEventFactory
import com.datadog.android.rum.timeseries.TimeseriesConfiguration
import com.datadog.android.rum.timeseries.TimeseriesType
import com.datadog.android.rum.utils.forge.Configurator
import com.datadog.android.utils.verifyLog
import com.datadog.tools.unit.getFieldValue
import fr.xgouchet.elmyr.Forge
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
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.util.concurrent.ScheduledExecutorService

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
@OptIn(ExperimentalRumApi::class)
internal class DefaultTimeseriesCollectorFactoryTest {

    @Mock
    lateinit var mockSdkCore: FeatureSdkCore

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @Mock
    lateinit var mockTimeProvider: TimeProvider

    @Mock
    lateinit var mockWriter: DataWriter<Any>

    @Mock
    lateinit var mockExecutor: ScheduledExecutorService

    @Mock
    lateinit var mockInsightsCollector: InsightsCollector

    @Mock
    lateinit var mockBatteryInfoProvider: InfoProvider<BatteryInfo>

    @Mock
    lateinit var mockDisplayInfoProvider: InfoProvider<DisplayInfo>

    @Forgery
    lateinit var fakeRumContext: RumContext

    lateinit var fakeSessionType: RumSessionType

    @BeforeEach
    fun `set up`(forge: Forge) {
        whenever(mockSdkCore.internalLogger) doReturn mockInternalLogger
        whenever(mockSdkCore.timeProvider) doReturn mockTimeProvider

        fakeSessionType = forge.aValueFrom(RumSessionType::class.java)
    }

    private fun createFactory(
        totalRamBytes: Long,
        configuration: TimeseriesConfiguration = TimeseriesConfiguration.Builder().build()
    ) = DefaultTimeseriesCollectorFactory(
        sdkCore = mockSdkCore,
        configuration = configuration,
        scheduledExecutorService = mockExecutor,
        insightsCollector = mockInsightsCollector,
        totalRamBytes = totalRamBytes,
        dataWriter = mockWriter,
        batteryInfoProvider = mockBatteryInfoProvider,
        displayInfoProvider = mockDisplayInfoProvider
    )

    @Test
    fun `M build cpu and memory pipelines W create() { totalRamBytes greater than zero }`(
        @LongForgery(min = 1L) fakeTotalRamBytes: Long
    ) {
        // Given
        val testedFactory = createFactory(fakeTotalRamBytes)

        // When
        val timeseries = testedFactory.create(fakeSessionType, fakeRumContext)

        // Then
        check(timeseries is DefaultTimeseriesCollector)
        assertThat(timeseries.pipelines).hasSize(2)
    }

    @Test
    fun `M use default sampling settings W create()`(
        @LongForgery(min = 1L) fakeTotalRamBytes: Long
    ) {
        // Given
        val testedFactory = createFactory(fakeTotalRamBytes)

        // When
        val timeseries = testedFactory.create(fakeSessionType, fakeRumContext)

        // Then
        check(timeseries is DefaultTimeseriesCollector)
        assertThat(timeseries.pipelines.map { it.intervalMs })
            .containsOnly(TimeseriesConfiguration.DEFAULT_INTERVAL_MS)
        assertThat(
            timeseries.pipelines.map { pipeline ->
                pipeline.getFieldValue<Buffer<*>, Pipeline<*>>("buffer")
                    .getFieldValue<Int, Buffer<*>>("size")
            }
        ).containsOnly(TimeseriesConfiguration.DEFAULT_BUFFER_SIZE)
    }

    @Test
    fun `M build only cpu pipeline W create() { totalRamBytes is zero }`() {
        // Given
        val testedFactory = createFactory(0L)

        // When
        val timeseries = testedFactory.create(fakeSessionType, fakeRumContext)

        // Then
        check(timeseries is DefaultTimeseriesCollector)
        assertThat(timeseries.pipelines).hasSize(1)
        mockInternalLogger.verifyLog(
            InternalLogger.Level.WARN,
            InternalLogger.Target.USER,
            DefaultTimeseriesCollectorFactory.MEMORY_TIMESERIES_DISABLED_MESSAGE,
            onlyOnce = true
        )
    }

    @Test
    fun `M build only cpu pipeline W create() { configuration collects only cpu }`() {
        // Given
        val fakeConfiguration = TimeseriesConfiguration.Builder()
            .collectOnly(TimeseriesType.CPU)
            .build()
        val testedFactory = createFactory(0L, fakeConfiguration)

        // When
        val timeseries = testedFactory.create(fakeSessionType, fakeRumContext)

        // Then
        check(timeseries is DefaultTimeseriesCollector)
        assertThat(timeseries.pipelines).hasSize(1)
        val eventFactory = timeseries.pipelines.single()
            .getFieldValue<Any, Pipeline<*>>("eventFactory")
        assertThat(eventFactory).isInstanceOf(CpuEventFactory::class.java)
        verifyNoInteractions(mockInternalLogger)
    }

    @Test
    fun `M build only memory pipeline W create() { configuration collects only memory }`(
        @LongForgery(min = 1L) fakeTotalRamBytes: Long
    ) {
        // Given
        val fakeConfiguration = TimeseriesConfiguration.Builder()
            .collectOnly(TimeseriesType.MEMORY)
            .build()
        val testedFactory = createFactory(fakeTotalRamBytes, fakeConfiguration)

        // When
        val timeseries = testedFactory.create(fakeSessionType, fakeRumContext)

        // Then
        check(timeseries is DefaultTimeseriesCollector)
        assertThat(timeseries.pipelines).hasSize(1)
        val eventFactory = timeseries.pipelines.single()
            .getFieldValue<Any, Pipeline<*>>("eventFactory")
        assertThat(eventFactory).isInstanceOf(MemoryEventFactory::class.java)
    }

    @Test
    fun `M build no pipelines W create() { configuration collects no types }`(
        @LongForgery(min = 1L) fakeTotalRamBytes: Long
    ) {
        // Given
        val fakeConfiguration = TimeseriesConfiguration.Builder()
            .collectOnly(*emptyArray())
            .build()
        val testedFactory = createFactory(fakeTotalRamBytes, fakeConfiguration)

        // When
        val timeseries = testedFactory.create(fakeSessionType, fakeRumContext)

        // Then
        check(timeseries is DefaultTimeseriesCollector)
        assertThat(timeseries.pipelines).isEmpty()
    }

    @Test
    fun `M propagate executor to the collector and rum context to the pipelines W create()`(
        @LongForgery(min = 1L) fakeTotalRamBytes: Long
    ) {
        // Given
        val testedFactory = createFactory(fakeTotalRamBytes)

        // When
        val timeseries = testedFactory.create(fakeSessionType, fakeRumContext)

        // Then
        check(timeseries is DefaultTimeseriesCollector)
        assertThat(timeseries.scheduledExecutorService).isSameAs(mockExecutor)
        assertThat(
            timeseries.pipelines.map { it.getFieldValue<RumContext, Pipeline<*>>("rumContext") }
        ).containsOnly(fakeRumContext)
    }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun `M propagate collectInBackground to the collector W create()`(
        fakeCollectInBackground: Boolean
    ) {
        // Given
        val fakeConfiguration = TimeseriesConfiguration.Builder()
            .collectInBackground(fakeCollectInBackground)
            .build()
        val testedFactory = createFactory(totalRamBytes = 1L, configuration = fakeConfiguration)

        // When
        val timeseries = testedFactory.create(fakeSessionType, fakeRumContext)

        // Then
        check(timeseries is DefaultTimeseriesCollector)
        assertThat(
            timeseries.getFieldValue<Boolean, DefaultTimeseriesCollector>("collectInBackground")
        ).isEqualTo(fakeCollectInBackground)
    }

    @Test
    fun `M propagate session type and info providers to both factories W create()`(
        @LongForgery(min = 1L) fakeTotalRamBytes: Long
    ) {
        // Given
        val testedFactory = createFactory(fakeTotalRamBytes)

        // When
        val timeseries = testedFactory.create(fakeSessionType, fakeRumContext)

        // Then
        check(timeseries is DefaultTimeseriesCollector)
        val cpuEventFactory = timeseries.pipelines[0]
            .getFieldValue<CpuEventFactory, Pipeline<*>>("eventFactory")
        val memoryEventFactory = timeseries.pipelines[1]
            .getFieldValue<MemoryEventFactory, Pipeline<*>>("eventFactory")

        assertThat(
            cpuEventFactory.getFieldValue<RumSessionType, CpuEventFactory>("sessionType")
        ).isEqualTo(fakeSessionType)
        assertThat(
            cpuEventFactory.getFieldValue<InfoProvider<BatteryInfo>, CpuEventFactory>("batteryInfoProvider")
        ).isSameAs(mockBatteryInfoProvider)
        assertThat(
            cpuEventFactory.getFieldValue<InfoProvider<DisplayInfo>, CpuEventFactory>("displayInfoProvider")
        ).isSameAs(mockDisplayInfoProvider)

        assertThat(
            memoryEventFactory.getFieldValue<RumSessionType, MemoryEventFactory>("sessionType")
        ).isEqualTo(fakeSessionType)
        assertThat(
            memoryEventFactory.getFieldValue<Long, MemoryEventFactory>("totalRamBytes")
        ).isEqualTo(fakeTotalRamBytes)
        assertThat(
            memoryEventFactory.getFieldValue<InfoProvider<BatteryInfo>, MemoryEventFactory>("batteryInfoProvider")
        ).isSameAs(mockBatteryInfoProvider)
        assertThat(
            memoryEventFactory.getFieldValue<InfoProvider<DisplayInfo>, MemoryEventFactory>("displayInfoProvider")
        ).isSameAs(mockDisplayInfoProvider)
    }
}
