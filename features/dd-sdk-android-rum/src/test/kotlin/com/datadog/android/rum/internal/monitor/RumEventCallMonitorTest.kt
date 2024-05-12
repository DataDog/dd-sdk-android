/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.monitor

import com.datadog.android.api.InternalLogger
import com.datadog.android.rum.internal.monitor.RumEventCallMonitor.Companion.TOO_MANY_RUM_EVENT_CALLS
import com.datadog.android.rum.utils.forge.Configurator
import com.datadog.android.rum.utils.verifyLog
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.IntForgery
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
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.quality.Strictness
import java.util.Locale
import java.util.concurrent.TimeUnit

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class RumEventCallMonitorTest {
    private lateinit var testedMonitor: RumEventCallMonitor

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @IntForgery(min = 2, max = 10)
    var fakeMaxCallsThreshold: Int = 0

    @LongForgery(min = 10L, max = 20L)
    var fakeMaxCallPeriodMs: Long = 0L

    private val fakeCallsMap = mutableMapOf<String, RumEventCallMonitorEntry>()

    @BeforeEach
    fun setup() {
        testedMonitor = RumEventCallMonitor(
            mockInternalLogger,
            fakeCallsMap,
            maxCallsThreshold = fakeMaxCallsThreshold,
            timePeriodMs = fakeMaxCallPeriodMs
        )
    }

    @Test
    fun `M not warn W trackCallsAndWarnIfNecessary() { num calls below warn threshold }`(
        forge: Forge
    ) {
        // Given
        val eventType = forge.anAlphaNumericalString()

        // When
        testedMonitor.trackCallsAndWarnIfNecessary(eventType = eventType)

        // Then
        verifyNoMoreInteractions(mockInternalLogger)
    }

    @Test
    fun `M initialize and increment call correctly W trackCallsAndWarnIfNecessary()`(
        forge: Forge
    ) {
        // Given
        val eventType = forge.anAlphaNumericalString()

        // When
        testedMonitor.trackCallsAndWarnIfNecessary(eventType = eventType)

        // then
        val eventInMap = fakeCallsMap[eventType]
        assertThat(eventInMap).isNotNull

        eventInMap?.let {
            assertThat(it.numCallsInTimePeriod.get()).isEqualTo(1)

            assertThat(
                System.currentTimeMillis() - (it.timePeriodStartTimeMs.get())
            ).isLessThan(5000)
        }
    }

    @Test
    fun `M warn W trackCallsAndWarnIfNecessary() { too many calls within time frame }`(
        forge: Forge
    ) {
        // Given
        val eventType = forge.anAlphaNumericalString()

        // When
        repeat(fakeMaxCallsThreshold + 1) {
            testedMonitor.trackCallsAndWarnIfNecessary(eventType = eventType)
        }

        // Then
        mockInternalLogger.verifyLog(
            level = InternalLogger.Level.WARN,
            target = InternalLogger.Target.USER,
            TOO_MANY_RUM_EVENT_CALLS.format(Locale.US, fakeMaxCallsThreshold, eventType, fakeMaxCallPeriodMs)
        )
    }

    @Test
    fun `M reinitialize event in map W trackCallsAndWarnIfNecessary() { time period has passed }`(
        forge: Forge
    ) {
        // Given
        val eventType = forge.anAlphaNumericalString()

        testedMonitor = RumEventCallMonitor(
            mockInternalLogger,
            fakeCallsMap,
            maxCallsThreshold = 20,
            timePeriodMs = 1
        )

        // When
        testedMonitor.trackCallsAndWarnIfNecessary(eventType)

        val firstEventTimePeriodStartMs = fakeCallsMap[eventType]?.timePeriodStartTimeMs?.get()

        TimeUnit.SECONDS.sleep(2L)

        testedMonitor.trackCallsAndWarnIfNecessary(eventType)
        val secondEventTimePeriodStartMs = fakeCallsMap[eventType]?.timePeriodStartTimeMs?.get()

        // Then
        assertThat(firstEventTimePeriodStartMs).isNotEqualTo(secondEventTimePeriodStartMs)

        assertThat(fakeCallsMap[eventType]?.numCallsInTimePeriod?.get()).isEqualTo(1)
    }
}
