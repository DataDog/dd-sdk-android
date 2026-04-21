/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.trace

import androidx.annotation.FloatRange
import com.datadog.android.core.sampling.DeterministicSampler
import com.datadog.android.internal.sampling.computeSamplingDecision
import com.datadog.android.log.LogAttributes
import com.datadog.android.trace.api.span.DatadogSpan
import com.datadog.android.trace.internal.net.SpanSamplingIdProvider

/**
 * A [com.datadog.android.core.sampling.DeterministicSampler] using the TraceID of a Span to compute the sampling
 * decision.
 *
 * When a span carries a RUM session context (written by
 * [com.datadog.android.trace.internal.RumContextPropagator]), the sampling threshold is
 * automatically rebased against the RUM session sample rate so that the combined effective
 * sampling probability reflects:
 *
 * ```
 * effectiveRate = traceSampleRate * sessionSampleRate / 100
 * ```
 *
 * Spans without a `session_sample_rate` tag are sampled at the raw trace sample rate.
 *
 * @param sampleRateProvider Provider for the trace sample rate. Called each time the sampling
 * decision needs to be made. Values must be in the range [0;100].
 */
// TODO RUM-13454 -> Make this class internal in V4 (RUM-15590)
open class DeterministicTraceSampler(
    sampleRateProvider: () -> Float
) : DeterministicSampler<DatadogSpan>(
    SpanSamplingIdProvider::provideId,
    sampleRateProvider
) {

    /**
     * Creates a new instance of [DeterministicTraceSampler] with the given sample rate.
     *
     * @param sampleRate Sample rate to use.
     */
    constructor(
        @FloatRange(from = 0.0, to = 100.0) sampleRate: Float
    ) : this({ sampleRate })

    /**
     * Creates a new instance of [DeterministicTraceSampler] with the given sample rate.
     *
     * @param sampleRate Sample rate to use.
     */
    constructor(
        @FloatRange(from = 0.0, to = 100.0) sampleRate: Double
    ) : this(sampleRate.toFloat())

    /** @inheritDoc */
    // TODO RUM-13454 -> Remove the @Suppress when getSampleRate(DatadogSpan) is removed in V4 (RUM-15590)
    @Suppress("DEPRECATION")
    override fun sample(item: DatadogSpan): Boolean =
        computeSamplingDecision(getSampleRate(item), SpanSamplingIdProvider.provideId(item))

    /**
     * Returns the effective sample rate for [item], applying cross-product rebasing when the
     * span carries a `session_sample_rate` tag (written by
     * [com.datadog.android.trace.internal.RumContextPropagator]).
     *
     * When no tag is present the raw trace sample rate is returned unchanged.
     */
    // TODO RUM-13454 -> Remove this in V4 when deprecating legacy paths (RUM-15590)
    @Deprecated(
        "Will be removed in v4.",
        level = DeprecationLevel.WARNING
    )
    fun getSampleRate(item: DatadogSpan): Float {
        return resolveEffectiveSampleRate(item)
    }

    // region private

    private fun resolveEffectiveSampleRate(item: DatadogSpan): Float {
        val traceSampleRate = super.getSampleRate()
        val sessionSampleRate = resolveSessionSampleRate(item)
        return if (sessionSampleRate != null &&
            sessionSampleRate >= 0f &&
            sessionSampleRate < DeterministicSampler.SAMPLE_ALL_RATE
        ) {
            (traceSampleRate * sessionSampleRate / DeterministicSampler.SAMPLE_ALL_RATE)
                .coerceAtMost(DeterministicSampler.SAMPLE_ALL_RATE)
        } else {
            traceSampleRate
        }
    }

    private fun resolveSessionSampleRate(item: DatadogSpan): Float? {
        return (item.context().tags[LogAttributes.RUM_SESSION_SAMPLE_RATE] as? Number)?.toFloat()
    }

    // endregion
}
