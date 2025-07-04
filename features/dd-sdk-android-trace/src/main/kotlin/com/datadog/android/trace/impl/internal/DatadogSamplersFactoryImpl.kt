/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.trace.impl.internal

import com.datadog.android.trace.api.sampling.DatadogSamplersFactory
import com.datadog.android.trace.api.sampling.DatadogTracerSampler
import com.datadog.trace.common.sampling.AllSampler
import com.datadog.trace.common.sampling.DeterministicSampler
import com.datadog.trace.common.sampling.RateByServiceTraceSampler
import com.datadog.trace.common.sampling.Sampler

internal object DatadogSamplersFactoryImpl : DatadogSamplersFactory {

    override fun newAllSampler(): DatadogTracerSampler = wrapSampler(AllSampler())

    override fun newTraceSampler(rate: Double): DatadogTracerSampler = wrapSampler(
        DeterministicSampler.TraceSampler(rate)
    )

    override fun newSpanSampler(rate: Double): DatadogTracerSampler = wrapSampler(
        DeterministicSampler.SpanSampler(rate)
    )

    override fun newRateBasedSampler(rate: Double): DatadogTracerSampler = wrapSampler(
        RateByServiceTraceSampler()
    )

    private fun wrapSampler(sampler: Sampler): DatadogTracerSampler = DatadogTracerSamplerWrapper(sampler)
}
