/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.trace.api.sampling

interface DatadogSamplersFactory {
    /**
     * Creates a new instance of a [DatadogTracerSampler] that uses an [AllSampler].
     *
     * @return a [DatadogTracerSampler] that always samples all spans.
     */
    fun newAllSampler(): DatadogTracerSampler

    /**
     * Creates a new custom trace sampler with the specified sampling rate.
     * Uses trace-id as a sampling id
     *
     * @param rate The sampling rate, where 0.0 means no spans are sampled, and 1.0 means all spans are sampled.
     * @return An instance of [DatadogTracerSampler] configured with the provided sampling rate.
     */
    fun newTraceSampler(rate: Double): DatadogTracerSampler

    /**
     * Creates a new custom trace sampler with the specified sampling rate.
     * Uses span-id as a sampling id
     *
     * @param rate The sampling rate, where 0.0 means no spans are sampled, and 1.0 means all spans are sampled.
     * @return An instance of [DatadogTracerSampler] configured with the provided sampling rate.
     */
    fun newSpanSampler(rate: Double): DatadogTracerSampler

    /**
     * Creates a new instance of a rate-based sampler to determine whether or not
     * a span should be traced.
     *
     * @param rate The sampling rate to be used by the sampler. This value is used to control
     * the probability of spans being traced.
     * @return A rate-based implementation of a `DatadogTracerSampler`.
     */
    fun newRateBasedSampler(rate: Double): DatadogTracerSampler
}
