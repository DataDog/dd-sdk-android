/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.trace.internal.net

import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.api.instrumentation.network.HttpRequestInfo
import com.datadog.android.core.sampling.Sampler
import com.datadog.android.trace.NetworkTracingInstrumentation.Companion.AGENT_PSR_ATTRIBUTE
import com.datadog.android.trace.NetworkTracingInstrumentation.Companion.ALL_IN_SAMPLE_RATE
import com.datadog.android.trace.NetworkTracingInstrumentation.Companion.SPAN_NAME
import com.datadog.android.trace.NetworkTracingInstrumentation.Companion.URL_QUERY_PARAMS_BLOCK_SEPARATOR
import com.datadog.android.trace.NetworkTracingInstrumentation.Companion.ZERO_SAMPLE_RATE
import com.datadog.android.trace.api.DatadogTracingConstants.PrioritySampling
import com.datadog.android.trace.api.DatadogTracingConstants.Tags
import com.datadog.android.trace.api.span.DatadogSpan
import com.datadog.android.trace.api.tracer.DatadogTracer
import com.datadog.android.trace.internal.DatadogTracingToolkit.propagationHelper
import java.util.Locale

internal val FeatureSdkCore?.isRumEnabled: Boolean
    get() = this?.getFeature(Feature.RUM_FEATURE_NAME) != null

internal fun DatadogSpan.applyPriority(isSampled: Boolean, traceSampler: Sampler<DatadogSpan>) {
    val samplingPriority = if (isSampled) {
        PrioritySampling.SAMPLER_KEEP
    } else {
        PrioritySampling.SAMPLER_DROP
    }

    val spanContext = context()
    if (spanContext.setSamplingPriority(samplingPriority)) {
        spanContext.setMetric(
            AGENT_PSR_ATTRIBUTE,
            (traceSampler.getSampleRate() ?: ZERO_SAMPLE_RATE) / ALL_IN_SAMPLE_RATE
        )
    }
}

internal fun DatadogSpan.sample(request: HttpRequestInfo, traceSampler: Sampler<DatadogSpan>): Boolean {
    val samplingPriority = samplingPriority
    return if (samplingPriority != null) {
        samplingPriority > 0
    } else {
        propagationHelper.extractSamplingDecision(request) ?: traceSampler.sample(this)
    }
}

internal fun DatadogSpan.finishRumAware(isSampled: Boolean, canSendSpan: Boolean) {
    if (canSendSpan) {
        if (isSampled) finish() else drop()
    } else {
        drop()
    }
}

internal fun DatadogTracer.buildSpan(
    request: HttpRequestInfo,
    networkInstrumentationName: String,
    traceOrigin: String?
): DatadogSpan {
    val parentContext = propagationHelper.extractParentContext(this, request)

    val span = buildSpan(SPAN_NAME.format(Locale.US, networkInstrumentationName))
        .withOrigin(traceOrigin)
        .withParentContext(parentContext)
        .start()

    span.resourceName = request.url.substringBefore(URL_QUERY_PARAMS_BLOCK_SEPARATOR)
    span.setTag(Tags.KEY_HTTP_URL, request.url)
    span.setTag(Tags.KEY_HTTP_METHOD, request.method)
    span.setTag(Tags.KEY_SPAN_KIND, Tags.VALUE_SPAN_KIND_CLIENT)

    return span
}
