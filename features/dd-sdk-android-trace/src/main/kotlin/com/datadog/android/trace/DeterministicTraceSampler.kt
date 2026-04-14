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
 * A [com.datadog.android.core.sampling.DeterministicSampler] using the TraceID of a Span to compute the sampling decision.
 *
 * The sampling threshold is automatically rebased against the RUM session sample rate when it is
 * available so that the combined effective sampling probability reflects:
 *
 * ```
 * rebasedTraceSampleRate = sessionSampleRate * traceSampleRate / 100
 * ```
 *
 * Note: overriding [getSampleRate] is not a supported extension point for per-span sampling
 * behavior.
 *
 * @param sampleRateProvider Provider for the trace sample rate. Called each time the sampling
 * decision needs to be made. Values must be in the range [0;100].
 * @param sessionSampleRateProvider Provider for the RUM session sample rate used to rebase the
 * trace sample rate. Defaults to 100 (no rebasing).
 */
// TODO RUM-13454 -> Make this class internal in V4 (RUM-15590)
open class DeterministicTraceSampler private constructor(
    private val sampleRateProvider: () -> Float,
    private val sessionSampleRateProvider: () -> Float
) : DeterministicSampler<DatadogSpan>(
    SpanSamplingIdProvider::provideId,
    sampleRateProvider
) {

    /**
     * Creates a new instance of [DeterministicTraceSampler] with the given sample rate provider.
     *
     * @param sampleRateProvider Provider for the sample rate value.
     */
    constructor(
        sampleRateProvider: () -> Float
    ) : this(sampleRateProvider, { DeterministicSampler.SAMPLE_ALL_RATE })

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

    /**
     * Internal constructor used by [com.datadog.android.trace.ApmNetworkInstrumentationConfiguration]
     * to wire the RUM session sample rate provider at instrumentation setup time.
     */
    internal constructor(
        @FloatRange(from = 0.0, to = 100.0) sampleRate: Float,
        sessionSampleRateProvider: () -> Float
    ) : this({ sampleRate }, sessionSampleRateProvider)

    /**
     * Returns the effective sample rate, rebased against the RUM session sample rate when it is
     * available:
     *
     * ```
     * effectiveRate = traceSampleRate * sessionSampleRate / 100
     * ```
     *
     * Falls back to the raw trace sample rate when no session sample rate is available
     * (i.e. [sessionSampleRateProvider] returns 100).
     */
    override fun getSampleRate(): Float {
        val traceSampleRate = super.getSampleRate()
        val sessionSampleRate = sessionSampleRateProvider()
        return if (sessionSampleRate >= 0f && sessionSampleRate < DeterministicSampler.SAMPLE_ALL_RATE) {
            val rebased = traceSampleRate * sessionSampleRate / DeterministicSampler.SAMPLE_ALL_RATE
            rebased.coerceAtMost(DeterministicSampler.SAMPLE_ALL_RATE)
        } else {
            traceSampleRate
        }
    }

    /** @inheritDoc */
    // TODO RUM-13454 -> Remove the @Suppress when getSampleRate(DatadogSpan) is removed in V4 (RUM-15590)
    @Suppress("DEPRECATION")
    override fun sample(item: DatadogSpan): Boolean =
        computeSamplingDecision(getSampleRate(item), SpanSamplingIdProvider.provideId(item))

    /**
     * Returns the effective sample rate for [item], applying cross-product rebasing when a RUM
     * session rate is available.
     *
     * When this sampler was created via [ApmNetworkInstrumentationConfiguration] the session rate
     * is read from the injected [sessionSampleRateProvider]. For samplers created via the public
     * constructors (no provider) the rate is read from the `session_sample_rate` span tag written
     * by [com.datadog.android.trace.internal.RumContextPropagator], providing compatibility with
     * [com.datadog.android.okhttp.trace.TracingInterceptor] and related legacy integrations.
     */
    // TODO RUM-13454 -> Remove this in V4 when deprecating legacy paths (RUM-15590)
    @Deprecated(
        "Use ApmNetworkInstrumentationConfiguration for session-aware sampling. Will be removed in v4.",
        level = DeprecationLevel.WARNING
    )
    fun getSampleRate(item: DatadogSpan): Float {
        // Compatibility path for integrations creating this sampler via public constructors
        // (no sessionSampleRateProvider). In that case the session sample rate is propagated on
        // the span context as `session_sample_rate` and must be used for rebasing.
        val providerRate = sessionSampleRateProvider()
        val providerRateIsValid = providerRate >= 0f && providerRate < DeterministicSampler.SAMPLE_ALL_RATE
        val sessionSampleRate = when {
            providerRateIsValid -> providerRate
            else -> (item.context().tags[LogAttributes.RUM_SESSION_SAMPLE_RATE] as? Number)?.toFloat()
        }

        return if (sessionSampleRate != null &&
            sessionSampleRate >= 0f &&
            sessionSampleRate < DeterministicSampler.SAMPLE_ALL_RATE
        ) {
            val rebased = super.getSampleRate() * sessionSampleRate / DeterministicSampler.SAMPLE_ALL_RATE
            if (rebased > DeterministicSampler.SAMPLE_ALL_RATE) DeterministicSampler.SAMPLE_ALL_RATE else rebased
        } else {
            super.getSampleRate()
        }
    }
}
