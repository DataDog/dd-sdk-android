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
 * Maps a remote [RemoteConfiguration.TracingHeaderType] to the SDK [TracingHeaderType].
 */
@InternalApi
fun RemoteConfiguration.TracingHeaderType.toSdkHeaderType(): TracingHeaderType =
    when (this) {
        RemoteConfiguration.TracingHeaderType.DATADOG -> TracingHeaderType.DATADOG
        RemoteConfiguration.TracingHeaderType.B3 -> TracingHeaderType.B3
        RemoteConfiguration.TracingHeaderType.B3MULTI -> TracingHeaderType.B3MULTI
        RemoteConfiguration.TracingHeaderType.TRACECONTEXT -> TracingHeaderType.TRACECONTEXT
    }

/**
 * Builds a new [DefaultFirstPartyHostHeaderTypeResolver] from the RC `tracedHosts`, mirroring
 * iOS PR #3047 semantics:
 * - `null` (absent) → returns `null` (caller keeps existing resolver unchanged)
 * - explicit empty list → returns an empty resolver (no first-party requests will be traced)
 * - non-empty list → returns a resolver with RC hosts replacing the developer's hosts
 *
 * For each host the header types are resolved in order:
 * 1. Per-host `propagatorTypes` from the RC payload
 * 2. Global `tracingHeaderTypes` from the RC payload
 * 3. SDK default `{DATADOG, TRACECONTEXT}`
 */
@Suppress("ReturnCount")
@InternalApi
fun RemoteConfiguration.Trace.buildRcHostResolver(): DefaultFirstPartyHostHeaderTypeResolver? {
    val rcHosts = tracedHosts ?: return null
    if (rcHosts.isEmpty()) {
        return DefaultFirstPartyHostHeaderTypeResolver(emptyMap())
    }
    // null (absent) → SDK default; [] (explicit empty) → no global fallback types
    val globalTypes = tracingHeaderTypes
        ?.map { it.toSdkHeaderType() }
        ?.toSet()
        ?: setOf(TracingHeaderType.DATADOG, TracingHeaderType.TRACECONTEXT)

    val newHosts = rcHosts.associate { tracedHost ->
        // null (absent) → fall back to globalTypes; [] (explicit empty) → no headers for this host
        val types = tracedHost.propagatorTypes
            ?.map { it.toSdkHeaderType() }
            ?.toSet()
            ?: globalTypes
        tracedHost.host to types
    }
    // RC hosts come from the server and are already validated — pass directly to the resolver
    // which will lowercase them in its constructor.
    return DefaultFirstPartyHostHeaderTypeResolver(newHosts)
}
