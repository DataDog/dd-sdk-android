/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.timeseries.serializer

import com.datadog.android.internal.time.TimeProvider
import com.datadog.android.rum.RumSessionType
import com.datadog.android.rum.internal.timeseries.DataPoint
import com.datadog.android.rum.model.TimeseriesMemoryEvent
import com.datadog.android.rum.utils.forge.Configurator
import fr.xgouchet.elmyr.annotation.DoubleForgery
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.annotation.LongForgery
import fr.xgouchet.elmyr.annotation.StringForgery
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
import java.util.UUID
import kotlin.math.pow
import kotlin.math.roundToLong

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

    @IntForgery(min = 2, max = 9)
    var fakePrecision: Int = 0

    val fakeScale: Long get() = 10.0.pow(fakePrecision.toDouble()).toLong()

    @BeforeEach
    fun `set up`() {
        whenever(mockTimeProvider.getDeviceTimestampMillis()) doReturn fakeNowMs
    }

    private fun testedSerializer(
        useDeltaCompression: Boolean = false,
        sessionType: RumSessionType = RumSessionType.USER,
        totalRamBytes: Long = fakeTotalRamBytes,
        precision: Int = fakePrecision
    ) = MemoryEventSerializer(
        sessionId = fakeSessionId,
        applicationId = fakeApplicationId,
        sessionType = sessionType,
        totalRamBytes = totalRamBytes,
        timeProvider = mockTimeProvider,
        useDeltaCompression = useDeltaCompression,
        precision = precision
    )

    @Test
    fun `M return null W serialize() { empty input }`() {
        // When
        val result = testedSerializer().serialize(emptyList())

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
            .serialize(listOf(DataPoint(fakeTs, fakeMemory)))

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
            .serialize(listOf(DataPoint(fakeTs, fakeMemory)))

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
        val json = testedSerializer().serialize(samples) ?: error(NON_NULL_JSON_ERROR)

        // Then
        val timeseries = json.getAsJsonObject(KEY_TIMESERIES)
        assertThat(timeseries.get(KEY_SCHEMA).asString).isEqualTo(VALUE_SCHEMA_OBJECT)
        assertThat(timeseries.get(KEY_START).asLong).isEqualTo(fakeTs)
        assertThat(timeseries.get(KEY_END).asLong).isEqualTo(fakeTs + 1L)
        UUID.fromString(timeseries.get(KEY_ID).asString)

        val parsed = TimeseriesMemoryEvent.fromJsonObject(json)
        assertThat(parsed.timeseries.data).hasSize(2)
        val first = parsed.timeseries.data.first().dataPoint
        assertThat(first.memoryMax.toDouble()).isCloseTo(fakeMemory, Offset.offset(MEMORY_OFFSET))
        assertThat(first.memoryPercent.toDouble())
            .isCloseTo(fakeMemory / fakeTotalRamBytes * PERCENT_FACTOR, Offset.offset(MEMORY_OFFSET))
    }

    @Test
    fun `M map session type W serialize() { synthetics }`(
        @DoubleForgery(min = 1.0) fakeMemory: Double,
        @LongForgery(min = 1L) fakeTs: Long
    ) {
        // When
        val json = testedSerializer(sessionType = RumSessionType.SYNTHETICS)
            .serialize(listOf(DataPoint(fakeTs, fakeMemory), DataPoint(fakeTs + 1L, fakeMemory)))
            ?: error(NON_NULL_JSON_ERROR)

        // Then
        assertThat(json.getAsJsonObject(KEY_SESSION).get(KEY_TYPE).asString).isEqualTo(VALUE_TYPE_SYNTHETICS)
    }

    @Test
    fun `M produce delta-object schema W serialize() { compression on, multi-sample }`(
        @DoubleForgery(min = 1.0, max = 1_000_000.0) fakeMem1: Double,
        @DoubleForgery(min = 1.0, max = 1_000_000.0) fakeMem2: Double,
        @DoubleForgery(min = 1.0, max = 1_000_000.0) fakeMem3: Double,
        @LongForgery(min = 1L, max = 1_000_000L) fakeTs1: Long,
        @LongForgery(min = 1L, max = 1_000L) fakeTimestampStep: Long
    ) {
        // Given
        val fakeTs2 = fakeTs1 + fakeTimestampStep
        val fakeTs3 = fakeTs2 + fakeTimestampStep

        val samples = listOf(
            DataPoint(fakeTs1, fakeMem1),
            DataPoint(fakeTs2, fakeMem2),
            DataPoint(fakeTs3, fakeMem3)
        )

        // When
        val json = testedSerializer(useDeltaCompression = true).serialize(samples) ?: error(NON_NULL_JSON_ERROR)

        // Then
        val timeseries = json.getAsJsonObject(KEY_TIMESERIES)
        assertThat(timeseries.get(KEY_SCHEMA).asString).isEqualTo(VALUE_SCHEMA_DELTA_OBJECT)
        val data = timeseries.get(KEY_DATA).asJsonObject
        assertThat(data.get(KEY_PRECISION).asInt).isEqualTo(fakePrecision)
        assertThat(data.get(KEY_RESOLUTION).asString).isEqualTo(VALUE_RESOLUTION_NS)

        val tsArray = data.get(KEY_TS).asJsonArray
        assertThat(tsArray[0].asLong).isEqualTo(fakeTs1)
        assertThat(tsArray[1].asLong).isEqualTo(fakeTimestampStep)
        assertThat(tsArray[2].asLong).isEqualTo(fakeTimestampStep)

        val maxArr = data.get(KEY_MEMORY_MAX).asJsonArray
        val pctArr = data.get(KEY_MEMORY_PERCENT).asJsonArray
        val scaledMax = listOf(fakeMem1, fakeMem2, fakeMem3).map { (it * fakeScale).roundToLong() }
        val scaledPct = listOf(fakeMem1, fakeMem2, fakeMem3)
            .map { (it / fakeTotalRamBytes * PERCENT_FACTOR * fakeScale).roundToLong() }

        assertThat(maxArr[0].asLong).isEqualTo(scaledMax[0])
        assertThat(maxArr[1].asLong).isEqualTo(scaledMax[1] - scaledMax[0])
        assertThat(maxArr[2].asLong).isEqualTo(scaledMax[2] - scaledMax[1])

        assertThat(pctArr[0].asLong).isEqualTo(scaledPct[0])
        assertThat(pctArr[1].asLong).isEqualTo(scaledPct[1] - scaledPct[0])
        assertThat(pctArr[2].asLong).isEqualTo(scaledPct[2] - scaledPct[1])
    }

    @Test
    fun `M fall back to object schema W serialize() { compression on, single sample }`(
        @DoubleForgery(min = 1.0) fakeMemory: Double,
        @LongForgery(min = 1L) fakeTs: Long
    ) {
        // When
        val json = testedSerializer(useDeltaCompression = true)
            .serialize(listOf(DataPoint(fakeTs, fakeMemory)))
            ?: error(NON_NULL_JSON_ERROR)

        // Then
        val timeseries = json.getAsJsonObject(KEY_TIMESERIES)
        assertThat(timeseries.get(KEY_SCHEMA).asString).isEqualTo(VALUE_SCHEMA_OBJECT)
        assertThat(timeseries.get(KEY_DATA).isJsonArray).isTrue()
    }

    private companion object {
        private const val PERCENT_FACTOR: Double = 100.0
        private const val MEMORY_OFFSET: Double = 0.0001

        // JSON keys
        private const val KEY_TIMESERIES: String = "timeseries"
        private const val KEY_SCHEMA: String = "schema"
        private const val KEY_DATA: String = "data"
        private const val KEY_SESSION: String = "session"
        private const val KEY_TYPE: String = "type"
        private const val KEY_ID: String = "id"
        private const val KEY_START: String = "start"
        private const val KEY_END: String = "end"
        private const val KEY_PRECISION: String = "precision"
        private const val KEY_RESOLUTION: String = "resolution"
        private const val KEY_TS: String = "ts"
        private const val KEY_MEMORY_MAX: String = "memory_max"
        private const val KEY_MEMORY_PERCENT: String = "memory_percent"

        // JSON values
        private const val VALUE_SCHEMA_OBJECT: String = "object"
        private const val VALUE_SCHEMA_DELTA_OBJECT: String = "delta-object"
        private const val VALUE_TYPE_SYNTHETICS: String = "synthetics"
        private const val VALUE_RESOLUTION_NS: String = "ns"

        private const val NON_NULL_JSON_ERROR: String = "expected non-null json"
    }
}
