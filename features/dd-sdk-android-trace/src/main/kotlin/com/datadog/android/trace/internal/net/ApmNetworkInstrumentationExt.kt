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
import com.datadog.android.lint.InternalApi
import com.datadog.android.trace.api.DatadogTracingConstants.PrioritySampling
import com.datadog.android.trace.api.DatadogTracingConstants.Tags
import com.datadog.android.trace.api.span.DatadogSpan
import com.datadog.android.trace.api.span.DatadogSpanContext
import com.datadog.android.trace.api.tracer.DatadogTracer
import com.datadog.android.trace.internal.ApmNetworkInstrumentation.Companion.AGENT_PSR_ATTRIBUTE
import com.datadog.android.trace.internal.ApmNetworkInstrumentation.Companion.ALL_IN_SAMPLE_RATE
import com.datadog.android.trace.internal.ApmNetworkInstrumentation.Companion.SPAN_NAME
import com.datadog.android.trace.internal.ApmNetworkInstrumentation.Companion.URL_QUERY_PARAMS_BLOCK_SEPARATOR
import com.datadog.android.trace.internal.ApmNetworkInstrumentation.Companion.ZERO_SAMPLE_RATE
import com.datadog.android.trace.internal._TraceInternalProxy
import com.datadog.android.trace.internal._TraceInternalProxy.propagationHelper
import java.util.Locale

internal val FeatureSdkCore?.isRumEnabled: Boolean
    get() = this?.getFeature(Feature.RUM_FEATURE_NAME) != null

/**
 * Returns the effective sample rate for the given [span].
 * If the sampler implements [SpanAwareSampler], the per-span rate is returned.
 * Otherwise, the sampler's static rate is returned.
 */
@InternalApi
fun Sampler<DatadogSpan>.effectiveSampleRate(span: DatadogSpan): Float? {
    return when (this) {
        is SpanAwareSampler -> getSampleRate(span)
        else -> getSampleRate()
    }
}

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
            (traceSampler.effectiveSampleRate(this) ?: ZERO_SAMPLE_RATE) / ALL_IN_SAMPLE_RATE
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
    if (canSendSpan && isSampled) {
        finish()
    } else {
        drop()
    }
}

internal fun DatadogTracer.buildSpan(
    request: HttpRequestInfo,
    networkInstrumentationName: String,
    traceOrigin: String?,
    ignoreDroppedParent: Boolean
): DatadogSpan {
    val parentContext = propagationHelper.extractParentContext(this, request)
    val shouldIgnoreParent = ignoreDroppedParent && isParentDropped(this, parentContext)

    val builder = buildSpan(SPAN_NAME.format(Locale.US, networkInstrumentationName))
        .withOrigin(traceOrigin)

    if (shouldIgnoreParent) {
        builder.ignoreActiveSpan()
    } else {
        builder.withParentContext(parentContext)
    }

    val span = builder.start()

    span.resourceName = request.url.substringBefore(URL_QUERY_PARAMS_BLOCK_SEPARATOR)
    span.setTag(Tags.KEY_HTTP_URL, request.url)
    span.setTag(Tags.KEY_HTTP_METHOD, request.method)
    span.setTag(Tags.KEY_SPAN_KIND, Tags.VALUE_SPAN_KIND_CLIENT)

    return span
}

private fun isParentDropped(tracer: DatadogTracer, explicitParent: DatadogSpanContext?): Boolean {
    // Only consult the local active span. Explicit parents (request tags or propagated
    // headers) represent developer intent and must be honored regardless of priority.
    val activeContext = if (explicitParent != null) null else tracer.activeSpan()?.context()
    // Force resolution of the active span's sampling priority — a manual span backed
    // by a PendingTrace can read UNSET until the sampler commits at inject time.
    activeContext?.let { _TraceInternalProxy.setTracingSamplingPriorityIfNecessary(it) }
    val priority = activeContext?.samplingPriority
    return priority == PrioritySampling.SAMPLER_DROP || priority == PrioritySampling.USER_DROP
}
