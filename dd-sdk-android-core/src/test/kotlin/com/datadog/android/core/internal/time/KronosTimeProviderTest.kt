/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.time

import com.datadog.android.utils.forge.Configurator
import com.lyft.kronos.Clock
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.data.Offset
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.util.Date
import java.util.concurrent.TimeUnit

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class KronosTimeProviderTest {

    lateinit var testedTimeProvider: KronosTimeProvider

    @Mock
    lateinit var mockClock: Clock

    @Forgery
    lateinit var fakeDate: Date

    @BeforeEach
    fun `set up`() {
        whenever(mockClock.getCurrentTimeMs()) doReturn fakeDate.time
        testedTimeProvider = KronosTimeProvider(mockClock)
    }

    @Test
    fun `returns clock's time as server time`() {
        val result = testedTimeProvider.getServerTimestamp()

        assertThat(result).isEqualTo(fakeDate.time)
    }

    @Test
    fun `returns server time offset in nanoseconds`() {
        val now = System.currentTimeMillis()
        val result = testedTimeProvider.getServerOffsetNanos()

        val expectedOffset = TimeUnit.MILLISECONDS.toNanos(fakeDate.time - now)
        assertThat(result).isCloseTo(
            expectedOffset,
            Offset.offset(TimeUnit.MILLISECONDS.toNanos(TEST_OFFSET))
        )
    }

    @Test
    fun `returns server time offset in milliseconds`() {
        val now = System.currentTimeMillis()
        val result = testedTimeProvider.getServerOffsetMillis()

        val expectedOffset = fakeDate.time - now
        assertThat(result).isCloseTo(
            expectedOffset,
            Offset.offset(TEST_OFFSET)
        )
    }

    @Test
    fun `returns device time`() {
        val now = System.currentTimeMillis()
        val result = testedTimeProvider.getDeviceTimestamp()

        assertThat(result).isCloseTo(now, Offset.offset(TEST_OFFSET))
    }

    // region Reproduction tests for RUMS-5093: Incorrect Timing and Ordering of Traces

    @Test
    fun `REPRO RUMS-5093 - negative offset is returned when device clock is ahead of server`() {
        // Given
        // Simulate device clock being 5 seconds AHEAD of server: Kronos returns server time
        // that is 5 seconds BEHIND the current device time.
        // This is the root cause of RUMS-5093: when the device clock is ahead, serverOffset
        // is a large negative value. When applied at serialization time in
        // CoreTracerSpanToSpanEventMapper.map(), it shifts the span start to BEFORE the actual request.
        val deviceClockAheadMs = 5_000L // device is 5 seconds ahead
        val fakeServerTimeMs = System.currentTimeMillis() - deviceClockAheadMs
        whenever(mockClock.getCurrentTimeMs()) doReturn fakeServerTimeMs

        // When
        val offsetMs = testedTimeProvider.getServerOffsetMillis()

        // Then: offset is negative (server is behind device).
        // This negative offset, when applied at serialization time to a span's startTime,
        // shifts the absolute start timestamp EARLIER than the actual span start.
        // FAILS: we assert that the provider guards against negative offsets being applied
        // to serialized span timestamps (e.g., by clamping to 0 or flagging for caller).
        // Currently, getServerOffsetMillis() returns -5000L with NO guard or warning.
        assertThat(offsetMs)
            .describedAs(
                "RUMS-5093 root cause: device is 5s ahead, so serverOffset is ~-5000ms. " +
                    "Confirmed: offset=%d ms",
                offsetMs
            )
            .isLessThan(0L)

        // The SDK should protect callers from applying large negative offsets to span timestamps.
        // FAILS: there is no guard — getServerOffsetMillis() returns the raw negative delta.
        assertThat(offsetMs)
            .describedAs(
                "RUMS-5093: getServerOffsetMillis() must not return a value that would shift " +
                    "a span start timestamp earlier than the actual request time. " +
                    "A large negative offset of %d ms indicates device clock is ahead of NTP server. " +
                    "The SDK has no protection: span serialization blindly applies this offset, " +
                    "producing incorrect absolute start timestamps.",
                offsetMs
            )
            .isGreaterThanOrEqualTo(0L)
    }

    // endregion

    companion object {
        const val TEST_OFFSET = 10L
    }
}
