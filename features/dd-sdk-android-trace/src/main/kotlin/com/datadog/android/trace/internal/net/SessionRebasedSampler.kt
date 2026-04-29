/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.trace.internal.net

import com.datadog.android.core.sampling.Sampler
import com.datadog.android.internal.sampling.DeterministicSampling
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
internal class SessionRebasedSampler(
    private val delegate: Sampler<DatadogSpan>
) : Sampler<DatadogSpan> {

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

    /**
     * Returns the effective (rebased) sample rate for the given span.
     * If the span has a `session_sample_rate` tag and the delegate is a [DeterministicTraceSampler],
     * the rate is rebased. Otherwise, the raw delegate rate is returned.
     */
    internal fun getEffectiveSampleRate(item: DatadogSpan): Float {
        val rawRate = delegate.getSampleRate() ?: SAMPLE_ALL_RATE
        return if (delegate is DeterministicTraceSampler) {
            computeEffectiveRate(item, rawRate)
        } else {
            rawRate
        }
    }

    private fun computeEffectiveRate(item: DatadogSpan, rawRate: Float): Float {
        val sessionRate = (item.context().tags[LogAttributes.RUM_SESSION_SAMPLE_RATE] as? Number)?.toFloat()
        return if (sessionRate != null) {
            DeterministicSampling.combinedSampleRate(sessionRate, rawRate)
        } else {
            rawRate
        }
    }

    internal companion object {
        internal const val SAMPLE_ALL_RATE = 100f
    }
}
