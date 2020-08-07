/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.time

import com.datadog.android.utils.forge.Configurator
import com.lyft.kronos.Clock
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.whenever
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import java.util.Date
import java.util.concurrent.TimeUnit
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.data.Offset
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
    fun `returns server time offset`() {
        val now = System.currentTimeMillis()
        val result = testedTimeProvider.getServerOffsetNanos()

        val expectedOffset = TimeUnit.MILLISECONDS.toNanos(fakeDate.time - now)
        assertThat(result).isCloseTo(
            expectedOffset,
            Offset.offset(TimeUnit.MILLISECONDS.toNanos(1))
        )
    }

    @Test
    fun `returns device time`() {
        val now = System.currentTimeMillis()
        val result = testedTimeProvider.getDeviceTimestamp()

        assertThat(result).isCloseTo(now, Offset.offset(50L))
    }
}
