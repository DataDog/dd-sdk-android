/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.trace.internal

import com.datadog.android.api.instrumentation.network.HttpRequestInfo
import com.datadog.android.api.instrumentation.network.HttpRequestInfoModifier
import com.datadog.android.api.instrumentation.network.tag
import com.datadog.android.lint.InternalApi
import com.datadog.android.trace.TraceContextInjection
import com.datadog.android.trace.TracingHeaderType
import com.datadog.android.trace.api.DatadogTracingConstants.PrioritySampling
import com.datadog.android.trace.api.span.DatadogSpan
import com.datadog.android.trace.api.span.DatadogSpanContext
import com.datadog.android.trace.api.tracer.DatadogTracer
import com.datadog.android.trace.internal.net.TraceContext
import com.datadog.trace.api.DDSpanId
import com.datadog.trace.api.DDTraceId
import com.datadog.trace.core.propagation.B3HttpCodec
import com.datadog.trace.core.propagation.DatadogHttpCodec
import com.datadog.trace.core.propagation.ExtractedContext
import com.datadog.trace.core.propagation.W3CHttpCodec

/**
 * For internal usage only.
 * Helper class for handling Datadog context propagation.
 */
@InternalApi
class DatadogPropagationHelper internal constructor() {
    /**
     * Determines if the provided [DatadogSpanContext] represents an extracted context.
     *
     * @param context The [DatadogSpanContext] to be evaluated.
     * @return True if the context is identified as an extracted context, otherwise false.
     */
    // TODO RUM-13441: This method should be private after refactor of TracingInterceptor.
    fun isExtractedContext(context: DatadogSpanContext): Boolean {
        if (context !is DatadogSpanContextAdapter) return false
        return context.delegate is ExtractedContext
    }

    /**
     * Sets the trace context information as a tag on the request.
     *
     * @param requestInfo the request modifier to add the trace context to.
     * @param traceId the trace ID to set.
     * @param spanId the span ID to set.
     * @param samplingPriority the sampling priority for the trace.
     */
    fun setTraceContext(
        requestInfo: HttpRequestInfoModifier,
        traceId: String,
        spanId: String,
        samplingPriority: Int
    ) {
        requestInfo.addTag(
            TraceContext::class.java,
            TraceContext(
                traceId,
                spanId,
                samplingPriority
            )
        )
    }

    /**
     * Extracts the parent span context from the request.
     * Checks both the request tags and headers for trace context information.
     *
     * @param tracer the tracer to use for context extraction.
     * @param request the HTTP request info to extract context from.
     * @return the extracted parent context, or null if none found.
     */
    fun extractParentContext(tracer: DatadogTracer, request: HttpRequestInfo): DatadogSpanContext? {
        val tagContext = request.tag(DatadogSpan::class.java)?.context() ?: extractTraceContext(request)

        val headerContext: DatadogSpanContext? = tracer.propagate().extract(request) { carrier, classifier ->
            val headers = carrier.headers
                .map { it.key to it.value.joinToString(";") }
                .toMap()

            // there is no actual classification here, values just got cached
            for ((key, value) in headers) classifier(key, value)
        }

        return if (headerContext != null && isExtractedContext(headerContext)) {
            headerContext
        } else {
            tagContext
        }
    }

    private fun extractTraceContext(request: HttpRequestInfo): DatadogSpanContext? =
        request.tag(DatadogSpan::class.java)?.context()
            ?: request.tag(TraceContext::class.java)?.let {
                createExtractedContext(
                    it.traceId,
                    it.spanId,
                    it.samplingPriority
                )
            }

    /**
     * Creates a [DatadogSpanContext] object that represents an extracted context from the given parameters.
     *
     * @param traceId The unique identifier for the trace.
     * @param spanId The unique identifier for the span.
     * @param samplingPriority The sampling priority value for determining the trace's sampling behavior.
     * @return A [DatadogSpanContext] instance containing the extracted context.
     */
    // TODO RUM-13441: this class should be internal after refactor of TracingInterceptor.
    fun createExtractedContext(
        traceId: String,
        spanId: String,
        samplingPriority: Int
    ): DatadogSpanContext = DatadogSpanContextAdapter(
        ExtractedContext(
            DDTraceId.fromHexOrDefault(traceId, DDTraceId.ZERO),
            DDSpanId.fromHexOrDefault(spanId, DDSpanId.ZERO),
            samplingPriority,
            null,
            null,
            null
        )
    )

