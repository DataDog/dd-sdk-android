/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.timeseries.serializer

import com.datadog.android.internal.time.TimeProvider
import com.datadog.android.rum.RumSessionType
import com.datadog.android.rum.internal.timeseries.DataPoint
import com.datadog.android.rum.model.TimeseriesCpuEvent
import com.datadog.android.rum.utils.forge.Configurator
import fr.xgouchet.elmyr.annotation.DoubleForgery
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
import kotlin.math.roundToLong

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class CpuEventSerializerTest {

    @Mock
    lateinit var mockTimeProvider: TimeProvider

    @StringForgery
    lateinit var fakeSessionId: String

    @StringForgery
    lateinit var fakeApplicationId: String

    @LongForgery(min = 1L)
    var fakeNowMs: Long = 0L

    @BeforeEach
    fun `set up`() {
        whenever(mockTimeProvider.getDeviceTimestampMillis()) doReturn fakeNowMs
    }

    @Test
    fun `M return null W serialize() { empty input }`() {
        // Given
        val testedSerializer = CpuEventSerializer(
            sessionId = fakeSessionId,
            applicationId = fakeApplicationId,
            sessionType = RumSessionType.USER,
            timeProvider = mockTimeProvider
        )

        // When
        val result = testedSerializer.serialize(emptyList())

        // Then
        assertThat(result).isNull()
    }

    @Test
    fun `M produce object schema W serialize() { compression off }`(
        @DoubleForgery(min = 0.0, max = 100.0) fakeCpuValue: Double,
        @LongForgery(min = 1L) fakeTs: Long
    ) {
        // Given
        val testedSerializer = CpuEventSerializer(
            sessionId = fakeSessionId,
            applicationId = fakeApplicationId,
            sessionType = RumSessionType.USER,
            timeProvider = mockTimeProvider,
            useDeltaCompression = false
        )
        val samples = listOf(DataPoint(fakeTs, fakeCpuValue), DataPoint(fakeTs + 1L, fakeCpuValue + 0.1))

        // When
        val json = testedSerializer.serialize(samples) ?: error(NON_NULL_JSON_ERROR)

        // Then
        val timeseries = json.getAsJsonObject(KEY_TIMESERIES)
        assertThat(timeseries.get(KEY_SCHEMA).asString).isEqualTo(VALUE_SCHEMA_OBJECT)
        val data = timeseries.get(KEY_DATA).asJsonArray
        assertThat(data).hasSize(2)
        assertThat(json.getAsJsonObject(KEY_SESSION).get(KEY_TYPE).asString).isEqualTo(VALUE_TYPE_USER)
        assertThat(json.getAsJsonObject(KEY_APPLICATION).get(KEY_ID).asString).isEqualTo(fakeApplicationId)
        assertThat(json.get(KEY_DATE).asLong).isEqualTo(fakeNowMs)
        assertThat(timeseries.get(KEY_START).asLong).isEqualTo(fakeTs)
        assertThat(timeseries.get(KEY_END).asLong).isEqualTo(fakeTs + 1L)
        // id is a valid UUID
        UUID.fromString(timeseries.get(KEY_ID).asString)
    }

    @Test
    fun `M map session type W serialize() { synthetics }`(
        @DoubleForgery(min = 0.0, max = 100.0) fakeCpuValue: Double,
        @LongForgery(min = 1L) fakeTs: Long
    ) {
        // Given
        val testedSerializer = CpuEventSerializer(
            sessionId = fakeSessionId,
            applicationId = fakeApplicationId,
            sessionType = RumSessionType.SYNTHETICS,
            timeProvider = mockTimeProvider
        )
        val samples = listOf(DataPoint(fakeTs, fakeCpuValue))

        // When
        val json = testedSerializer.serialize(samples) ?: error(NON_NULL_JSON_ERROR)

        // Then
        assertThat(json.getAsJsonObject(KEY_SESSION).get(KEY_TYPE).asString).isEqualTo(VALUE_TYPE_SYNTHETICS)
        // 1-sample fallback to OBJECT schema even if delta were on
        assertThat(json.getAsJsonObject(KEY_TIMESERIES).get(KEY_SCHEMA).asString).isEqualTo(VALUE_SCHEMA_OBJECT)
    }

    @Test
    fun `M produce delta-scalar schema W serialize() { compression on, multi-sample }`(
        @DoubleForgery(min = 0.001, max = 100.0) fakeFirstCpu: Double,
        @DoubleForgery(min = 0.001, max = 100.0) fakeSecondCpu: Double,
        @DoubleForgery(min = 0.001, max = 100.0) fakeThirdCpu: Double,
        @LongForgery(min = 1L, max = 1_000_000L) fakeTs1: Long,
        @LongForgery(min = 1L, max = 1_000L) fakeTimestampStep: Long
    ) {
        // Given
        val testedSerializer = CpuEventSerializer(
            sessionId = fakeSessionId,
            applicationId = fakeApplicationId,
            sessionType = RumSessionType.USER,
            timeProvider = mockTimeProvider,
            useDeltaCompression = true
        )

        val fakeTs2 = fakeTs1 + fakeTimestampStep
        val fakeTs3 = fakeTs2 + fakeTimestampStep

        val samples = listOf(
            DataPoint(fakeTs1, fakeFirstCpu),
            DataPoint(fakeTs2, fakeSecondCpu),
            DataPoint(fakeTs3, fakeThirdCpu)
        )

        // When
        val json = testedSerializer.serialize(samples) ?: error(NON_NULL_JSON_ERROR)

        // Then
        val timeseries = json.getAsJsonObject(KEY_TIMESERIES)
        assertThat(timeseries.get(KEY_SCHEMA).asString).isEqualTo(VALUE_SCHEMA_DELTA_SCALAR)
        val data = timeseries.get(KEY_DATA).asJsonObject
        assertThat(data.get(KEY_PRECISION).asInt).isEqualTo(EXPECTED_PRECISION)
        assertThat(data.get(KEY_RESOLUTION).asString).isEqualTo(VALUE_RESOLUTION_NS)

        val tsArray = data.get(KEY_TS).asJsonArray
        assertThat(tsArray[0].asLong).isEqualTo(fakeTs1)
        assertThat(tsArray[1].asLong).isEqualTo(fakeTimestampStep)
        assertThat(tsArray[2].asLong).isEqualTo(fakeTimestampStep)

        val cpuArray = data.get(KEY_VALUE).asJsonArray
        val scaled = listOf(fakeFirstCpu, fakeSecondCpu, fakeThirdCpu).map { (it * SCALE).roundToLong() }
        assertThat(cpuArray[0].asLong).isEqualTo(scaled[0])
        assertThat(cpuArray[1].asLong).isEqualTo(scaled[1] - scaled[0])
        assertThat(cpuArray[2].asLong).isEqualTo(scaled[2] - scaled[1])
    }

    @Test
    fun `M fall back to object schema W serialize() { compression on, single sample }`(
        @DoubleForgery(min = 0.001, max = 100.0) fakeCpu: Double,
        @LongForgery(min = 1L) fakeTs: Long
    ) {
        // Given - encodeDelta returns null when data.size <= 1
        val testedSerializer = CpuEventSerializer(
            sessionId = fakeSessionId,
            applicationId = fakeApplicationId,
            sessionType = RumSessionType.USER,
            timeProvider = mockTimeProvider,
            useDeltaCompression = true
        )

        // When
        val json = testedSerializer.serialize(listOf(DataPoint(fakeTs, fakeCpu)))
            ?: error(NON_NULL_JSON_ERROR)

        // Then
        val timeseries = json.getAsJsonObject(KEY_TIMESERIES)
        assertThat(timeseries.get(KEY_SCHEMA).asString).isEqualTo(VALUE_SCHEMA_OBJECT)
        // data is a JsonArray (object schema), not a JsonObject (delta)
        assertThat(timeseries.get(KEY_DATA).isJsonArray).isTrue()
    }

    @Test
    fun `M preserve cpu_usage W serialize() { object schema }`(
        @DoubleForgery(min = 1.0, max = 100.0) fakeCpu: Double,
        @LongForgery(min = 1L) fakeTs: Long
    ) {
        // Given
        val testedSerializer = CpuEventSerializer(
            sessionId = fakeSessionId,
            applicationId = fakeApplicationId,
            sessionType = RumSessionType.USER,
            timeProvider = mockTimeProvider
        )

        // When
        val json = testedSerializer.serialize(
            listOf(DataPoint(fakeTs, fakeCpu), DataPoint(fakeTs + 1L, fakeCpu))
        ) ?: error(NON_NULL_JSON_ERROR)

        // Then
        val parsed = TimeseriesCpuEvent.fromJsonObject(json)
        assertThat(parsed.timeseries.data).hasSize(2)
        assertThat(parsed.timeseries.data.first().dataPoint.cpuUsage.toDouble())
            .isCloseTo(fakeCpu, Offset.offset(CPU_OFFSET))
    }

    private companion object {
        private const val SCALE: Long = 10_000L
        private const val EXPECTED_PRECISION: Int = 4
        private const val CPU_OFFSET: Double = 0.0001

        // JSON keys
        private const val KEY_TIMESERIES: String = "timeseries"
        private const val KEY_SCHEMA: String = "schema"
        private const val KEY_DATA: String = "data"
        private const val KEY_SESSION: String = "session"
        private const val KEY_APPLICATION: String = "application"
        private const val KEY_TYPE: String = "type"
        private const val KEY_ID: String = "id"
        private const val KEY_DATE: String = "date"
        private const val KEY_START: String = "start"
        private const val KEY_END: String = "end"
        private const val KEY_PRECISION: String = "precision"
        private const val KEY_RESOLUTION: String = "resolution"
        private const val KEY_TS: String = "ts"
        private const val KEY_VALUE: String = "value"

        // JSON values
        private const val VALUE_SCHEMA_OBJECT: String = "object"
        private const val VALUE_SCHEMA_DELTA_SCALAR: String = "delta-scalar"
        private const val VALUE_TYPE_USER: String = "user"
        private const val VALUE_TYPE_SYNTHETICS: String = "synthetics"
        private const val VALUE_RESOLUTION_NS: String = "ns"

        private const val NON_NULL_JSON_ERROR: String = "expected non-null json"
    }
}
