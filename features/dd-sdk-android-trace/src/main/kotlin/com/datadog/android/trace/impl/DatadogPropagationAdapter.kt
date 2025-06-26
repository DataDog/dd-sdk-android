package com.datadog.android.trace.impl

import com.datadog.android.trace.api.propagation.DatadogPropagation
import com.datadog.android.trace.api.span.DatadogSpan
import com.datadog.android.trace.api.span.DatadogSpanContext
import com.datadog.trace.api.DDSpanId
import com.datadog.trace.api.DDTraceId
import com.datadog.trace.bootstrap.instrumentation.api.AgentPropagation
import com.datadog.trace.core.propagation.ExtractedContext

internal class DatadogPropagationAdapter(private val delegate: AgentPropagation) : DatadogPropagation {
    override fun <C> inject(span: DatadogSpan, carrier: C, setter: (carrier: C, key: String, value: String) -> Unit) {
        if (span !is DatadogSpanAdapter) return
        delegate.inject(span.delegate, carrier, setter)
    }

    override fun <C> inject(
        context: DatadogSpanContext,
        carrier: C,
        setter: (carrier: C, key: String, value: String) -> Unit
    ) {
        if (context !is DatadogSpanContextAdapter) return
        delegate.inject(context.delegate, carrier, setter)
    }

    override fun <C> extract(
        carrier: C,
        getter: (carrier: C, classifier: (String, String) -> Boolean) -> Unit
    ): DatadogSpanContext? {
        return delegate.extract(carrier) { car, cls -> getter(car, cls::accept) }
            ?.let { DatadogSpanContextAdapter(it) }
    }

    override fun isExtractedContext(context: DatadogSpanContext): Boolean {
        if (context !is DatadogSpanContextAdapter) return false
        return context.delegate is ExtractedContext
    }

    override fun createExtractedContext(traceId: String, spanId: String, samplingPriority: Int): DatadogSpanContext {
        val extractedContext = ExtractedContext(
            DDTraceId.fromHexOrDefault(traceId, DDTraceId.ZERO),
            DDSpanId.fromHexOrDefault(spanId, DDSpanId.ZERO),
            samplingPriority,
            null,
            null,
            null
        )

        return DatadogSpanContextAdapter(extractedContext)
    }
}