    /**
     * Injects trace context headers for a sampled request.
     *
     * @param modifier the request modifier to add headers to.
     * @param tracer the tracer to use for context injection.
     * @param span the span containing the trace context.
     * @param tracingHeaderTypes the set of header types to inject.
     * @return the modified request info builder.
     */
    fun propagateSampledHeaders(
        modifier: HttpRequestInfoModifier,
        tracer: DatadogTracer,
        span: DatadogSpan,
        tracingHeaderTypes: Set<TracingHeaderType>
    ) = modifier.apply {
        tracer.propagate().inject(
            span.context(),
            modifier
        ) { carrier: HttpRequestInfoModifier, key: String, value: String ->
            when (key) {
                DatadogHttpCodec.ORIGIN_KEY,
                DatadogHttpCodec.SPAN_ID_KEY,
                DatadogHttpCodec.TRACE_ID_KEY,
                DatadogHttpCodec.DATADOG_TAGS_KEY,
                DatadogHttpCodec.SAMPLING_PRIORITY_KEY -> if (tracingHeaderTypes.contains(TracingHeaderType.DATADOG)) {
                    carrier.replaceHeader(key, value)
                } else {
                    carrier.removeHeader(key)
                }

                B3HttpCodec.B3_KEY -> if (tracingHeaderTypes.contains(TracingHeaderType.B3)) {
                    carrier.replaceHeader(key, value)
                } else {
                    carrier.removeHeader(key)
                }

                B3HttpCodec.SPAN_ID_KEY,
                B3HttpCodec.TRACE_ID_KEY,
                B3HttpCodec.SAMPLING_PRIORITY_KEY -> if (tracingHeaderTypes.contains(TracingHeaderType.B3MULTI)) {
                    carrier.replaceHeader(key, value)
                } else {
                    carrier.removeHeader(key)
                }

                W3CHttpCodec.BAGGAGE_KEY -> carrier.replaceHeader(
                    key,
                    DatadogTracingToolkit.mergeBaggage(
                        carrier.resolveExistingBaggageHeaderValue(),
                        value
                    )
                )

                W3CHttpCodec.TRACE_PARENT_KEY,
                W3CHttpCodec.TRACE_STATE_KEY -> if (tracingHeaderTypes.contains(TracingHeaderType.TRACECONTEXT)) {
                    carrier.replaceHeader(key, value)
                } else {
                    carrier.removeHeader(key)
                }

                else -> carrier.replaceHeader(key, value)
            }
        }
    }

    /**
     * Handles trace context headers for a non-sampled request.
     * Depending on the injection type, may remove or inject headers with drop sampling priority.
     *
     * @param modifier the request modifier to modify headers on.
     * @param tracer the tracer to use for context injection.
     * @param span the span containing the trace context.
     * @param tracingHeaderTypes the set of header types to handle.
     * @param injectionType whether to inject headers for non-sampled requests.
     * @param traceOrigin optional trace origin to include in headers.
     * @return the modified request info builder.
     */
    @Suppress("NestedBlockDepth")
    fun propagateNotSampledHeaders(
        modifier: HttpRequestInfoModifier,
        tracer: DatadogTracer,
        span: DatadogSpan,
        tracingHeaderTypes: Set<TracingHeaderType>,
        injectionType: TraceContextInjection,
        traceOrigin: String?
    ) = modifier.apply {
        for (headerType in tracingHeaderTypes) {
            when (headerType) {
                TracingHeaderType.DATADOG -> {
                    DATADOG_CODEC_HEADERS.forEach { removeHeader(it) }
                    if (TraceContextInjection.ALL == injectionType) resetDatadogHeaders(span, tracer)
                }

                TracingHeaderType.B3 -> {
                    removeHeader(B3HttpCodec.B3_KEY)
                    if (TraceContextInjection.ALL == injectionType) resetB3Headers()
                }

                TracingHeaderType.B3MULTI -> {
                    B3M_CODEC_HEADERS.forEach { removeHeader(it) }
                    if (TraceContextInjection.ALL == injectionType) resetB3MultiHeaders()
                }

                TracingHeaderType.TRACECONTEXT -> {
                    W3C_CODEC_HEADERS.forEach { removeHeader(it) }
                    if (TraceContextInjection.ALL == injectionType) {
                        resetW3CHeaders(
                            span,
                            traceOrigin
                        )
                    }
                }
            }
        }
    }

