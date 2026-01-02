/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.time

import com.datadog.android.api.InternalLogger
import com.datadog.android.core.internal.time.KronosTimeProvider.Companion.FAIL_MESSAGE
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.utils.verifyLog
import com.datadog.tools.unit.forge.anException
import com.lyft.kronos.Clock
import fr.xgouchet.elmyr.Forge
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
import org.mockito.kotlin.doThrow
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

    @Mock
    lateinit var internalLogger: InternalLogger

    @BeforeEach
    fun `set up`() {
        whenever(mockClock.getCurrentTimeMs()) doReturn fakeDate.time
        testedTimeProvider = KronosTimeProvider(mockClock, internalLogger)
    }

    @Test
    fun `returns clock's time as server time`() {
        val result = testedTimeProvider.getServerTimestampMillis()

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
        val result = testedTimeProvider.getDeviceTimestampMillis()

        assertThat(result).isCloseTo(now, Offset.offset(TEST_OFFSET))
    }

    @Test
    fun `M log and return 0 W getServerOffsetMillis { getCurrentTimeMs throws }`(forge: Forge) {
        // Given
        val exception = forge.anException()
        whenever(mockClock.getCurrentTimeMs()) doThrow exception

        // When
        val result = testedTimeProvider.getServerOffsetMillis()

        // Then
        internalLogger.verifyLog(
            level = InternalLogger.Level.WARN,
            targets = listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
            message = FAIL_MESSAGE,
            throwable = exception,
            onlyOnce = true,
            additionalProperties = emptyMap()
        )
        assertThat(result).isZero()
    }

    @Test
    fun `M log and return System currentTimeMillis W getServerTimestampMillis { getCurrentTimeMs throws }`(
        forge: Forge
    ) {
        // Given
        val exception = forge.anException()
        whenever(mockClock.getCurrentTimeMs()) doThrow exception

        // When
        val now = System.currentTimeMillis()
        val result = testedTimeProvider.getServerTimestampMillis()

        // Then
        internalLogger.verifyLog(
            level = InternalLogger.Level.WARN,
            targets = listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
            message = FAIL_MESSAGE,
            throwable = exception,
            onlyOnce = true,
            additionalProperties = emptyMap()
        )
        assertThat(result).isCloseTo(now, Offset.offset(TEST_OFFSET))
    }

    companion object {
        const val TEST_OFFSET = 10L
    }
}
