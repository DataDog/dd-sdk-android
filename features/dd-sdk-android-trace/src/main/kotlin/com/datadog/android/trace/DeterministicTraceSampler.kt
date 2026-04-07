/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.trace

import androidx.annotation.FloatRange
import com.datadog.android.core.sampling.DeterministicSampler
import com.datadog.android.log.LogAttributes
import com.datadog.android.trace.api.span.DatadogSpan
import com.datadog.android.trace.internal.RumContextPropagator
import com.datadog.android.trace.internal.net.SpanSamplingIdProvider

/**
 * A [com.datadog.android.core.sampling.DeterministicSampler] using the TraceID of a Span to compute the sampling decision.
 *
 * When a span is linked to an active RUM session (i.e. the span carries a `rum.session.id` tag),
 * the sampling threshold is automatically rebased against the RUM session sample rate so that
 * the combined effective sampling probability reflects the configured trace sample rate applied
 * only to the sessions already tracked by RUM:
 *
 * ```
 * rebasedTraceSampleRate = sessionSampleRate * traceSampleRate / 100
 * ```
 *
 * @param sampleRateProvider Provider for the sample rate value which will be called each time
 * the sampling decision needs to be made. All the values should be in the range [0;100].
 */
open class DeterministicTraceSampler(
    sampleRateProvider: () -> Float
) : DeterministicSampler<DatadogSpan>(
    SpanSamplingIdProvider::provideId,
    sampleRateProvider
) {

    /**
     * Creates a new instance of [DeterministicSampler] with the given sample rate.
     *
     * @param sampleRate Sample rate to use.
     */
    constructor(
        @FloatRange(from = 0.0, to = 100.0) sampleRate: Float
    ) : this({ sampleRate })

    /**
     * Creates a new instance of [DeterministicSampler] with the given sample rate.
     *
     * @param sampleRate Sample rate to use.
     */
    constructor(
        @FloatRange(from = 0.0, to = 100.0) sampleRate: Double
    ) : this(sampleRate.toFloat())

    /** @inheritDoc */
    override fun sample(item: DatadogSpan): Boolean {
        val sampleRate = rebasedSampleRate(item)
        return when {
            sampleRate >= DeterministicSampler.SAMPLE_ALL_RATE -> true
            sampleRate <= 0f -> false
            else -> {
                val hash = SpanSamplingIdProvider.provideId(item) * DeterministicSampler.SAMPLER_HASHER
                val threshold = (
                    DeterministicSampler.MAX_ID.toDouble() * sampleRate / DeterministicSampler.SAMPLE_ALL_RATE
                    ).toULong()
                hash < threshold
            }
        }
    }

    private fun rebasedSampleRate(item: DatadogSpan): Float {
        val traceSampleRate = getSampleRate()
        val sessionId = item.context().tags[LogAttributes.RUM_SESSION_ID] as? String
        val sessionSampleRate = item.context().tags[RumContextPropagator.SESSION_SAMPLE_RATE_KEY] as? Number

        return if (sessionId != null && sessionSampleRate != null) {
            (traceSampleRate * sessionSampleRate.toFloat() / DeterministicSampler.SAMPLE_ALL_RATE)
                .coerceAtMost(DeterministicSampler.SAMPLE_ALL_RATE)
        } else {
            traceSampleRate
        }
    }
}
