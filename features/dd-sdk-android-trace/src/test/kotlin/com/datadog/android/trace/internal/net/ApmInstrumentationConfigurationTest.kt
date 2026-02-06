/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.trace.internal.net

import com.datadog.android.api.SdkCore
import com.datadog.android.core.sampling.Sampler
import com.datadog.android.trace.ApmNetworkInstrumentationConfiguration
import com.datadog.android.trace.ApmNetworkInstrumentationConfiguration.Companion.createInstrumentation
import com.datadog.android.trace.ApmNetworkTracingScope
import com.datadog.android.trace.DeterministicTraceSampler
import com.datadog.android.trace.NetworkTracedRequestListener
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
internal class ApmInstrumentationConfigurationTest {

    private lateinit var testedBuilder: ApmNetworkInstrumentationConfiguration

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
    lateinit var fakeNetworkLibraryName: String

    @BeforeEach
    fun `set up`(forge: Forge) {
        fakeTracedHosts = forge.aMap {
            aStringMatching("[a-z]+\\.[a-z]{2,3}") to forge.aList {
                aValueFrom(TracingHeaderType::class.java)
            }.toSet()
        }
        testedBuilder = ApmNetworkInstrumentationConfiguration(fakeTracedHosts)
    }

    @Test
    fun `M build with default values W build()`() {
        // When
        val result = testedBuilder.createInstrumentation(fakeNetworkLibraryName)

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
        val result = testedBuilder.setTraceOrigin(fakeTraceOrigin).createInstrumentation(fakeNetworkLibraryName)

        // Then
        assertThat(result.traceOrigin).isEqualTo(fakeTraceOrigin)
    }

    @Test
    fun `M set SDK instance name W setSdkInstanceName()`(
        @StringForgery fakeSdkInstanceName: String
    ) {
        // When
        val result = testedBuilder.setSdkInstanceName(fakeSdkInstanceName).createInstrumentation(fakeNetworkLibraryName)

        // Then
        assertThat(result.sdkInstanceName).isEqualTo(fakeSdkInstanceName)
    }

    @Test
    fun `M set traced request listener W setTracedRequestListener()`() {
        // When
        val result = testedBuilder.setTracedRequestListener(mockNetworkTracedRequestListener)
            .createInstrumentation(fakeNetworkLibraryName)

        // Then
        assertThat(result.tracedRequestListener).isSameAs(mockNetworkTracedRequestListener)
    }

    @Test
    fun `M set trace sample rate W setTraceSampleRate()`(
        @FloatForgery(min = 0f, max = 100f) fakeSampleRate: Float
    ) {
        // When
        val result = testedBuilder.setTraceSampleRate(fakeSampleRate).createInstrumentation(fakeNetworkLibraryName)

        // Then
        assertThat(result.traceSampler).isInstanceOf(DeterministicTraceSampler::class.java)
        assertThat(result.traceSampler.getSampleRate()).isEqualTo(fakeSampleRate)
    }

    @Test
    fun `M set trace sampler W setTraceSampler()`() {
        // When
        val result = testedBuilder.setTraceSampler(mockTraceSampler).createInstrumentation(fakeNetworkLibraryName)

        // Then
        assertThat(result.traceSampler).isSameAs(mockTraceSampler)
    }

    @Test
    fun `M set trace context injection W setTraceContextInjection() {ALL}`() {
        // When
        val result = testedBuilder.setTraceContextInjection(TraceContextInjection.ALL)
            .createInstrumentation(fakeNetworkLibraryName)

        // Then
        assertThat(result.injectionType).isEqualTo(TraceContextInjection.ALL)
    }

    @Test
    fun `M set trace context injection W setTraceContextInjection() {SAMPLED}`() {
        // When
        val result = testedBuilder.setTraceContextInjection(TraceContextInjection.SAMPLED)
            .createInstrumentation(fakeNetworkLibraryName)

        // Then
        assertThat(result.injectionType).isEqualTo(TraceContextInjection.SAMPLED)
    }

    @Test
    fun `M set redacted 404 resource name W set404ResourcesRedacted()`(
        @BoolForgery fakeRedacted: Boolean
    ) {
        // When
        val result = testedBuilder.set404ResourcesRedacted(fakeRedacted).createInstrumentation(fakeNetworkLibraryName)

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
            .setTracingScope(ApmNetworkTracingScope.APPLICATION_LEVEL_REQUESTS_ONLY)

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
    fun `M return self W setRumInstrumentationActive()`(
        @BoolForgery fakeActive: Boolean
    ) {
        // When
        val result = testedBuilder.setRumInstrumentationActive(fakeActive)

        // Then
        assertThat(result).isSameAs(testedBuilder)
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
        testedBuilder = ApmNetworkInstrumentationConfiguration(mixedHosts)

        // When
        val result = testedBuilder.createInstrumentation(fakeNetworkLibraryName)

        // Then
        assertThat(result.localFirstPartyHostHeaderTypeResolver.isFirstPartyUrl("https://$validHost/path")).isTrue()
    }

    @Test
    fun `M set APPLICATION_LEVEL_REQUESTS_ONLY scope W setTracingScope()`() {
        // When
        val result = testedBuilder.setTracingScope(ApmNetworkTracingScope.APPLICATION_LEVEL_REQUESTS_ONLY)

        // Then
        assertThat(result).isSameAs(testedBuilder)
        assertThat(testedBuilder.networkTracingScope).isEqualTo(ApmNetworkTracingScope.APPLICATION_LEVEL_REQUESTS_ONLY)
    }

    @Test
    fun `M set DETAILED scope W setTracingScope()`() {
        // When
        val result = testedBuilder.setTracingScope(ApmNetworkTracingScope.DETAILED)

        // Then
        assertThat(result).isSameAs(testedBuilder)
        assertThat(testedBuilder.networkTracingScope).isEqualTo(ApmNetworkTracingScope.DETAILED)
    }
}
