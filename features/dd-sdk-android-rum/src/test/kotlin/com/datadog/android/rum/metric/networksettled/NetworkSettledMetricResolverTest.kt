/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.metric.networksettled

import com.datadog.android.api.InternalLogger
import com.datadog.android.rum.internal.metric.NoValueReason
import com.datadog.android.rum.internal.metric.ViewInitializationMetricsConfig
import com.datadog.android.rum.internal.metric.networksettled.InternalResourceContext
import com.datadog.android.rum.internal.metric.networksettled.NetworkSettledMetricResolver
import com.datadog.android.rum.utils.forge.Configurator
import com.datadog.android.rum.utils.verifyLog
import fr.xgouchet.elmyr.Forge
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
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.util.UUID

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class NetworkSettledMetricResolverTest {

    private lateinit var testedMetric: NetworkSettledMetricResolver

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @Mock
    lateinit var mockInitialResourceIdentifier: InitialResourceIdentifier

    private var fakeViewStartTime: Long = System.nanoTime()

    // region SetUp

    @BeforeEach
    fun `set up`() {
        fakeViewStartTime = System.nanoTime()
        testedMetric = NetworkSettledMetricResolver(mockInitialResourceIdentifier, mockInternalLogger)
        testedMetric.viewWasCreated(fakeViewStartTime)
        whenever(mockInitialResourceIdentifier.validate(any())).thenReturn(true)
    }

    // endregion

    // region metric computation

    @Test
    fun `M return the correct metric W resolveMetric(){ resources stopped in random order }`(forge: Forge) {
        // Given
        val startTimestamps = forge.forgeStartTimestamps()
        val stopTimestamps = startTimestamps.mapToStopTimestamps(forge)
        val settledIntervals = stopTimestamps.mapToSettledIntervals()
        val expectedMetricValue = settledIntervals.max()
        val fakeResourcesIds = forge.aList(size = startTimestamps.size) {
            forge.getForgery<UUID>().toString()
        }
        fakeResourcesIds.forEachIndexed { index, id ->
            testedMetric.resourceWasStarted(
                InternalResourceContext(id, startTimestamps[index])
            )
            testedMetric.resourceWasStopped(
                InternalResourceContext(id, stopTimestamps[index])
            )
        }

        // When
        val metricValue = testedMetric.resolveMetric()

        // Then
        assertThat(metricValue).isEqualTo(expectedMetricValue)
    }

    @Test
    fun `M return the correct metric W resolveMetric(){ only one resource registered }`(forge: Forge) {
        // Given
        val startTimestamp = System.nanoTime()
        val stopTimestamp = startTimestamp + forge.aLong(min = 1, max = 1000)
        val fakeResourceId = forge.getForgery<UUID>().toString()
        testedMetric.resourceWasStarted(InternalResourceContext(fakeResourceId, startTimestamp))
        testedMetric.resourceWasStopped(InternalResourceContext(fakeResourceId, stopTimestamp))

        // When
        val metricValue = testedMetric.resolveMetric()

        // Then
        assertThat(metricValue).isEqualTo(stopTimestamp - fakeViewStartTime)
    }

    @Test
    fun `M return null W resolveMetric(){ no resources registered }`() {
        // When
        val metricValue = testedMetric.resolveMetric()

        // Then
        assertThat(metricValue).isNull()
    }

    @Test
    fun `M return valid config value W getState()`() {
        // Given
        val custom = NetworkSettledMetricResolver(mockInitialResourceIdentifier, mockInternalLogger)
        val timeBasedDefault = NetworkSettledMetricResolver(internalLogger = mockInternalLogger)
        val timeBasedCustom = NetworkSettledMetricResolver(
            TimeBasedInitialResourceIdentifier(TimeBasedInitialResourceIdentifier.DEFAULT_TIME_THRESHOLD_MS + 1),
            mockInternalLogger
        )

        // Then
        assertThat(custom.getState().config).isEqualTo(ViewInitializationMetricsConfig.CUSTOM)
        assertThat(timeBasedCustom.getState().config).isEqualTo(ViewInitializationMetricsConfig.TIME_BASED_CUSTOM)
        assertThat(timeBasedDefault.getState().config).isEqualTo(ViewInitializationMetricsConfig.TIME_BASED_DEFAULT)
    }

    @Test
    fun `M return NO_RESOURCES W getState(){ no resources registered }`() {
        // When
        val state = testedMetric.getState()

        // Then
        assertThat(state.initializationTime).isNull()
        assertThat(state.noValueReason).isEqualTo(NoValueReason.TimeToNetworkSettle.NO_RESOURCES)
    }

    @Test
    fun `M return NOT_SETTLED_YET W getState(){ not all resources settled }`(forge: Forge) {
        // Given
        val startTimestamps = forge.forgeStartTimestamps(size = forge.anInt(min = 4, max = 10))
        val stopTimestamps = startTimestamps.mapToStopTimestamps(forge)
        val fakeResourcesIds = forge.aList(size = startTimestamps.size) {
            forge.getForgery<UUID>().toString()
        }
        fakeResourcesIds.forEachIndexed { index, id ->
            testedMetric.resourceWasStarted(
                InternalResourceContext(id, startTimestamps[index])
            )
        }
        fakeResourcesIds.take(forge.anInt(min = 1, max = fakeResourcesIds.size / 2))
            .forEachIndexed { index, fakeResourceContext ->
                testedMetric.resourceWasStopped(
                    InternalResourceContext(fakeResourceContext, stopTimestamps[index])
                )
            }

        // When
        val state = testedMetric.getState()

        // Then
        assertThat(state.initializationTime).isNull()
        assertThat(state.noValueReason).isEqualTo(NoValueReason.TimeToNetworkSettle.NOT_SETTLED_YET)
    }

    @Test
    fun `M return NO_INITIAL_RESOURCES W getState(){ no resource was validated }`(forge: Forge) {
        // Given
        val startTimestamps = forge.forgeStartTimestamps()
        whenever(mockInitialResourceIdentifier.validate(any())).thenReturn(false)
        val stopTimestamps = startTimestamps.mapToStopTimestamps(forge)
        val fakeResourcesIds = forge.aList(size = startTimestamps.size) {
            forge.getForgery<UUID>().toString()
        }
        fakeResourcesIds.forEachIndexed { index, id ->
            testedMetric.resourceWasStarted(
                InternalResourceContext(id, startTimestamps[index])
            )
            testedMetric.resourceWasStopped(
                InternalResourceContext(id, stopTimestamps[index])
            )
        }
        // When
        val state = testedMetric.getState()

        // Then
        assertThat(state.initializationTime).isNull()
        assertThat(state.noValueReason).isEqualTo(NoValueReason.TimeToNetworkSettle.NO_INITIAL_RESOURCES)
    }

    @Test
    fun `M return null W resolveMetric(){ view not created }`(forge: Forge) {
        // Given
        testedMetric = NetworkSettledMetricResolver(mockInitialResourceIdentifier, mockInternalLogger)
        val startTimestamp = System.nanoTime()
        val stopTimestamp = startTimestamp + forge.aLong(min = 1, max = 1000)
        val fakeResourceId = forge.getForgery<UUID>().toString()
        testedMetric.resourceWasStarted(InternalResourceContext(fakeResourceId, startTimestamp))
        testedMetric.resourceWasStopped(InternalResourceContext(fakeResourceId, stopTimestamp))

        // When
        val metricValue = testedMetric.resolveMetric()

        // Then
        assertThat(metricValue).isNull()
        mockInternalLogger.verifyLog(
            InternalLogger.Level.DEBUG,
            InternalLogger.Target.MAINTAINER,
            "[ViewNetworkSettledMetric] There was no view created yet for this resource"
        )
    }

    @Test
    fun `M return the correct state W getState(){ resources stopped in random order }`(forge: Forge) {
        // Given
        val startTimestamps = forge.forgeStartTimestamps()
        val stopTimestamps = startTimestamps.mapToStopTimestamps(forge)
        val settledIntervals = stopTimestamps.mapToSettledIntervals()
        val expectedMetricValue = settledIntervals.max()
        val fakeResourcesIds = forge.aList(size = startTimestamps.size) {
            forge.getForgery<UUID>().toString()
        }
        fakeResourcesIds.forEachIndexed { index, id ->
            testedMetric.resourceWasStarted(
                InternalResourceContext(id, startTimestamps[index])
            )
            testedMetric.resourceWasStopped(
                InternalResourceContext(id, stopTimestamps[index])
            )
        }

        // When
        val state = testedMetric.getState()

        // Then
        assertThat(state.initializationTime).isEqualTo(expectedMetricValue)
        assertThat(state.noValueReason).isNull()
    }

    @Test
    fun `M pass the viewCreatedTimestamp to validator W resourceWasStarted()`(forge: Forge) {
        // Given
        testedMetric = NetworkSettledMetricResolver(mockInitialResourceIdentifier, mockInternalLogger)
        val startTimestamp = System.nanoTime()
        val expectedViewCreatedTimestamp: Long?
        if (forge.aBool()) {
            testedMetric.viewWasCreated(fakeViewStartTime)
            expectedViewCreatedTimestamp = fakeViewStartTime
        } else {
            expectedViewCreatedTimestamp = null
        }
        val fakeResourceId = forge.getForgery<UUID>().toString()

        // When
        testedMetric.resourceWasStarted(InternalResourceContext(fakeResourceId, startTimestamp))

        // Then
        verify(mockInitialResourceIdentifier).validate(
            NetworkSettledResourceContext(
                fakeResourceId,
                startTimestamp,
                expectedViewCreatedTimestamp
            )
        )
    }

    @Test
    fun `M return null W resolveMetric(){ not all resources settled }`(forge: Forge) {
        // Given
        val startTimestamps = forge.forgeStartTimestamps(size = forge.anInt(min = 4, max = 10))
        val stopTimestamps = startTimestamps.mapToStopTimestamps(forge)
        val fakeResourcesIds = forge.aList(size = startTimestamps.size) {
            forge.getForgery<UUID>().toString()
        }
        fakeResourcesIds.forEachIndexed { index, id ->
            testedMetric.resourceWasStarted(
                InternalResourceContext(id, startTimestamps[index])
            )
        }
        fakeResourcesIds.take(forge.anInt(min = 1, max = fakeResourcesIds.size / 2))
            .forEachIndexed { index, fakeResourceContext ->
                testedMetric.resourceWasStopped(
                    InternalResourceContext(fakeResourceContext, stopTimestamps[index])
                )
            }

        // When
        val metricValue = testedMetric.resolveMetric()

        // Then
        assertThat(metricValue).isNull()
        mockInternalLogger.verifyLog(
            InternalLogger.Level.DEBUG,
            InternalLogger.Target.MAINTAINER,
            "[ViewNetworkSettledMetric] Not all the initial resources were stopped for this view"
        )
    }

    @Test
    fun `M return null W resolveMetric(){ view was stopped, not all resources settled }`(forge: Forge) {
        // Given
        val startTimestamps = forge.forgeStartTimestamps(size = forge.anInt(min = 4, max = 10))
        val stopTimestamps = startTimestamps.mapToStopTimestamps(forge)
        val fakeResourcesIds = forge.aList(size = startTimestamps.size) {
            forge.getForgery<UUID>().toString()
        }
        fakeResourcesIds.forEachIndexed { index, id ->
            testedMetric.resourceWasStarted(
                InternalResourceContext(id, startTimestamps[index])
            )
        }
        val stoppedResourcesBeforeViewStopped =
            fakeResourcesIds.take(forge.anInt(min = 1, max = fakeResourcesIds.size / 2))
        val stoppedResourcesAfterViewStopped =
            fakeResourcesIds.takeLast(fakeResourcesIds.size / 2)
        stoppedResourcesBeforeViewStopped.forEachIndexed { index, fakeResourceContext ->
            testedMetric.resourceWasStopped(InternalResourceContext(fakeResourceContext, stopTimestamps[index]))
        }
        testedMetric.viewWasStopped()
        stoppedResourcesAfterViewStopped.forEachIndexed { index, fakeResourceContext ->
            testedMetric.resourceWasStopped(InternalResourceContext(fakeResourceContext, stopTimestamps[index]))
        }

        // When
        val metricValue = testedMetric.resolveMetric()

        // Then
        assertThat(metricValue).isNull()
    }

    @Test
    fun `M return last value W resolveMetric(){ view was stopped, all resources settled }`(forge: Forge) {
        // Given
        val startTimestamps = forge.forgeStartTimestamps()
        val stopTimestamps = startTimestamps.mapToStopTimestamps(forge)
        val settledIntervals = stopTimestamps.mapToSettledIntervals()
        val expectedMetricValue = settledIntervals.max()
        val fakeResourcesIds = forge.aList(size = startTimestamps.size) {
            forge.getForgery<UUID>().toString()
        }
        fakeResourcesIds.forEachIndexed { index, id ->
            testedMetric.resourceWasStarted(
                InternalResourceContext(id, startTimestamps[index])
            )
            testedMetric.resourceWasStopped(
                InternalResourceContext(id, stopTimestamps[index])
            )
        }
        testedMetric.resolveMetric()

        // When
        testedMetric.viewWasStopped()
        val afterViewStoppedMetricValue = testedMetric.resolveMetric()

        // Then
        assertThat(afterViewStoppedMetricValue).isEqualTo(expectedMetricValue)
    }

    @Test
    fun `M skip last value W resolveMetric(){ view was stopped, last resource was dropped }`(forge: Forge) {
        // Given
        val startTimestamps = forge.forgeStartTimestamps(size = 2)
        val stopTimestamps = startTimestamps.mapToStopTimestamps(forge)
        val fakeResourcesIds = forge.aList(size = 2) {
            forge.getForgery<UUID>().toString()
        }
        val expectedMetricValue = stopTimestamps[0] - fakeViewStartTime
        testedMetric.resourceWasStarted(
            InternalResourceContext(fakeResourcesIds[0], startTimestamps[0])
        )
        testedMetric.resourceWasStarted(
            InternalResourceContext(fakeResourcesIds[1], stopTimestamps[1])
        )
        testedMetric.resourceWasStopped(
            InternalResourceContext(fakeResourcesIds[0], stopTimestamps[0])
        )
        testedMetric.resourceWasDropped(fakeResourcesIds[1])

        // When
        val metricValue = testedMetric.resolveMetric()

        // Then
        assertThat(metricValue).isEqualTo(expectedMetricValue)
    }

    @Test
    fun `M return null W resolveMetric(){ no resource was validated }`(forge: Forge) {
        // Given
        val startTimestamps = forge.forgeStartTimestamps()
        whenever(mockInitialResourceIdentifier.validate(any())).thenReturn(false)
        val stopTimestamps = startTimestamps.mapToStopTimestamps(forge)
        val fakeResourcesIds = forge.aList(size = startTimestamps.size) {
            forge.getForgery<UUID>().toString()
        }
        fakeResourcesIds.forEachIndexed { index, id ->
            testedMetric.resourceWasStarted(
                InternalResourceContext(id, startTimestamps[index])
            )
            testedMetric.resourceWasStopped(
                InternalResourceContext(id, stopTimestamps[index])
            )
        }
        // When
        val metricValue = testedMetric.resolveMetric()

        // Then
        assertThat(metricValue).isNull()
    }

    @Test
    fun `M return null W resolveMetric(){ resource was stopped with a different id }`(forge: Forge) {
        // Given
        val startTimestamp = System.nanoTime()
        val stopTimestamp = startTimestamp + forge.aLong(min = 1, max = 1000)
        val fakeResourceContext = forge.getForgery<InternalResourceContext>()
        val fakeDifferentResourceContext = forge.getForgery<InternalResourceContext>()
        testedMetric.resourceWasStarted(InternalResourceContext(fakeResourceContext.resourceId, startTimestamp))
        testedMetric.resourceWasStopped(
            InternalResourceContext(
                fakeDifferentResourceContext.resourceId,
                stopTimestamp
            )
        )

        // When
        val metricValue = testedMetric.resolveMetric()

        // Then
        assertThat(metricValue).isNull()
    }

    @Test
    fun `M return null W resolveMetric(){ resource was dropped }`(forge: Forge) {
        // Given
        val startTimestamp = System.nanoTime()
        val fakeResourceContext = forge.getForgery<InternalResourceContext>()
        testedMetric.resourceWasStarted(InternalResourceContext(fakeResourceContext.resourceId, startTimestamp))
        testedMetric.resourceWasDropped(fakeResourceContext.resourceId)

        // When
        val metricValue = testedMetric.resolveMetric()

        // Then
        assertThat(metricValue).isNull()
    }

    // endregion

    // region view stopped

    @Test
    fun `M cleanup W viewWasStopped`(forge: Forge) {
        // Given
        val startTimestamps = forge.forgeStartTimestamps()
        val fakeResourcesIds = forge.aList(size = startTimestamps.size) {
            forge.getForgery<UUID>().toString()
        }
        fakeResourcesIds.forEachIndexed { index, id ->
            testedMetric.resourceWasStarted(
                InternalResourceContext(id, startTimestamps[index])
            )
        }
        assertThat(testedMetric.getResourceStartedCacheSize()).isEqualTo(fakeResourcesIds.size)

        // When
        testedMetric.viewWasStopped()

        // Then
        assertThat(testedMetric.getResourceStartedCacheSize()).isEqualTo(0)
    }

    // endregion

    // region thread visibility

    @Test
    fun `M return the correct metric W resolveMetric(){ resource started in one Thread and continued in another }`(
        forge: Forge
    ) {
        // Given
        val startTimestamp = System.nanoTime()
        val stopTimestamp = startTimestamp + forge.aLong(min = 1, max = 1000)
        val fakeResourceContext = forge.getForgery<InternalResourceContext>()
        val thread1 = Thread {
            testedMetric.resourceWasStarted(
                InternalResourceContext(
                    fakeResourceContext.resourceId,
                    startTimestamp
                )
            )
        }
        thread1.start()
        thread1.join()
        val thread2 = Thread {
            testedMetric.resourceWasStopped(
                InternalResourceContext(
                    fakeResourceContext.resourceId,
                    stopTimestamp
                )
            )
        }
        thread2.start()
        thread2.join()

        // When
        val metricValue = testedMetric.resolveMetric()

        // Then
        assertThat(metricValue).isEqualTo(stopTimestamp - fakeViewStartTime)
    }

    @Test
    fun `M return the correct metric W resolveMetric(){ view stopped in one thread and metric requested in another }`(
        forge: Forge
    ) {
        // Given
        val startTimestamp = System.nanoTime()
        val stopTimestamp = startTimestamp + forge.aLong(min = 1, max = 1000)
        val fakeResourceContext = forge.getForgery<InternalResourceContext>()
        val thread1 = Thread {
            testedMetric.resourceWasStarted(
                InternalResourceContext(
                    fakeResourceContext.resourceId,
                    startTimestamp
                )
            )
        }
        thread1.start()
        thread1.join()
        val thread2 = Thread {
            testedMetric.viewWasStopped()
        }
        thread2.start()
        thread2.join()
        testedMetric.resourceWasStopped(
            InternalResourceContext(
                fakeResourceContext.resourceId,
                stopTimestamp
            )
        )

        // When
        val metricValue = testedMetric.resolveMetric()

        // Then
        assertThat(metricValue).isNull()
    }

    @Test
    fun `M return the correct metric W resolveMetric(){ metric computed in one thread, stopped in another}`(
        forge: Forge
    ) {
        // Given
        val startTimestamp = System.nanoTime()
        val stopTimestamp = startTimestamp + forge.aLong(min = 1, max = 1000)
        val fakeResourceContext = forge.getForgery<InternalResourceContext>()
        val thread1 = Thread {
            testedMetric.resourceWasStarted(
                InternalResourceContext(
                    fakeResourceContext.resourceId,
                    startTimestamp
                )
            )
            testedMetric.resourceWasStopped(
                InternalResourceContext(
                    fakeResourceContext.resourceId,
                    stopTimestamp
                )
            )
            testedMetric.resolveMetric()
        }
        thread1.start()
        thread1.join()
        val thread2 = Thread {
            testedMetric.viewWasStopped()
        }
        thread2.start()
        thread2.join()

        // When
        val metricValue = testedMetric.resolveMetric()

        // Then
        assertThat(metricValue).isEqualTo(stopTimestamp - fakeViewStartTime)
    }

    // endregion

    // region Internal

    private fun Forge.forgeStartTimestamps(size: Int = anInt(min = 1, max = 10)) = aList(size = size) {
        System.nanoTime() + aLong(min = 1, max = 1000)
    }

    private fun List<Long>.mapToSettledIntervals(): List<Long> {
        return map { it - fakeViewStartTime }
    }

    private fun List<Long>.mapToStopTimestamps(forge: Forge): List<Long> {
        // just to avoid overloads we are using small offsets here
        return map { it + forge.aLong(min = 1, max = 1000) }
    }

    // endregion
}
