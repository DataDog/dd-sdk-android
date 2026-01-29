/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.trace.internal.net

import com.datadog.android.api.SdkCore
import com.datadog.android.core.sampling.Sampler
import com.datadog.android.trace.DeterministicTraceSampler
import com.datadog.android.trace.NetworkTracedRequestListener
import com.datadog.android.trace.NetworkTracingInstrumentationConfiguration
import com.datadog.android.trace.TraceContextInjection
import com.datadog.android.trace.TracingHeaderType
import com.datadog.android.trace.api.span.DatadogSpan
import com.datadog.android.trace.api.tracer.DatadogTracer
import com.datadog.android.utils.forge.Configurator
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.BoolForgery
import fr.xgouchet.elmyr.annotation.FloatForgery
import fr.xgouchet.elmyr.annotation.StringForgery
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
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class TraceInstrumentationBuilderTest {

    private lateinit var testedBuilder: NetworkTracingInstrumentationConfiguration

    private lateinit var fakeTracedHosts: Map<String, Set<TracingHeaderType>>

    @Mock
    lateinit var mockNetworkTracedRequestListener: NetworkTracedRequestListener

    @Mock
    lateinit var mockTraceSampler: Sampler<DatadogSpan>

    @Mock
    lateinit var mockGlobalTracer: DatadogTracer

    @Mock
    lateinit var mockLocalTracer: DatadogTracer

    @StringForgery
    lateinit var fakeNetworkInstrumentationName: String

    @BeforeEach
    fun `set up`(forge: Forge) {
        fakeTracedHosts = forge.aMap {
            aStringMatching("[a-z]+\\.[a-z]{2,3}") to forge.aList {
                aValueFrom(TracingHeaderType::class.java)
            }.toSet()
        }
        testedBuilder = NetworkTracingInstrumentationConfiguration(fakeTracedHosts)
    }

    @Test
    fun `M build with default values W build()`() {
        // When
        val result = testedBuilder.build(fakeNetworkInstrumentationName)

        // Then
        assertThat(result.sdkInstanceName).isNull()
        assertThat(result.traceOrigin).isNull()
        assertThat(result.redacted404ResourceName).isTrue()
        assertThat(result.injectionType).isEqualTo(TraceContextInjection.SAMPLED)
    }

    @Test
    fun `M set trace origin W setTraceOrigin()`(
        @StringForgery fakeTraceOrigin: String
    ) {
        // When
        val result = testedBuilder.setTraceOrigin(fakeTraceOrigin).build(fakeNetworkInstrumentationName)

        // Then
        assertThat(result.traceOrigin).isEqualTo(fakeTraceOrigin)
    }

    @Test
    fun `M set SDK instance name W setSdkInstanceName()`(
        @StringForgery fakeSdkInstanceName: String
    ) {
        // When
        val result = testedBuilder.setSdkInstanceName(fakeSdkInstanceName).build(fakeNetworkInstrumentationName)

        // Then
        assertThat(result.sdkInstanceName).isEqualTo(fakeSdkInstanceName)
    }

    @Test
    fun `M set traced request listener W setTracedRequestListener()`() {
        // When
        val result = testedBuilder.setTracedRequestListener(mockNetworkTracedRequestListener)
            .build(fakeNetworkInstrumentationName)

        // Then
        assertThat(result.tracedRequestListener).isSameAs(mockNetworkTracedRequestListener)
    }

    @Test
    fun `M set trace sample rate W setTraceSampleRate()`(
        @FloatForgery(min = 0f, max = 100f) fakeSampleRate: Float
    ) {
        // When
        val result = testedBuilder.setTraceSampleRate(fakeSampleRate).build(fakeNetworkInstrumentationName)

        // Then
        assertThat(result.traceSampler).isInstanceOf(DeterministicTraceSampler::class.java)
        assertThat(result.traceSampler.getSampleRate()).isEqualTo(fakeSampleRate)
    }

    @Test
    fun `M set trace sampler W setTraceSampler()`() {
        // When
        val result = testedBuilder.setTraceSampler(mockTraceSampler).build(fakeNetworkInstrumentationName)

        // Then
        assertThat(result.traceSampler).isSameAs(mockTraceSampler)
    }

    @Test
    fun `M set trace context injection W setTraceContextInjection() {ALL}`() {
        // When
        val result = testedBuilder.setTraceContextInjection(TraceContextInjection.ALL)
            .build(fakeNetworkInstrumentationName)

        // Then
        assertThat(result.injectionType).isEqualTo(TraceContextInjection.ALL)
    }

    @Test
    fun `M set trace context injection W setTraceContextInjection() {SAMPLED}`() {
        // When
        val result = testedBuilder.setTraceContextInjection(TraceContextInjection.SAMPLED)
            .build(fakeNetworkInstrumentationName)

        // Then
        assertThat(result.injectionType).isEqualTo(TraceContextInjection.SAMPLED)
    }

    @Test
    fun `M set redacted 404 resource name W set404ResourcesRedacted()`(
        @BoolForgery fakeRedacted: Boolean
    ) {
        // When
        val result = testedBuilder.set404ResourcesRedacted(fakeRedacted).build(fakeNetworkInstrumentationName)

        // Then
        assertThat(result.redacted404ResourceName).isEqualTo(fakeRedacted)
    }

    @Test
    fun `M return self W chaining builder methods()`(
        @StringForgery fakeTraceOrigin: String,
        @StringForgery fakeSdkInstanceName: String,
        @FloatForgery(min = 0f, max = 100f) fakeSampleRate: Float,
        @BoolForgery fakeRedacted: Boolean
    ) {
        // When
        val result = testedBuilder
            .setTraceOrigin(fakeTraceOrigin)
            .setSdkInstanceName(fakeSdkInstanceName)
            .setTracedRequestListener(mockNetworkTracedRequestListener)
            .setTraceSampleRate(fakeSampleRate)
            .setTraceContextInjection(TraceContextInjection.ALL)
            .set404ResourcesRedacted(fakeRedacted)
            .disableNetworkLayerInstrumentation()
            .disableRumToTracesLinking()

        // Then
        assertThat(result).isSameAs(testedBuilder)
    }

    @Test
    fun `M set local tracer factory W setLocalTracerFactory()`() {
        // Given
        val fakeFactory: (SdkCore, Set<TracingHeaderType>) -> DatadogTracer = { _, _ -> mockLocalTracer }

        // When
        val result = testedBuilder.setLocalTracerFactory(fakeFactory)

        // Then
        assertThat(result).isSameAs(testedBuilder)
        assertThat(testedBuilder.localTracerFactory).isSameAs(fakeFactory)
    }

    @Test
    fun `M set global tracer provider W setGlobalTracerProvider()`() {
        // Given
        val fakeProvider: () -> DatadogTracer? = { mockGlobalTracer }

        // When
        val result = testedBuilder.setGlobalTracerProvider(fakeProvider)

        // Then
        assertThat(result).isSameAs(testedBuilder)
        assertThat(testedBuilder.globalTracerProvider === fakeProvider).isTrue()
    }

    @Test
    fun `M sanitize hosts W build() {valid and invalid hosts}`() {
        // Given
        val validHost = "example.com"
        val invalidHost = "not a valid host!"
        val mixedHosts = mapOf(
            validHost to setOf(TracingHeaderType.DATADOG),
            invalidHost to setOf(TracingHeaderType.TRACECONTEXT)
        )
        testedBuilder = NetworkTracingInstrumentationConfiguration(mixedHosts)

        // When
        val result = testedBuilder.build(fakeNetworkInstrumentationName)

        // Then
        assertThat(result.localFirstPartyHostHeaderTypeResolver.isFirstPartyUrl("https://$validHost/path")).isTrue()
    }

    @Test
    fun `M disable network instrumentation W disableNetworkLayerInstrumentation()`() {
        // When
        val result = testedBuilder.disableNetworkLayerInstrumentation()

        // Then
        assertThat(result).isSameAs(testedBuilder)
        assertThat(testedBuilder.networkInstrumentationEnabled).isFalse()
    }

    @Test
    fun `M disable RUM to traces linking W disableRumToTracesLinking()`() {
        // When
        val result = testedBuilder.disableRumToTracesLinking()

        // Then
        assertThat(result).isSameAs(testedBuilder)
        assertThat(testedBuilder.rumApmLinkingEnabled).isFalse()
    }
}
