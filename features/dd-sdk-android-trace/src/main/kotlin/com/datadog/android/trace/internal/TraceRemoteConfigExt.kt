/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.trace.internal

import com.datadog.android.core.internal.net.DefaultFirstPartyHostHeaderTypeResolver
import com.datadog.android.core.internal.remote.model.RemoteConfiguration
import com.datadog.android.core.sampling.Sampler
import com.datadog.android.lint.InternalApi
import com.datadog.android.trace.DeterministicTraceSampler
import com.datadog.android.trace.TraceContextInjection
import com.datadog.android.trace.TracingHeaderType
import com.datadog.android.trace.api.span.DatadogSpan
import com.datadog.android.trace.internal.net.SessionRebasedSampler

/**
 * Applies the RC `sampleRate` to the given [current] sampler, preserving a
 * [SessionRebasedSampler] wrapper if one is present (used by [DatadogInterceptor] to maintain
 * `traceSampleRate × sessionSampleRate / 100` cross-product rebasing).
 */
@InternalApi
fun applyRcSampleRate(current: Sampler<DatadogSpan>, rcRate: Float): Sampler<DatadogSpan> {
    val newBase = DeterministicTraceSampler(rcRate)
    return if (current is SessionRebasedSampler) SessionRebasedSampler(newBase) else newBase
}

/**
 * Maps a remote [RemoteConfiguration.Trace.traceContextInjection] value to the SDK
 * [TraceContextInjection] enum, or returns `null` when the remote field is absent.
 */
@InternalApi
fun RemoteConfiguration.Trace.toSdkInjection(): TraceContextInjection? =
    when (traceContextInjection) {
        RemoteConfiguration.TraceContextInjection.ALL -> TraceContextInjection.ALL
        RemoteConfiguration.TraceContextInjection.SAMPLED -> TraceContextInjection.SAMPLED
        null -> null
    }

/**
 * Maps a remote [RemoteConfiguration.PropagatorType] to the SDK [TracingHeaderType].
 */
@InternalApi
fun RemoteConfiguration.PropagatorType.toSdkHeaderType(): TracingHeaderType =
    when (this) {
        RemoteConfiguration.PropagatorType.DATADOG -> TracingHeaderType.DATADOG
        RemoteConfiguration.PropagatorType.B3 -> TracingHeaderType.B3
        RemoteConfiguration.PropagatorType.B3MULTI -> TracingHeaderType.B3MULTI
        RemoteConfiguration.PropagatorType.TRACECONTEXT -> TracingHeaderType.TRACECONTEXT
    }

/**
 * Builds a new [DefaultFirstPartyHostHeaderTypeResolver] from the RC `tracedHosts`:
 * - `null` (absent) → returns `null` (caller keeps existing resolver unchanged)
 * - explicit empty list → returns an empty resolver (no first-party requests will be traced)
 * - non-empty list → returns a resolver with RC hosts replacing the developer's hosts
 *
 * Each host entry carries its own non-empty `propagatorTypes` list (required by the schema).
 */
@Suppress("ReturnCount")
@InternalApi
fun RemoteConfiguration.Trace.buildRcHostResolver(): DefaultFirstPartyHostHeaderTypeResolver? {
    val rcHosts = tracedHosts ?: return null
    if (rcHosts.isEmpty()) {
        return DefaultFirstPartyHostHeaderTypeResolver(emptyMap())
    }
    val newHosts = rcHosts.associate { tracedHost ->
        tracedHost.host to tracedHost.propagatorTypes.map { it.toSdkHeaderType() }.toSet()
    }
    // RC hosts come from the server and are already validated — pass directly to the resolver
    // which will lowercase them in its constructor.
    return DefaultFirstPartyHostHeaderTypeResolver(newHosts)
}
