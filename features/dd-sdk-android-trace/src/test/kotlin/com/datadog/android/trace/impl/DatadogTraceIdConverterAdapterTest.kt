/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.trace.impl

import com.datadog.android.trace.impl.internal.DatadogTraceIdAdapter
import com.datadog.android.trace.impl.internal.DatadogTraceIdConverterAdapter
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
        assertThat(
            testedConverter.zero()
        ).isEqualTo(
            DatadogTraceIdAdapter(DDTraceId.ZERO)
        )
    }

    @Test
    fun `M return expected W from(Long)`() {
        assertThat(
            testedConverter.from(fakeLong)
        ).isEqualTo(
            DatadogTraceIdAdapter(DDTraceId.from(fakeLong))
        )
    }

    @Test
    fun `M return expected W from(String)`() {
        val stringTraceId = fakeLong.toString()

        assertThat(
            testedConverter.from(stringTraceId)
        ).isEqualTo(
            DatadogTraceIdAdapter(DDTraceId.from(stringTraceId))
        )
    }

    @Test
    fun `M return expected W fromHex(String)`() {
        val stringTraceId = fakeLong.toString()

        assertThat(
            testedConverter.fromHex(stringTraceId)
        ).isEqualTo(
            DatadogTraceIdAdapter(DDTraceId.fromHex(stringTraceId))
        )
    }

    @Test
    fun `M return expected W toLong()`() {
        val traceId = DatadogTraceIdAdapter(DDTraceId.from(fakeLong))

        assertThat(
            testedConverter.toLong(traceId)
        ).isEqualTo(
            fakeLong
        )
    }

    @Test
    fun `M return expected W toHexString()`() {
        val ddTraceId = DDTraceId.from(fakeLong)

        assertThat(
            testedConverter.toHexString(DatadogTraceIdAdapter(ddTraceId))
        ).isEqualTo(
            ddTraceId.toHexString()
        )
    }
}
