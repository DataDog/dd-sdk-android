/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.timeseries.serializer

import com.datadog.android.api.context.DatadogContext
import com.datadog.android.internal.time.TimeProvider
import com.datadog.android.rum.RumSessionType
import com.datadog.android.rum.assertj.TimeseriesCpuEventAssert.Companion.assertThat
import com.datadog.android.rum.internal.timeseries.DataPoint
import com.datadog.android.rum.model.TimeseriesCpuEvent
import com.datadog.android.rum.utils.forge.Configurator
import com.google.gson.JsonObject
import fr.xgouchet.elmyr.annotation.DoubleForgery
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.LongForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.data.Offset
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class CpuEventSerializerTest {

    @LongForgery(min = 1L)
    var fakeNowMs: Long = 0L

    @StringForgery
    lateinit var fakeSessionId: String

    @StringForgery
    lateinit var fakeApplicationId: String

    @Forgery
    lateinit var fakeDatadogContext: DatadogContext

    val mockTimeProvider: TimeProvider = mock<TimeProvider> {
        on { getDeviceTimestampMillis() } doAnswer { fakeNowMs }
    }

    private fun testedSerializer(
        sessionType: RumSessionType = RumSessionType.USER
    ) = CpuEventSerializer(
        sessionId = fakeSessionId,
        applicationId = fakeApplicationId,
        sessionType = sessionType,
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
    fun `M produce object schema W serialize() { compression off }`(
        @DoubleForgery(min = 0.0, max = 100.0) fakeCpuValue: Double,
        @LongForgery(min = 1L) fakeTs: Long
    ) {
        // Given
        val samples = listOf(
            DataPoint(fakeTs, fakeCpuValue),
            DataPoint(fakeTs + 1L, fakeCpuValue + 0.1)
        )

        // When
        val json = testedSerializer().serialize(fakeDatadogContext, samples).failIfNull()

        // Then
        assertThat(json)
            .hasDate(fakeNowMs)
            .hasValidTimeseriesId()
            .hasTimeseriesDataCount(2)
            .hasTimeseriesStart(fakeTs)
            .hasTimeseriesEnd(fakeTs + 1L)
            .hasApplicationId(fakeApplicationId)
            .hasSessionType(TimeseriesCpuEvent.Type.USER)
            .hasTimeseriesSchema("object-v2")
            .hasService(fakeDatadogContext.service)
            .hasVersion(fakeDatadogContext.version)
    }

    @Test
    fun `M map session type W serialize() { synthetics }`(
        @DoubleForgery(min = 0.0, max = 100.0) fakeCpuValue: Double,
        @LongForgery(min = 1L) fakeTs: Long
    ) {
        // Given
        val samples = listOf(DataPoint(fakeTs, fakeCpuValue))

        // When
        val json = testedSerializer(sessionType = RumSessionType.SYNTHETICS)
            .serialize(fakeDatadogContext, samples)
            .failIfNull()

        // Then
        assertThat(json).hasSessionType(TimeseriesCpuEvent.Type.SYNTHETICS)
    }

    @Test
    fun `M preserve cpu_usage W serialize() { object schema }`(
        @DoubleForgery(min = 1.0, max = 100.0) fakeCpu: Double,
        @LongForgery(min = 1L) fakeTs: Long
    ) {
        // Given
        val samples = listOf(
            DataPoint(fakeTs, fakeCpu),
            DataPoint(fakeTs + 1L, fakeCpu)
        )

        // When
        val json = testedSerializer().serialize(fakeDatadogContext, samples).failIfNull()

        // Then
        assertThat(json)
            .hasCpuUsage(fakeCpu, Offset.offset(CPU_OFFSET), position = 0)
    }

    private companion object {
        private const val CPU_OFFSET: Double = 0.0001
        private const val NON_NULL_JSON_ERROR: String = "expected non-null json"
        private fun JsonObject?.failIfNull(): JsonObject {
            if (this == null) error(NON_NULL_JSON_ERROR) else return this
        }
    }
}
