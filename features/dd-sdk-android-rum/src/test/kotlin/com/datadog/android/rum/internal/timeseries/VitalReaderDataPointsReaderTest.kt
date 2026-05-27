/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.timeseries

import com.datadog.android.internal.time.TimeProvider
import com.datadog.android.rum.internal.timeseries.provider.VitalReaderWrapper
import com.datadog.android.rum.internal.vitals.VitalReader
import com.datadog.android.rum.utils.forge.Configurator
import fr.xgouchet.elmyr.annotation.DoubleForgery
import fr.xgouchet.elmyr.annotation.LongForgery
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
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.util.concurrent.TimeUnit

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class VitalReaderDataPointsReaderTest {

    @LongForgery(min = 1L)
    var fakeTimestamp: Long = 0L

    @Mock
    lateinit var mockTimeProvider: TimeProvider

    @BeforeEach
    fun `set up`() {
        whenever(mockTimeProvider.getDeviceTimestampMillis()) doReturn fakeTimestamp
    }

    @Test
    fun `M return sample W read() { reader has value }`(
        @DoubleForgery(min = 0.001) fakeValue: Double,
        @LongForgery(min = 1L) fakeIntervalMs: Long
    ) {
        // Given
        val mockVitalReader: VitalReader = mock {
            on { readVitalData() } doReturn fakeValue
        }
        val testedReader = VitalReaderWrapper(mockVitalReader, mockTimeProvider, fakeIntervalMs)

        // When
        val result = testedReader.read()

        // Then
        checkNotNull(result)
        assertThat(result.timestampNs).isEqualTo(TimeUnit.MILLISECONDS.toNanos(fakeTimestamp))
        assertThat(result.value).isEqualTo(fakeValue)
    }

    @Test
    fun `M return null W read() { reader has no value }`(@LongForgery(min = 1L) fakeIntervalMs: Long) {
        // Given
        val mockVitalReader: VitalReader = mock {
            on { readVitalData() } doReturn null
        }
        val testedReader = VitalReaderWrapper(mockVitalReader, mockTimeProvider, fakeIntervalMs)

        // When
        val result = testedReader.read()

        // Then
        assertThat(result).isNull()
    }

    @Test
    fun `M expose intervalMs W intervalMs { construction value }`(@LongForgery(min = 1L) fakeIntervalMs: Long) {
        // Given
        val mockVitalReader: VitalReader = mock()
        val testedReader = VitalReaderWrapper(mockVitalReader, mockTimeProvider, fakeIntervalMs)

        // When / Then
        assertThat(testedReader.intervalMs).isEqualTo(fakeIntervalMs)
    }
}
