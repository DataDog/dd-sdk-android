/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.okhttp.internal.utils

import com.datadog.android.internal.utils.toHexString
import com.datadog.android.log.LogAttributes
import com.datadog.android.okhttp.trace.newSpanMock
import com.datadog.android.trace.api.span.DatadogSpan
import com.datadog.android.trace.api.span.DatadogSpanContext
import com.datadog.android.trace.impl.internal.DatadogTracingInternal
import com.datadog.tools.unit.forge.BaseConfigurator
import fr.xgouchet.elmyr.Forge
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
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(BaseConfigurator::class)
internal class SpanSamplingIdProviderTest {

    @Mock
    lateinit var mockSpan: DatadogSpan

    @Mock
    lateinit var mockSpanContext: DatadogSpanContext

    private lateinit var fakeTags: Map<String?, Any?>

    @BeforeEach
    fun `set up`(forge: Forge) {
        fakeTags = forge.aMap { anAlphabeticalString() to aString() }
        whenever(mockSpanContext.tags) doReturn forge.aNullable { fakeTags }
        mockSpan = forge.newSpanMock(mockSpanContext)
    }

    @Test
    fun `M return sessionId lsb as ULong W provideId() {has rum session}`(
        @LongForgery(0L, 0xFFFFFFFF) part0: Long,
        @LongForgery(0L, 0xFFFF) part1: Long,
        @LongForgery(0x4000L, 0x4FFF) part2: Long,
        @LongForgery(8000L, 0xDFFF) part3: Long,
        @LongForgery(0L, 0xFFFFFFFFFFFF) part4: Long
    ) {
        // Given
        val expectedId = part4.toULong()
        val sessionId = arrayOf(part0, part1, part2, part3, part4).joinToString("-") { it.toHexString() }
        val fakeTagsWithSessionId = fakeTags + mapOf(LogAttributes.RUM_SESSION_ID to sessionId)
        whenever(mockSpanContext.tags) doReturn fakeTagsWithSessionId

        // When
        val result = SpanSamplingIdProvider.provideId(mockSpan)

        // Then
        assertThat(result).isEqualTo(expectedId)
    }

    @Test
    fun `M return traceId as ULong W provideId() {no rum session}`(
        @LongForgery traceId: Long
    ) {
        // Given
        val expectedId = traceId.toULong()
        whenever(mockSpanContext.traceId) doReturn DatadogTracingInternal.traceIdFactory.from(traceId)

        // When
        val result = SpanSamplingIdProvider.provideId(mockSpan)

        // Then
        assertThat(result).isEqualTo(expectedId)
    }

    @Test
    fun `M return 0u W provideId() {no rum session, invalid traceId}`() {
        // Given
        val expectedId: ULong = 0u
        whenever(mockSpanContext.traceId) doReturn DatadogTracingInternal.traceIdFactory.zero()

        // When
        val result = SpanSamplingIdProvider.provideId(mockSpan)

        // Then
        assertThat(result).isEqualTo(expectedId)
    }

    @Test
    fun `M return 0u W provideId() {no rum session, empty traceId}`() {
        // Given
        val expectedId: ULong = 0u
        whenever(mockSpanContext.traceId) doReturn DatadogTracingInternal.traceIdFactory.zero()

        // When
        val result = SpanSamplingIdProvider.provideId(mockSpan)

        // Then
        assertThat(result).isEqualTo(expectedId)
    }
}
