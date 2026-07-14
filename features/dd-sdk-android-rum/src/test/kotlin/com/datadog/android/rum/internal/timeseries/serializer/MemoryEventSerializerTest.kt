/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.timeseries.serializer

import com.datadog.android.api.context.DatadogContext
import com.datadog.android.internal.time.TimeProvider
import com.datadog.android.rum.RumSessionType
import com.datadog.android.rum.internal.timeseries.DataPoint
import com.datadog.android.rum.model.TimeseriesMemoryEvent
import com.datadog.android.rum.utils.forge.Configurator
import fr.xgouchet.elmyr.annotation.DoubleForgery
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.LongForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.data.Offset
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.util.UUID

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class MemoryEventSerializerTest {

    @Mock
    lateinit var mockTimeProvider: TimeProvider

    @StringForgery
    lateinit var fakeSessionId: String

    @StringForgery
    lateinit var fakeApplicationId: String

    @LongForgery(min = 1_000_000_000L, max = 16_000_000_000L)
    var fakeTotalRamBytes: Long = 0L

    @LongForgery(min = 1L)
    var fakeNowMs: Long = 0L

    @Forgery
    lateinit var fakeDatadogContext: DatadogContext

    @BeforeEach
    fun `set up`() {
        whenever(mockTimeProvider.getDeviceTimestampMillis()) doReturn fakeNowMs
    }

    private fun testedSerializer(
        sessionType: RumSessionType = RumSessionType.USER,
        totalRamBytes: Long = fakeTotalRamBytes
    ) = MemoryEventSerializer(
        sessionId = fakeSessionId,
        applicationId = fakeApplicationId,
        sessionType = sessionType,
        totalRamBytes = totalRamBytes,
        timeProvider = mockTimeProvider
    )

    @Test
    fun `M return null W serialize() { empty input }`() {
        // When
        val result = testedSerializer().serialize(fakeDatadogContext, emptyList())

        // Then
        assertThat(result).isNull()
    }

    @Test
    fun `M return null W serialize() { totalRamBytes is zero }`(
        @DoubleForgery(min = 1.0) fakeMemory: Double,
        @LongForgery(min = 1L) fakeTs: Long
    ) {
        // When
        val result = testedSerializer(totalRamBytes = 0L)
            .serialize(fakeDatadogContext, listOf(DataPoint(fakeTs, fakeMemory)))

        // Then
        assertThat(result).isNull()
    }

    @Test
    fun `M return null W serialize() { totalRamBytes is negative }`(
        @LongForgery(max = -1L) fakeNegativeRam: Long,
        @DoubleForgery(min = 1.0) fakeMemory: Double,
        @LongForgery(min = 1L) fakeTs: Long
    ) {
        // When
        val result = testedSerializer(totalRamBytes = fakeNegativeRam)
            .serialize(fakeDatadogContext, listOf(DataPoint(fakeTs, fakeMemory)))

        // Then
        assertThat(result).isNull()
    }

    @Test
    fun `M produce object schema W serialize() { compression off }`(
        @DoubleForgery(min = 1.0) fakeMemory: Double,
        @LongForgery(min = 1L) fakeTs: Long
    ) {
        // Given
        val samples = listOf(
            DataPoint(fakeTs, fakeMemory),
            DataPoint(fakeTs + 1L, fakeMemory + 1.0)
        )

        // When
        val result = testedSerializer().serialize(fakeDatadogContext, samples)

        // Then
        val json = checkNotNull(result)
        val timeseries = json.getAsJsonObject(TimeseriesAttributes.KEY_TIMESERIES)
        assertThat(timeseries.get(TimeseriesAttributes.KEY_SCHEMA).asString).isEqualTo(VALUE_SCHEMA_OBJECT)
        assertThat(timeseries.get(KEY_START).asLong).isEqualTo(fakeTs)
        assertThat(timeseries.get(KEY_END).asLong).isEqualTo(fakeTs + 1L)
        assertDoesNotThrow { UUID.fromString(timeseries.get(KEY_ID).asString) }
        assertThat(json.get(KEY_SERVICE).asString).isEqualTo(fakeDatadogContext.service)
        assertThat(json.get(KEY_VERSION).asString).isEqualTo(fakeDatadogContext.version)

        val parsed = TimeseriesMemoryEvent.fromJsonObject(json)
        assertThat(parsed.timeseries.data.timestamps).hasSize(2)
        assertThat(parsed.timeseries.data.values.memoryFootprint.first().toDouble())
            .isCloseTo(fakeMemory / 1024.0, Offset.offset(MEMORY_OFFSET))
        assertThat(parsed.timeseries.data.values.memoryPercent.first().toDouble())
            .isCloseTo(fakeMemory / fakeTotalRamBytes * PERCENT_FACTOR, Offset.offset(MEMORY_OFFSET))
    }

    @Test
    fun `M map session type W serialize() { synthetics }`(
        @DoubleForgery(min = 1.0) fakeMemory: Double,
        @LongForgery(min = 1L) fakeTs: Long
    ) {
        // When
        val result = testedSerializer(sessionType = RumSessionType.SYNTHETICS)
            .serialize(fakeDatadogContext, listOf(DataPoint(fakeTs, fakeMemory), DataPoint(fakeTs + 1L, fakeMemory)))

        // Then
        val json = checkNotNull(result)
        assertThat(json.getAsJsonObject(KEY_SESSION).get(KEY_TYPE).asString).isEqualTo(VALUE_TYPE_SYNTHETICS)
    }

    private companion object {
        private const val PERCENT_FACTOR: Double = 100.0
        private const val MEMORY_OFFSET: Double = 0.0001

        // JSON keys
        private const val KEY_SESSION: String = "session"
        private const val KEY_TYPE: String = "type"
        private const val KEY_ID: String = "id"
        private const val KEY_START: String = "start"
        private const val KEY_END: String = "end"
        private const val KEY_SERVICE: String = "service"
        private const val KEY_VERSION: String = "version"

        // JSON values
        private const val VALUE_SCHEMA_OBJECT: String = "object-v2"
        private const val VALUE_TYPE_SYNTHETICS: String = "synthetics"
    }
}
