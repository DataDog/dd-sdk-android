/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.trace.internal

import com.datadog.android.utils.forge.Configurator
import com.datadog.trace.api.DDTraceId
import fr.xgouchet.elmyr.annotation.LongForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
class DatadogTraceIdConverterAdapterTest {

    @LongForgery(min = 0)
    private var fakeLong = 0L

    private val testedConverter = DatadogTraceIdConverterAdapter

    @Test
    fun `M return ZERO W zero`() {
        // When
        val actual = testedConverter.zero()

        // Then
        assertThat(actual).isEqualTo(DatadogTraceIdAdapter(DDTraceId.ZERO))
    }

    @Test
    fun `M return expected W from(Long)`() {
        // When
        val actual = testedConverter.from(fakeLong)

        // Then
        assertThat(actual).isEqualTo(DatadogTraceIdAdapter(DDTraceId.from(fakeLong)))
    }

    @Test
    fun `M return expected W from(String)`() {
        // Given
        val stringTraceId = fakeLong.toString()

        // When
        val actual = testedConverter.from(stringTraceId)

        assertThat(actual).isEqualTo(DatadogTraceIdAdapter(DDTraceId.from(stringTraceId)))
    }

    @Test
    fun `M return expected W fromHex(String)`() {
        // Given
        val stringTraceId = fakeLong.toString()

        // When
        val actual = testedConverter.fromHex(stringTraceId)

        // Then
        assertThat(actual).isEqualTo(DatadogTraceIdAdapter(DDTraceId.fromHex(stringTraceId)))
    }

    @Test
    fun `M return expected W toLong()`() {
        // Given
        val traceId = DatadogTraceIdAdapter(DDTraceId.from(fakeLong))

        // When
        val actual = testedConverter.toLong(traceId)

        // Then
        assertThat(actual).isEqualTo(fakeLong)
    }

    @Test
    fun `M return expected W toHexString()`() {
        // Given
        val ddTraceId = DDTraceId.from(fakeLong)

        // When
        val actual = testedConverter.toHexString(DatadogTraceIdAdapter(ddTraceId))

        // Then
        assertThat(actual).isEqualTo(ddTraceId.toHexString())
    }
}
