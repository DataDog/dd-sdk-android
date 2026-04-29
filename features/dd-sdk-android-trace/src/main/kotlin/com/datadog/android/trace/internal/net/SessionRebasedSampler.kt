/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.trace.internal.net

import com.datadog.android.core.sampling.Sampler
import com.datadog.android.internal.sampling.DeterministicSampling
import com.datadog.android.lint.InternalApi
import com.datadog.android.log.LogAttributes
import com.datadog.android.trace.DeterministicTraceSampler
import com.datadog.android.trace.api.span.DatadogSpan

/**
 * A [Sampler] decorator that rebases the trace sample rate against the RUM session sample rate.
 *
 * When the delegate is a [DeterministicTraceSampler] and the span carries a
 * [LogAttributes.RUM_SESSION_SAMPLE_RATE] tag (injected by [com.datadog.android.trace.internal.RumContextPropagator]),
 * the effective sample rate becomes `traceSampleRate * sessionSampleRate / 100`.
 * This ensures that the combined probability of a trace being sampled within a sampled RUM session
 * is correctly represented.
 *
 * For custom (non-[DeterministicTraceSampler]) delegates, the decorator passes through
 * to the delegate without rebasing.
 *
 * @param delegate the underlying sampler to wrap.
 */
@InternalApi
class SessionRebasedSampler(
    private val delegate: Sampler<DatadogSpan>
) : Sampler<DatadogSpan>, SpanAwareSampler {

    override fun sample(item: DatadogSpan): Boolean {
        if (delegate !is DeterministicTraceSampler) return delegate.sample(item)
        val rawRate = delegate.getSampleRate()
        val effectiveRate = computeEffectiveRate(item, rawRate)
        return if (effectiveRate == rawRate) {
            delegate.sample(item)
        } else {
            DeterministicTraceSampler(effectiveRate).sample(item)
        }
    }

    override fun getSampleRate(): Float? = delegate.getSampleRate()

    override fun getSampleRate(span: DatadogSpan): Float? {
        if (delegate !is DeterministicTraceSampler) return delegate.getSampleRate()
        val rawRate = delegate.getSampleRate()
        return computeEffectiveRate(span, rawRate)
    }

    private fun computeEffectiveRate(item: DatadogSpan, rawRate: Float): Float {
        val sessionRate = (item.context().tags[LogAttributes.RUM_SESSION_SAMPLE_RATE] as? Number)?.toFloat()
        return if (sessionRate != null) {
            DeterministicSampling.combinedSampleRate(sessionRate, rawRate)
        } else {
            rawRate
        }
    }
}
