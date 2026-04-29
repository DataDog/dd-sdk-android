/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.trace.internal.net

import com.datadog.android.lint.InternalApi
import com.datadog.android.trace.api.span.DatadogSpan

/**
 * A sampler that can provide a per-span sample rate.
 *
 * This extends the concept of [com.datadog.android.core.sampling.Sampler.getSampleRate] (no-arg)
 * with a span-aware variant. It is used when the effective sample rate depends on span-level
 * context (e.g., the RUM session sample rate tag for cross-product sampling rebasing).
 */
@InternalApi
interface SpanAwareSampler {

    /**
     * Returns the effective sample rate for the given [span].
     *
     * @param span the span for which to compute the sample rate.
     * @return the effective sample rate, or null if not available.
     */
    fun getSampleRate(span: DatadogSpan): Float?
}
