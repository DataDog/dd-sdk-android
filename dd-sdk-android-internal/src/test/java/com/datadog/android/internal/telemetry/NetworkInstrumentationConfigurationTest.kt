/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.internal.telemetry

import com.datadog.android.internal.telemetry.InternalTelemetryEvent.ResourceHeadersTrackingConfigured.Mode
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

internal class NetworkInstrumentationConfigurationTest {
    @ParameterizedTest
    @EnumSource(Mode::class)
    fun `M publish complete setup W update() {previous snapshot retained}`(mode: Mode) {
        // Given
        val testedConfiguration = NetworkInstrumentationConfiguration()
        val previousSnapshot = testedConfiguration.snapshot()

        // When
        testedConfiguration.update(InternalTelemetryEvent.NetworkInstrumentationConfigured(mode))
        val currentSnapshot = testedConfiguration.snapshot()

        // Then
        assertThat(currentSnapshot.trackNetworkRequests).isTrue()
        assertThat(currentSnapshot.resourceHeadersTracking).isEqualTo(mode)
        assertThat(previousSnapshot.trackNetworkRequests).isFalse()
        assertThat(previousSnapshot.resourceHeadersTracking).isNull()
    }

    @ParameterizedTest
    @EnumSource(Mode::class)
    fun `M preserve other configuration W update() {hosts and integration without headers}`(mode: Mode) {
        // Given
        val testedConfiguration = NetworkInstrumentationConfiguration()
        testedConfiguration.update(InternalTelemetryEvent.NetworkInstrumentationConfigured(mode))

        // When
        testedConfiguration.update(InternalTelemetryEvent.FirstPartyHostsConfigured)
        testedConfiguration.update(InternalTelemetryEvent.NetworkInstrumentationConfigured(null))
        val snapshot = testedConfiguration.snapshot()

        // Then
        assertThat(snapshot.trackNetworkRequests).isTrue()
        assertThat(snapshot.useFirstPartyHosts).isTrue()
        assertThat(snapshot.resourceHeadersTracking).isEqualTo(mode)
    }
}
