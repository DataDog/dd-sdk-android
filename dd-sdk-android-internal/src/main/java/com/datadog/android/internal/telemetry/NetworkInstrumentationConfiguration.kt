/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.internal.telemetry

import java.util.concurrent.atomic.AtomicReference

/** Network configuration retained for the lifetime of one SDK instance, independently of RUM. */
class NetworkInstrumentationConfiguration {
    private val state = AtomicReference(
        Snapshot(
            trackNetworkRequests = false,
            useFirstPartyHosts = false,
            resourceHeadersTracking = null
        )
    )

    /** Returns a consistent view that is unaffected by subsequent setup signals. */
    fun snapshot(): Snapshot = state.get()

    /** Retains setup signals without sampling or depending on a feature being enabled. */
    fun update(event: InternalTelemetryEvent.ConfigurationUpdate) {
        while (true) {
            val current = state.get()
            val updated = Snapshot(
                trackNetworkRequests = current.trackNetworkRequests ||
                    event is InternalTelemetryEvent.InterceptorInstantiated ||
                    event is InternalTelemetryEvent.NetworkInstrumentationConfigured,
                useFirstPartyHosts = current.useFirstPartyHosts ||
                    event is InternalTelemetryEvent.FirstPartyHostsConfigured,
                resourceHeadersTracking = when (event) {
                    is InternalTelemetryEvent.NetworkInstrumentationConfigured ->
                        event.resourceHeadersTracking ?: current.resourceHeadersTracking
                    is InternalTelemetryEvent.ResourceHeadersTrackingConfigured -> event.mode
                    else -> current.resourceHeadersTracking
                }
            )
            if (state.compareAndSet(current, updated)) return
        }
    }

    /** Immutable network setup used to build one configuration telemetry event. */
    class Snapshot internal constructor(
        /** Whether RUM network instrumentation has been configured. */
        val trackNetworkRequests: Boolean,
        /** Whether an integration has configured first-party hosts. */
        val useFirstPartyHosts: Boolean,
        /** Header capture mode reported by network instrumentation. */
        val resourceHeadersTracking: InternalTelemetryEvent.ResourceHeadersTrackingConfigured.Mode?
    )
}
