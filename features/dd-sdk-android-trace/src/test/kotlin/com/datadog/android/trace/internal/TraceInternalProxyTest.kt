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
import com.datadog.trace.core.propagation.Baggage
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
    fun `M remove anonymous id members W removeRetainedUserAnonymousIdBaggage() {no anonymous id tag}`() {
        // Given
        val span = mock<DatadogSpan>()

        // When
        val result = _TraceInternalProxy.removeRetainedUserAnonymousIdBaggage(
            span,
            listOf("user.id=user+id;prop=value,user.anonymous_id=old", " user.anonymous_id ;p=1 ,custom=")
        )

        // Then
        assertThat(result).containsExactly("user.id=user+id;prop=value", "custom=")
    }

    @Test
    fun `M remove anonymous id W removeRetainedUserAnonymousIdBaggage() {empty values}`() {
        // Given
        val span = mock<DatadogSpan>()

        // When
        val result = _TraceInternalProxy.removeRetainedUserAnonymousIdBaggage(
            span,
            listOf("custom=,user.anonymous_id=old")
        )

        // Then
        assertThat(result).containsExactly("custom=")
    }

    @Test
    fun `M remove anonymous id W removeRetainedUserAnonymousIdBaggage() {malformed members}`() {
        // Given
        val span = mock<DatadogSpan>()

        // When
        val result = _TraceInternalProxy.removeRetainedUserAnonymousIdBaggage(
            span,
            listOf("not a member,,=x,user.anonymous_id=old,k=%zz")
        )

        // Then
        assertThat(result).containsExactly("not a member,,=x,k=%zz")
    }

    @Test
    fun `M keep existing raw members W mergeBaggage() {empty value in existing baggage}`() {
        // When
        val result = _TraceInternalProxy.mergeBaggage("custom=,feature=on", "user.anonymous_id=abc")

        // Then
        assertThat(result).isEqualTo("custom=,feature=on,user.anonymous_id=abc")
    }

    @Test
    fun `M not restore anonymous id key W mergeBaggage() {double-encoded key retained, merged twice}`() {
        // Given
        val span = mock<DatadogSpan>()
        val retained = "user%252Eanonymous_id=old,custom=a"
        val cleaned = _TraceInternalProxy.removeRetainedUserAnonymousIdBaggage(span, listOf(retained))
            ?.single() ?: retained

        // When
        val once = _TraceInternalProxy.mergeBaggage(cleaned, "session.id=s")
        val twice = _TraceInternalProxy.mergeBaggage(once, "session.id=s")

        // Then
        assertThat(Baggage.from(twice).asMap()).doesNotContainKey(HttpCodec.BAGGAGE_USER_ANONYMOUS_ID)
        assertThat(twice).isEqualTo("user%252Eanonymous_id=old,custom=a,session.id=s")
    }

    @Test
    fun `M remove anonymous id W removeRetainedUserAnonymousIdBaggage() {percent-encoded key}`() {
        // Given
        val span = mock<DatadogSpan>()

        // When
        val result = _TraceInternalProxy.removeRetainedUserAnonymousIdBaggage(
            span,
            listOf("user%2Eanonymous%5Fid=old,user.id=user%2Bid", "user%2eanonymous_id=old2")
        )

        // Then
        assertThat(result).containsExactly("user.id=user%2Bid")
    }

    @Test
    fun `M drop emptied values W removeRetainedUserAnonymousIdBaggage() {only anonymous id}`() {
        // Given
        val span = mock<DatadogSpan>()

        // When
        val result = _TraceInternalProxy.removeRetainedUserAnonymousIdBaggage(
            span,
            listOf("user.anonymous_id=old", "first=one")
        )

        // Then
        assertThat(result).containsExactly("first=one")
    }

    @Test
    fun `M return null W removeRetainedUserAnonymousIdBaggage() {anonymous id tag set}`(
        @StringForgery fakeAnonymousId: String
    ) {
        // Given
        val span = mock<DatadogSpan>()
        whenever(span.getTag(HttpCodec.RUM_KEY_USER_ANONYMOUS_ID)).thenReturn(fakeAnonymousId)

        // When
        val result = _TraceInternalProxy.removeRetainedUserAnonymousIdBaggage(span, listOf("user.anonymous_id=old"))

        // Then
        assertThat(result).isNull()
    }

    @Test
    fun `M return null W removeRetainedUserAnonymousIdBaggage() {no anonymous id member}`() {
        // Given
        val span = mock<DatadogSpan>()

        // When
        val result = _TraceInternalProxy.removeRetainedUserAnonymousIdBaggage(
            span,
            listOf("user.id=userId,user.anonymous_idx=other")
        )

        // Then
        assertThat(result).isNull()
    }
}
