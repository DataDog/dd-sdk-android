/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.trace.internal

import com.datadog.android.core.internal.remote.model.RemoteConfiguration
import com.datadog.android.trace.TraceContextInjection
import com.datadog.android.trace.TracingHeaderType
import com.datadog.android.utils.forge.Configurator
import fr.xgouchet.elmyr.Forge
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
internal class TraceRemoteConfigExtTest {

    // region toSdkInjection

    @Test
    fun `M return ALL W toSdkInjection { remote ALL }`() {
        // Given
        val trace = RemoteConfiguration.Trace(
            traceContextInjection = RemoteConfiguration.TraceContextInjection.ALL
        )

        // When / Then
        assertThat(trace.toSdkInjection()).isEqualTo(TraceContextInjection.ALL)
    }

    @Test
    fun `M return SAMPLED W toSdkInjection { remote SAMPLED }`() {
        // Given
        val trace = RemoteConfiguration.Trace(
            traceContextInjection = RemoteConfiguration.TraceContextInjection.SAMPLED
        )

        // When / Then
        assertThat(trace.toSdkInjection()).isEqualTo(TraceContextInjection.SAMPLED)
    }

    @Test
    fun `M return null W toSdkInjection { absent }`() {
        // Given
        val trace = RemoteConfiguration.Trace(traceContextInjection = null)

        // When / Then
        assertThat(trace.toSdkInjection()).isNull()
    }

    // endregion

    // region toSdkHeaderType

    @Test
    fun `M map all RemoteConfiguration PropagatorType values W toSdkHeaderType`() {
        assertThat(RemoteConfiguration.PropagatorType.DATADOG.toSdkHeaderType())
            .isEqualTo(TracingHeaderType.DATADOG)
        assertThat(RemoteConfiguration.PropagatorType.B3.toSdkHeaderType())
            .isEqualTo(TracingHeaderType.B3)
        assertThat(RemoteConfiguration.PropagatorType.B3MULTI.toSdkHeaderType())
            .isEqualTo(TracingHeaderType.B3MULTI)
        assertThat(RemoteConfiguration.PropagatorType.TRACECONTEXT.toSdkHeaderType())
            .isEqualTo(TracingHeaderType.TRACECONTEXT)
    }

    // endregion

    // region buildRcHostResolver

    @Test
    fun `M return null W buildRcHostResolver { tracedHosts absent }`() {
        // Given
        val trace = RemoteConfiguration.Trace(tracedHosts = null)

        // When / Then
        assertThat(trace.buildRcHostResolver()).isNull()
    }

    @Test
    fun `M return empty resolver W buildRcHostResolver { tracedHosts explicit empty list }`() {
        // Given
        val trace = RemoteConfiguration.Trace(tracedHosts = emptyList())

        // When
        val resolver = trace.buildRcHostResolver()

        // Then
        assertThat(resolver).isNotNull
        assertThat(resolver!!.isEmpty()).isTrue()
    }

    @Test
    fun `M return resolver with RC hosts W buildRcHostResolver { non-empty tracedHosts }`(
        forge: Forge
    ) {
        // Given
        val fakeRcHost = forge.aStringMatching("[a-z]+\\.[a-z]{2,3}")
        val trace = RemoteConfiguration.Trace(
            tracedHosts = listOf(
                RemoteConfiguration.TracedHost(
                    host = fakeRcHost,
                    propagatorTypes = listOf(RemoteConfiguration.PropagatorType.B3)
                )
            )
        )

        // When
        val resolver = trace.buildRcHostResolver()

        // Then
        assertThat(resolver).isNotNull
        assertThat(resolver!!.headerTypesForUrl("https://$fakeRcHost/path"))
            .containsExactly(TracingHeaderType.B3)
    }

    @Test
    fun `M use per-host propagatorTypes W buildRcHostResolver { host has propagatorTypes }`(
        forge: Forge
    ) {
        // Given
        val fakeHost = forge.aStringMatching("[a-z]+\\.[a-z]{2,3}")
        val trace = RemoteConfiguration.Trace(
            tracedHosts = listOf(
                RemoteConfiguration.TracedHost(
                    host = fakeHost,
                    propagatorTypes = listOf(
                        RemoteConfiguration.PropagatorType.B3MULTI,
                        RemoteConfiguration.PropagatorType.TRACECONTEXT
                    )
                )
            )
        )

        // When
        val resolver = trace.buildRcHostResolver()

        // Then
        assertThat(resolver!!.headerTypesForUrl("https://$fakeHost/path"))
            .containsExactlyInAnyOrder(TracingHeaderType.B3MULTI, TracingHeaderType.TRACECONTEXT)
    }

    // endregion
}
