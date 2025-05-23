/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.okhttp.internal.otel

import com.datadog.android.okhttp.TraceContext
import com.datadog.android.okhttp.internal.utils.forge.OkHttpConfigurator
import com.datadog.opentracing.propagation.ExtractedContext
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness
import java.math.BigInteger

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(OkHttpConfigurator::class)
internal class TraceContextExtTest {

    @Forgery
    lateinit var fakeTestedContext: TraceContext

    @Test
    fun `M extract the context W toOpenTracingContext`() {
        // When
        val spanContext = fakeTestedContext.toAgentSpanContext()

        // Then
        val extractedContext = spanContext as ExtractedContext
        assertThat(extractedContext.traceId).isEqualTo(BigInteger(fakeTestedContext.traceId, 16))
        assertThat(extractedContext.spanId).isEqualTo(BigInteger(fakeTestedContext.spanId, 16))
        assertThat(extractedContext.samplingPriority).isEqualTo(fakeTestedContext.samplingPriority)
    }

    @Test
    fun `M extract the context W toOpenTracingContext {broken trace id format}`(forge: Forge) {
        // When
        val fakeBrokenTraceId = forge.aNonHexadecimalString()
        fakeTestedContext = fakeTestedContext.copy(traceId = fakeBrokenTraceId)
        val spanContext = fakeTestedContext.toAgentSpanContext()

        // Then
        val extractedContext = spanContext as ExtractedContext
        assertThat(extractedContext.traceId).isEqualTo(BigInteger.ZERO)
        assertThat(extractedContext.spanId).isEqualTo(BigInteger(fakeTestedContext.spanId, 16))
        assertThat(extractedContext.samplingPriority).isEqualTo(fakeTestedContext.samplingPriority)
    }

    @Test
    fun `M extract the context W toOpenTracingContext {broken span id format}`(forge: Forge) {
        // When
        val fakeBrokenSpanId = forge.aNonHexadecimalString()
        fakeTestedContext = fakeTestedContext.copy(spanId = fakeBrokenSpanId)
        val spanContext = fakeTestedContext.toAgentSpanContext()

        // Then
        val extractedContext = spanContext as ExtractedContext
        assertThat(extractedContext.traceId).isEqualTo(BigInteger(fakeTestedContext.traceId, 16))
        assertThat(extractedContext.spanId).isEqualTo(BigInteger.ZERO)
        assertThat(extractedContext.samplingPriority).isEqualTo(fakeTestedContext.samplingPriority)
    }

    private fun Forge.aNonHexadecimalString(): String {
        return anAlphabeticalString() + aStringMatching("[^0-9a-fA-F]")
    }
}