    /**
     * Extracts the sampling decision from the request.
     * Checks headers and tags for existing sampling decisions.
     *
     * @param request the HTTP request info to extract sampling decision from.
     * @return true if sampled, false if not sampled, null if no decision found.
     */
    fun extractSamplingDecision(request: HttpRequestInfo): Boolean? {
        val datadogSpan = request.tag(DatadogSpan::class.java)
        val headerSamplingPriority = extractSamplingDecisionFromHeader(request)
        val openTelemetrySpanSamplingPriority = request.tag(TraceContext::class.java)?.samplingPriority

        return when {
            headerSamplingPriority != null -> headerSamplingPriority

            datadogSpan != null -> {
                DatadogTracingToolkit.setTracingSamplingPriorityIfNecessary(datadogSpan.context())
                datadogSpan.context().samplingPriority > 0
            }

            openTelemetrySpanSamplingPriority == PrioritySampling.UNSET -> null

            else -> openTelemetrySpanSamplingPriority?.let { samplingPriority -> samplingPriority > 0 }
        }
    }

    @Suppress("ReturnCount")
    private fun extractSamplingDecisionFromHeader(request: HttpRequestInfo): Boolean? {
        val datadogSamplingPriority =
            request.headers[DatadogHttpCodec.SAMPLING_PRIORITY_KEY]?.firstOrNull()?.toIntOrNull()
        if (datadogSamplingPriority != null) {
            if (datadogSamplingPriority == PrioritySampling.UNSET) return null
            return datadogSamplingPriority == PrioritySampling.USER_KEEP ||
                datadogSamplingPriority == PrioritySampling.SAMPLER_KEEP
        }
        val b3MSamplingPriority = request.headers[B3HttpCodec.SAMPLING_PRIORITY_KEY]?.firstOrNull()
        if (b3MSamplingPriority != null) {
            return when (b3MSamplingPriority) {
                "1" -> true
                "0" -> false
                else -> null
            }
        }

        val b3HeaderValue = request.headers[B3HttpCodec.B3_KEY]?.firstOrNull()
        if (b3HeaderValue != null) {
            if (b3HeaderValue == "0") {
                return false
            }
            val b3HeaderParts = b3HeaderValue.split("-")
            if (b3HeaderParts.size >= B3_SAMPLING_DECISION_INDEX + 1) {
                return when (b3HeaderParts[B3_SAMPLING_DECISION_INDEX]) {
                    "1", "d" -> true
                    "0" -> false
                    else -> null
                }
            }
        }

        val w3cHeaderValue = request.headers[W3CHttpCodec.TRACE_PARENT_KEY]?.firstOrNull()
        if (w3cHeaderValue != null) {
            val w3CHeaderParts = w3cHeaderValue.split("-")
            if (w3CHeaderParts.size >= W3C_SAMPLING_DECISION_INDEX + 1) {
                return when (w3CHeaderParts[W3C_SAMPLING_DECISION_INDEX].toIntOrNull()) {
                    1 -> true
                    0 -> false
                    else -> null
                }
            }
        }

        return null
    }

