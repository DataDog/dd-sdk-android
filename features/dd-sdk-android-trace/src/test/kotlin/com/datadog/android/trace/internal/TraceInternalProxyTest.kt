/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.trace.internal

import com.datadog.android.trace.ApmNetworkInstrumentationConfiguration
import com.datadog.android.trace.TracingHeaderType
import com.datadog.android.trace.api.span.DatadogSpan
import com.datadog.android.trace.api.tracer.DatadogTracer
import com.datadog.android.utils.forge.Configurator
import com.datadog.trace.core.propagation.HttpCodec
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
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class TraceInternalProxyTest {

    @Mock
    lateinit var mockCustomTracer: DatadogTracer

    private lateinit var fakeTracedHosts: Map<String, Set<TracingHeaderType>>

    @StringForgery
    lateinit var fakeInstrumentationName: String

    @BeforeEach
    fun `set up`(forge: Forge) {
        fakeTracedHosts = forge.aMap {
            aStringMatching("[a-z]+\\.[a-z]{2,3}") to forge.aList {
                aValueFrom(TracingHeaderType::class.java)
            }.toSet()
        }
    }

    @Test
    fun `M build TracingInstrumentation W createApmNetworkInstrumentation()`() {
        // Given
        val builder = ApmNetworkInstrumentationConfiguration(fakeTracedHosts)

        // When
        val result: ApmNetworkInstrumentation = _TraceInternalProxy.createApmNetworkInstrumentation(
            fakeInstrumentationName,
            builder
        )

        // Then
        assertThat(result).isNotNull
        assertThat(result.sdkInstanceName).isNull()
        assertThat(result.traceOrigin).isNull()
    }

    @Test
    fun `M build TracingInstrumentation with configured values W createApmNetworkInstrumentation()`(
        @StringForgery fakeSdkInstanceName: String,
        @StringForgery fakeTraceOrigin: String
    ) {
        // Given
        val builder = ApmNetworkInstrumentationConfiguration(fakeTracedHosts)
            .setSdkInstanceName(fakeSdkInstanceName)
            .setTraceOrigin(fakeTraceOrigin)

        // When
        val result: ApmNetworkInstrumentation =
            _TraceInternalProxy.createApmNetworkInstrumentation(
                fakeInstrumentationName,
                builder
            )

        // Then
        assertThat(result).isNotNull
        assertThat(result.sdkInstanceName).isEqualTo(fakeSdkInstanceName)
        assertThat(result.traceOrigin).isEqualTo(fakeTraceOrigin)
    }

    @Test
    fun `M return false W isDefaultTracer() {custom tracer}`() {
        assertThat(_TraceInternalProxy.isDefaultTracer(mockCustomTracer)).isFalse()
    }

    @Test
    fun `M return true W isDefaultTracer() {SDK DatadogTracerAdapter}`() {
        val sdkTracer: DatadogTracer = mock<DatadogTracerAdapter>()

        assertThat(_TraceInternalProxy.isDefaultTracer(sdkTracer)).isTrue()
    }

    @Test
    fun `M remove retained anonymous id W removeRetainedUserAnonymousIdBaggage() {no anonymous id tag}`() {
        // Given
        val span = mock<DatadogSpan>()

        // When
        val result = _TraceInternalProxy.removeRetainedUserAnonymousIdBaggage(
            span,
            "user.id=userId,user.anonymous_id=anonymousId"
        )

        // Then
        assertThat(result).isEqualTo("user.id=userId")
    }

    @Test
    fun `M return null W removeRetainedUserAnonymousIdBaggage() {only anonymous id, no tag}`() {
        // Given
        val span = mock<DatadogSpan>()

        // When
        val result = _TraceInternalProxy.removeRetainedUserAnonymousIdBaggage(span, "user.anonymous_id=anonymousId")

        // Then
        assertThat(result).isNull()
    }

    @Test
    fun `M keep header W removeRetainedUserAnonymousIdBaggage() {anonymous id tag set}`(
        @StringForgery fakeAnonymousId: String
    ) {
        // Given
        val span = mock<DatadogSpan>()
        whenever(span.getTag(HttpCodec.RUM_KEY_USER_ANONYMOUS_ID)).thenReturn(fakeAnonymousId)
        val header = "user.id=userId,user.anonymous_id=anonymousId"

        // When
        val result = _TraceInternalProxy.removeRetainedUserAnonymousIdBaggage(span, header)

        // Then
        assertThat(result).isSameAs(header)
    }

    @Test
    fun `M keep header W removeRetainedUserAnonymousIdBaggage() {no anonymous id entry}`() {
        // Given
        val span = mock<DatadogSpan>()
        val header = "user.id=userId,session.id=sessionId"

        // When
        val result = _TraceInternalProxy.removeRetainedUserAnonymousIdBaggage(span, header)

        // Then
        assertThat(result).isSameAs(header)
    }
}
