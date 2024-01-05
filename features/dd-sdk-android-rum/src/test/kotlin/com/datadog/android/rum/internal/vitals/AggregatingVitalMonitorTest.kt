/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.vitals

import com.datadog.android.rum.utils.forge.Configurator
import fr.xgouchet.elmyr.annotation.DoubleForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.withinPercentage
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class AggregatingVitalMonitorTest {

    lateinit var testedMonitor: AggregatingVitalMonitor

    @Mock
    lateinit var mockListener: VitalListener

    @BeforeEach
    fun `set up`() {
        testedMonitor = AggregatingVitalMonitor()
    }

    @Test
    fun `𝕄 return null by default 𝕎 getLastSample()`() {
        // Given

        // When

        // Then
        assertThat(testedMonitor.getLastSample())
            .isNaN()
    }

    @Test
    fun `𝕄 update last known value 𝕎 onNewSample()`(
        @DoubleForgery(-REASONABLE_DOUBLE, REASONABLE_DOUBLE) value: Double
    ) {
        // Given

        // When
        testedMonitor.onNewSample(value)

        // Then
        assertThat(testedMonitor.getLastSample())
            .isEqualTo(value)
    }

    @Test
    fun `𝕄 not notify listener 𝕎 register() {no previous sample}`() {
        // When
        testedMonitor.register(mockListener)

        // Then
        verify(mockListener, never()).onVitalUpdate(any())
    }

    @Test
    fun `𝕄 notify listener 𝕎 register()`(
        @DoubleForgery(-REASONABLE_DOUBLE, REASONABLE_DOUBLE) value: Double
    ) {
        // Given
        testedMonitor.onNewSample(value)

        // When
        testedMonitor.register(mockListener)

        // Then
        argumentCaptor<VitalInfo> {
            verify(mockListener).onVitalUpdate(capture())
            assertThat(firstValue.sampleCount).isEqualTo(1)
            assertThat(firstValue.minValue).isEqualTo(value)
            assertThat(firstValue.maxValue).isEqualTo(value)
            assertThat(firstValue.meanValue).isEqualTo(value)
        }
    }

    @Test
    fun `𝕄 notify listener 𝕎 onNewSample() {multiple values, single thread}`(
        @DoubleForgery(-REASONABLE_DOUBLE, REASONABLE_DOUBLE) values: List<Double>
    ) {
        // Given
        testedMonitor.register(mockListener)

        // When
        for (value in values) {
            testedMonitor.onNewSample(value)
        }

        // Then
        argumentCaptor<VitalInfo> {
            verify(mockListener, times(values.size)).onVitalUpdate(capture())

            allValues.forEachIndexed { index, vitalInfo ->
                assertThat(vitalInfo.sampleCount).isEqualTo(index + 1)
            }

            assertThat(firstValue.minValue).isEqualTo(values[0])
            assertThat(firstValue.maxValue).isEqualTo(values[0])
            assertThat(firstValue.meanValue).isEqualTo(values[0])

            assertThat(lastValue.minValue).isEqualTo(values.minOrNull())
            assertThat(lastValue.maxValue).isEqualTo(values.maxOrNull())
            assertThat(lastValue.meanValue).isCloseTo(values.average(), withinPercentage(1))
        }
    }

    @Test
    fun `𝕄 notify listener 𝕎 onNewSample() {multiple values, multiple threads}`(
        @DoubleForgery(-REASONABLE_DOUBLE, REASONABLE_DOUBLE) values: List<Double>
    ) {
        // Given
        val countDownLatch = CountDownLatch(values.size)
        testedMonitor.register(mockListener)

        // When
        values.forEach { value ->
            Thread {
                testedMonitor.onNewSample(value)
                countDownLatch.countDown()
            }.start()
        }
        countDownLatch.await(1, TimeUnit.SECONDS)

        // Then
        argumentCaptor<VitalInfo> {
            verify(mockListener, times(values.size)).onVitalUpdate(capture())

            allValues.forEachIndexed { index, vitalInfo ->
                assertThat(vitalInfo.sampleCount).isEqualTo(index + 1)
            }

            assertThat(lastValue.minValue).isEqualTo(values.minOrNull())
            assertThat(lastValue.maxValue).isEqualTo(values.maxOrNull())
            assertThat(lastValue.meanValue).isCloseTo(values.average(), withinPercentage(1))
        }
    }

    @Test
    fun `𝕄 notify multiple listeners 𝕎 onNewSample() multiple values`(
        @DoubleForgery(-REASONABLE_DOUBLE, REASONABLE_DOUBLE) value1: Double,
        @DoubleForgery(-REASONABLE_DOUBLE, REASONABLE_DOUBLE) value2: Double,
        @DoubleForgery(-REASONABLE_DOUBLE, REASONABLE_DOUBLE) value3: Double
    ) {
        // Given
        val mock1: VitalListener = mock()
        val mock2: VitalListener = mock()
        val mock3: VitalListener = mock()
        val mock4: VitalListener = mock()

        // When
        testedMonitor.register(mock1)
        testedMonitor.onNewSample(value1)
        testedMonitor.register(mock2)
        testedMonitor.onNewSample(value2)
        testedMonitor.register(mock3)
        testedMonitor.onNewSample(value3)
        testedMonitor.register(mock4)

        // Then
        argumentCaptor<VitalInfo> {
            verify(mock1, times(3)).onVitalUpdate(capture())
            allValues.forEachIndexed { index, vitalInfo ->
                assertThat(vitalInfo.sampleCount).isEqualTo(index + 1)
            }
            assertThat(firstValue.minValue).isEqualTo(value1)
            assertThat(firstValue.maxValue).isEqualTo(value1)
            assertThat(firstValue.meanValue).isEqualTo(value1)
        }
        argumentCaptor<VitalInfo> {
            verify(mock2, times(3)).onVitalUpdate(capture())
            allValues.forEachIndexed { index, vitalInfo ->
                assertThat(vitalInfo.sampleCount).isEqualTo(index + 1)
            }
            assertThat(firstValue.minValue).isEqualTo(value1)
            assertThat(firstValue.maxValue).isEqualTo(value1)
            assertThat(firstValue.meanValue).isEqualTo(value1)
        }
        argumentCaptor<VitalInfo> {
            verify(mock3, times(2)).onVitalUpdate(capture())
            allValues.forEachIndexed { index, vitalInfo ->
                assertThat(vitalInfo.sampleCount).isEqualTo(index + 1)
            }
            assertThat(firstValue.minValue).isEqualTo(value2)
            assertThat(firstValue.maxValue).isEqualTo(value2)
            assertThat(firstValue.meanValue).isEqualTo(value2)
        }
        argumentCaptor<VitalInfo> {
            verify(mock4, times(1)).onVitalUpdate(capture())
            allValues.forEachIndexed { index, vitalInfo ->
                assertThat(vitalInfo.sampleCount).isEqualTo(index + 1)
            }
            assertThat(firstValue.minValue).isEqualTo(value3)
            assertThat(firstValue.maxValue).isEqualTo(value3)
            assertThat(firstValue.meanValue).isEqualTo(value3)
        }
    }

    @Test
    fun `𝕄 stop notifying listener 𝕎 unregister()`(
        @DoubleForgery(-REASONABLE_DOUBLE, REASONABLE_DOUBLE) trackedValue: Double,
        @DoubleForgery(-REASONABLE_DOUBLE, REASONABLE_DOUBLE) untrackedValue: Double
    ) {
        // Given
        testedMonitor.register(mockListener)

        // When
        testedMonitor.onNewSample(trackedValue)
        testedMonitor.unregister(mockListener)
        testedMonitor.onNewSample(untrackedValue)

        // Then
        argumentCaptor<VitalInfo> {
            verify(mockListener).onVitalUpdate(capture())
            assertThat(firstValue.sampleCount).isEqualTo(+1)
            assertThat(firstValue.minValue).isEqualTo(trackedValue)
            assertThat(firstValue.maxValue).isEqualTo(trackedValue)
            assertThat(firstValue.meanValue).isEqualTo(trackedValue)
        }
    }

    companion object {
        // avoid unrealistic values that will make mean computation reach infinity
        const val REASONABLE_DOUBLE = Float.MAX_VALUE.toDouble()
    }
}
