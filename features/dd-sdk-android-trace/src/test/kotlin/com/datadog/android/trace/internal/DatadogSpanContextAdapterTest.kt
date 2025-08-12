/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.trace.internal

import com.datadog.android.utils.forge.Configurator
import com.datadog.trace.api.DDTraceId
import com.datadog.trace.bootstrap.instrumentation.api.AgentSpan
import com.datadog.trace.core.DDSpanContext
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
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
class DatadogSpanContextAdapterTest {

    @LongForgery
    private var fakeLong: Long = 0L

    @IntForgery
    private var fakeInt: Int = 0

    @Mock
    lateinit var mockAgentSpanContext: AgentSpan.Context

    @Mock
    lateinit var mockDDSpanContext: DDSpanContext

    private lateinit var testedAgentSpanContextAdapter: DatadogSpanContextAdapter
    private lateinit var testedDDSpanContextContextAdapter: DatadogSpanContextAdapter

    @BeforeEach
    fun `set up`() {
        testedAgentSpanContextAdapter = DatadogSpanContextAdapter(mockAgentSpanContext)
        testedDDSpanContextContextAdapter = DatadogSpanContextAdapter(mockDDSpanContext)
    }

    @Test
    fun `M return delegate#spanId W spanId is called`() {
        // Given
        whenever(mockAgentSpanContext.spanId).thenReturn(fakeLong)

        // When
        val actual = testedAgentSpanContextAdapter.spanId

        // Then
        assertThat(actual).isEqualTo(fakeLong)
    }

    @Test
    fun `M return delegate#samplingPriority W samplingPriority is called`() {
        // Given
        whenever(mockAgentSpanContext.traceSamplingPriority).thenReturn(fakeInt)

        // When
        val actual = testedAgentSpanContextAdapter.samplingPriority

        // Then
        assertThat(actual).isEqualTo(fakeInt)
    }

    @Test
    fun `M return delegate#tags W tags is called`(forge: Forge) {
        // Given
        val expectedMap = forge.aMap { aString() to aString() }
        whenever(mockDDSpanContext.tags).thenReturn(expectedMap)

        // When
        val actual = testedDDSpanContextContextAdapter.tags

        // Then
        assertThat(actual).isEqualTo(expectedMap)
    }

    @Test
    fun `M return delegate#traceId W traceId is called`(forge: Forge) {
        // Given
        val ddTraceId = forge.getForgery<DDTraceId>()

        // When
        whenever(mockAgentSpanContext.traceId).thenReturn(ddTraceId)

        // Then
        assertThat(testedAgentSpanContextAdapter.traceId).isEqualTo(DatadogTraceIdAdapter(ddTraceId))
    }
}
