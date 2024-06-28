/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.okhttp

import com.datadog.android.core.sampling.RateBasedSampler
import com.datadog.android.core.sampling.Sampler
import com.datadog.android.okhttp.internal.rum.NoOpRumResourceAttributesProvider
import com.datadog.android.okhttp.trace.NoOpTracedRequestListener
import com.datadog.android.okhttp.trace.TracedRequestListener
import com.datadog.android.rum.RumResourceAttributesProvider
import com.datadog.android.trace.TracingHeaderType
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import com.datadog.tools.unit.forge.BaseConfigurator
import fr.xgouchet.elmyr.Forge
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
    ExtendWith(ForgeExtension::class),
    ExtendWith(TestConfigurationExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(BaseConfigurator::class)
internal class DatadogInterceptorBuilderTest {

    @StringForgery
    lateinit var fakeSdkInstaceName: String

    private lateinit var fakeTraceContextInjection: TraceContextInjection

    private lateinit var fakeTracedHostsWithHeaderType: Map<String, Set<TracingHeaderType>>

    @Mock
    lateinit var mockRumResourceAttributesProvider: RumResourceAttributesProvider

    @Mock
    lateinit var mockTracedRequestListener: TracedRequestListener

    private lateinit var fakeTracedHosts: List<String>

    @Mock
    lateinit var mockSampler: Sampler

    @BeforeEach
    fun `set up`(forge: Forge) {
        fakeTracedHosts = forge.aList { forge.aString() }
        fakeTraceContextInjection = forge.aValueFrom(TraceContextInjection::class.java)
        fakeTracedHostsWithHeaderType = forge.aMap {
            forge.aString() to setOf(forge.aValueFrom(TracingHeaderType::class.java))
        }
    }

    @Test
    fun `M build a DatadogInterceptor with default values W build { traced hosts with header types provided }`() {
        // Given
        val builder = DatadogInterceptor.Builder(fakeTracedHostsWithHeaderType)

        // When
        val interceptor = builder.build()

        // Then
        assertThat(interceptor.tracedHosts).isEqualTo(fakeTracedHostsWithHeaderType)
        assertThat(interceptor.traceContextInjection).isEqualTo(TraceContextInjection.All)
        assertThat(interceptor.sdkInstanceName).isNull()
        assertThat(
            interceptor.rumResourceAttributesProvider
        ).isInstanceOf(NoOpRumResourceAttributesProvider::class.java)
        assertThat(interceptor.tracedRequestListener).isInstanceOf(NoOpTracedRequestListener::class.java)
        assertThat(interceptor.traceSampler).isInstanceOf(RateBasedSampler::class.java)
        assertThat(interceptor.traceOrigin).isEqualTo(DatadogInterceptor.ORIGIN_RUM)
        assertThat(interceptor.localTracerFactory).isNotNull()
    }

    @Test
    fun `M build a DatadogInterceptor with default values W build { only traced hosts provided }`() {
        // Given
        val builder = DatadogInterceptor.Builder(fakeTracedHosts)

        // When
        val interceptor = builder.build()

        // Then
        assertThat(interceptor.tracedHosts).containsExactlyEntriesOf(
            fakeTracedHosts.associateWith {
                setOf(TracingHeaderType.DATADOG, TracingHeaderType.TRACECONTEXT)
            }
        )
        assertThat(interceptor.traceContextInjection).isEqualTo(TraceContextInjection.All)
        assertThat(interceptor.sdkInstanceName).isNull()
        assertThat(
            interceptor.rumResourceAttributesProvider
        ).isInstanceOf(NoOpRumResourceAttributesProvider::class.java)
        assertThat(interceptor.tracedRequestListener).isInstanceOf(NoOpTracedRequestListener::class.java)
        assertThat(interceptor.traceSampler).isInstanceOf(RateBasedSampler::class.java)
        assertThat(interceptor.traceOrigin).isEqualTo(DatadogInterceptor.ORIGIN_RUM)
        assertThat(interceptor.localTracerFactory).isNotNull()
    }

    @Test
    fun `M set sdkInstanceName W build { setSdkInstanceName }`() {
        // When
        val interceptor = DatadogInterceptor.Builder(fakeTracedHostsWithHeaderType)
            .setSdkInstanceName(fakeSdkInstaceName)
            .build()

        // Then
        assertThat(interceptor.tracedHosts).isEqualTo(fakeTracedHostsWithHeaderType)
        assertThat(interceptor.traceContextInjection).isEqualTo(TraceContextInjection.All)
        assertThat(interceptor.sdkInstanceName).isEqualTo(fakeSdkInstaceName)
        assertThat(
            interceptor.rumResourceAttributesProvider
        ).isInstanceOf(NoOpRumResourceAttributesProvider::class.java)
        assertThat(interceptor.tracedRequestListener).isInstanceOf(NoOpTracedRequestListener::class.java)
        assertThat(interceptor.traceSampler).isInstanceOf(RateBasedSampler::class.java)
        assertThat(interceptor.traceOrigin).isEqualTo(DatadogInterceptor.ORIGIN_RUM)
        assertThat(interceptor.localTracerFactory).isNotNull()
    }

    @Test
    fun `M set traceContextInjection W build { setTraceContextInjection }`() {
        // When
        val interceptor = DatadogInterceptor.Builder(fakeTracedHostsWithHeaderType)
            .setTraceContextInjection(fakeTraceContextInjection)
            .build()

        // Then
        assertThat(interceptor.tracedHosts).isEqualTo(fakeTracedHostsWithHeaderType)
        assertThat(interceptor.traceContextInjection).isEqualTo(fakeTraceContextInjection)
        assertThat(interceptor.sdkInstanceName).isNull()
        assertThat(
            interceptor.rumResourceAttributesProvider
        ).isInstanceOf(NoOpRumResourceAttributesProvider::class.java)
        assertThat(interceptor.tracedRequestListener).isInstanceOf(NoOpTracedRequestListener::class.java)
        assertThat(interceptor.traceSampler).isInstanceOf(RateBasedSampler::class.java)
        assertThat(interceptor.traceOrigin).isEqualTo(DatadogInterceptor.ORIGIN_RUM)
        assertThat(interceptor.localTracerFactory).isNotNull()
    }

    @Test
    fun `M set traceRequestListener W build { setTraceRequestListener }`() {
        // When
        val interceptor = DatadogInterceptor.Builder(fakeTracedHostsWithHeaderType)
            .setTracedRequestListener(mockTracedRequestListener)
            .build()

        // Then
        assertThat(interceptor.tracedHosts).isEqualTo(fakeTracedHostsWithHeaderType)
        assertThat(interceptor.traceContextInjection).isEqualTo(TraceContextInjection.All)
        assertThat(interceptor.sdkInstanceName).isNull()
        assertThat(
            interceptor.rumResourceAttributesProvider
        ).isInstanceOf(NoOpRumResourceAttributesProvider::class.java)
        assertThat(interceptor.tracedRequestListener).isSameAs(mockTracedRequestListener)
        assertThat(interceptor.traceSampler).isInstanceOf(RateBasedSampler::class.java)
        assertThat(interceptor.traceOrigin).isEqualTo(DatadogInterceptor.ORIGIN_RUM)
        assertThat(interceptor.localTracerFactory).isNotNull()
    }

    @Test
    fun `M set rumResourceAttributesProvider W build { setRumResourceAttributesProvider }`() {
        // When
        val interceptor = DatadogInterceptor.Builder(fakeTracedHostsWithHeaderType)
            .setRumResourceAttributesProvider(mockRumResourceAttributesProvider)
            .build()

        // Then
        assertThat(interceptor.tracedHosts).isEqualTo(fakeTracedHostsWithHeaderType)
        assertThat(interceptor.traceContextInjection).isEqualTo(TraceContextInjection.All)
        assertThat(interceptor.sdkInstanceName).isNull()
        assertThat(interceptor.rumResourceAttributesProvider).isSameAs(mockRumResourceAttributesProvider)
        assertThat(interceptor.tracedRequestListener).isInstanceOf(NoOpTracedRequestListener::class.java)
        assertThat(interceptor.traceSampler).isInstanceOf(RateBasedSampler::class.java)
        assertThat(interceptor.traceOrigin).isEqualTo(DatadogInterceptor.ORIGIN_RUM)
        assertThat(interceptor.localTracerFactory).isNotNull()
    }

    @Test
    fun `M set traceSampler W build { setTraceSampler }`() {
        // When
        val interceptor = DatadogInterceptor.Builder(fakeTracedHostsWithHeaderType)
            .setTraceSampler(mockSampler)
            .build()

        // Then
        assertThat(interceptor.tracedHosts).isEqualTo(fakeTracedHostsWithHeaderType)
        assertThat(interceptor.traceContextInjection).isEqualTo(TraceContextInjection.All)
        assertThat(interceptor.sdkInstanceName).isNull()
        assertThat(
            interceptor.rumResourceAttributesProvider
        ).isInstanceOf(NoOpRumResourceAttributesProvider::class.java)
        assertThat(interceptor.tracedRequestListener).isInstanceOf(NoOpTracedRequestListener::class.java)
        assertThat(interceptor.traceSampler).isSameAs(mockSampler)
        assertThat(interceptor.traceOrigin).isEqualTo(DatadogInterceptor.ORIGIN_RUM)
        assertThat(interceptor.localTracerFactory).isNotNull()
    }
}