    companion object {
        internal const val B3_DROP_SAMPLING_DECISION = "0"
        internal const val B3M_DROP_SAMPLING_DECISION = "0"
        internal const val DATADOG_DROP_SAMPLING_DECISION = "0"
        internal const val W3C_TRACE_PARENT_DROP_SAMPLING_DECISION = "00-%s-%s-00"
        internal const val W3C_TRACE_STATE_DROP_SAMPLING_DECISION = "dd=p:%s;s:0"
        internal const val W3C_TRACE_ID_LENGTH = 32
        internal const val W3C_PARENT_ID_LENGTH = 16
        internal const val B3_SAMPLING_DECISION_INDEX = 2
        internal const val W3C_SAMPLING_DECISION_INDEX = 3

        internal val DATADOG_CODEC_HEADERS = setOf(
            DatadogHttpCodec.ORIGIN_KEY,
            DatadogHttpCodec.SPAN_ID_KEY,
            DatadogHttpCodec.TRACE_ID_KEY,
            DatadogHttpCodec.DATADOG_TAGS_KEY,
            DatadogHttpCodec.SAMPLING_PRIORITY_KEY
        )

        internal val B3M_CODEC_HEADERS = setOf(
            B3HttpCodec.TRACE_ID_KEY,
            B3HttpCodec.SPAN_ID_KEY,
            B3HttpCodec.SAMPLING_PRIORITY_KEY
        )

        internal val W3C_CODEC_HEADERS = setOf(
            W3CHttpCodec.TRACE_PARENT_KEY,
            W3CHttpCodec.TRACE_STATE_KEY
        )

        private fun HttpRequestInfoModifier.resetDatadogHeaders(
            span: DatadogSpan,
            tracer: DatadogTracer
        ) {
            tracer.propagate().inject(
                span.context(),
                this
            ) { carrier, key, value ->
                carrier.removeHeader(key)
                if (key in DATADOG_CODEC_HEADERS) carrier.addHeader(key, value)
            }

            replaceHeader(DatadogHttpCodec.SAMPLING_PRIORITY_KEY, DATADOG_DROP_SAMPLING_DECISION)
        }

        private fun HttpRequestInfoModifier.resetB3Headers() {
            addHeader(B3HttpCodec.B3_KEY, B3_DROP_SAMPLING_DECISION)
        }

        private fun HttpRequestInfoModifier.resetB3MultiHeaders() {
            addHeader(B3HttpCodec.SAMPLING_PRIORITY_KEY, B3M_DROP_SAMPLING_DECISION)
        }

        private fun HttpRequestInfoModifier.resetW3CHeaders(
            span: DatadogSpan,
            traceOrigin: String?
        ) {
            val traceId = span.context().traceId.toHexString()
            val spanId = span.context().spanId.toString()
            addHeader(
                W3CHttpCodec.TRACE_PARENT_KEY,
                // TODO RUM-11445 InvalidStringFormat false alarm
                @Suppress("UnsafeThirdPartyFunctionCall", "InvalidStringFormat") // Format string is static
                W3C_TRACE_PARENT_DROP_SAMPLING_DECISION.format(
                    traceId.padStart(length = W3C_TRACE_ID_LENGTH, padChar = '0'),
                    spanId.padStart(length = W3C_PARENT_ID_LENGTH, padChar = '0')
                )
            )
            // TODO RUM-2121 3rd party vendor information will be erased
            // TODO RUM-11445 InvalidStringFormat false alarm
            @Suppress("UnsafeThirdPartyFunctionCall", "InvalidStringFormat") // Format string is static
            var traceStateHeader = W3C_TRACE_STATE_DROP_SAMPLING_DECISION
                .format(spanId.padStart(length = W3C_PARENT_ID_LENGTH, padChar = '0'))
            if (traceOrigin != null) {
                traceStateHeader += ";o:$traceOrigin"
            }
            addHeader(W3CHttpCodec.TRACE_STATE_KEY, traceStateHeader)
        }

        @Suppress("UnsafeThirdPartyFunctionCall") // exceptions are caught
        private fun HttpRequestInfoModifier.resolveExistingBaggageHeaderValue(): String? = try {
            // w3 HTTP specification allows multiple baggage headers and in such case they should be combined
            // https://www.w3.org/TR/baggage/
            result()
                .headers[W3CHttpCodec.BAGGAGE_KEY].orEmpty()
                .reduce { acc, header ->
                    DatadogTracingToolkit.mergeBaggage(acc, header)
                }
        } catch (_: UnsupportedOperationException) {
            // Header values collection is empty
            null
        } catch (_: IllegalStateException) {
            // Failed to compose baggage header
            null
        }
    }
}
