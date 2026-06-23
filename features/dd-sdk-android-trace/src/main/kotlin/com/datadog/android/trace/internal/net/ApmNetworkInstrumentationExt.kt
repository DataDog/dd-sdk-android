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
import com.datadog.android.trace.internal.ParentContextSource
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

internal fun DatadogSpan.sample(
    request: HttpRequestInfo,
    traceSampler: Sampler<DatadogSpan>,
    ignoreLocalDroppedParent: Boolean
): Boolean {
    val samplingPriority = samplingPriority
    return if (samplingPriority != null) {
        samplingPriority > 0
    } else {
        propagationHelper.extractSamplingDecision(request, ignoreLocalDroppedParent)
            ?: traceSampler.sample(this)
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
    ignoreLocalDroppedParent: Boolean
): DatadogSpan {
    val extractedParent = propagationHelper.extractParentContext(this, request)
    val parentContext = extractedParent?.context
    // Local sources we can ignore when dropped: tag-attached parent (developer intent) and
    // active span on this thread. Header-propagated parents are always honored to preserve
    // head-based sampling of upstream propagation.
    val localParentContext = when (extractedParent) {
        is ParentContextSource.FromTag -> extractedParent.context
        is ParentContextSource.FromHeaders -> null
        null -> activeSpan()?.context()
    }
    // Force resolution of the local parent's sampling priority — a manual span backed by a
    // PendingTrace can read UNSET until the sampler commits at inject time.
    localParentContext?.let { _TraceInternalProxy.setTracingSamplingPriorityIfNecessary(it) }
    val shouldIgnoreLocalDroppedParent = ignoreLocalDroppedParent && localParentContext.isDropped()

    val builder = buildSpan(SPAN_NAME.format(Locale.US, networkInstrumentationName))
        .withOrigin(traceOrigin)

    if (shouldIgnoreLocalDroppedParent) {
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

/**
 * Returns true if this context's sampling priority is a dropped trace (SAMPLER_DROP or USER_DROP).
 * `@InternalApi` so `DatadogInterceptor` can share this; revert to `internal` once `DatadogInterceptor` is removed.
 */
@InternalApi
fun DatadogSpanContext?.isDropped(): Boolean {
    val priority = this?.samplingPriority
    return priority.isDroppedPriority()
}

/**
 * Returns true if this sampling priority value is a dropped trace (SAMPLER_DROP or USER_DROP).
 * `@InternalApi` so `DatadogInterceptor` can share this; revert to `internal` once `DatadogInterceptor` is removed.
 */
@InternalApi
fun Int?.isDroppedPriority(): Boolean =
    this == PrioritySampling.SAMPLER_DROP || this == PrioritySampling.USER_DROP
