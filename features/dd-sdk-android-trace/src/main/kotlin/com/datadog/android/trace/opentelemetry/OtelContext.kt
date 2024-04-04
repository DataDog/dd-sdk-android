package com.datadog.opentelemetry.context

import com.datadog.android.trace.opentelemetry.OtelScope
import com.datadog.opentelemetry.trace.OtelSpan
import io.opentelemetry.api.trace.Span
import io.opentelemetry.context.Context
import io.opentelemetry.context.ContextKey
import io.opentelemetry.context.Scope

internal class OtelContext(
    internal val wrapped: Context,
    private val currentSpan: Span = Span.getInvalid(),
    private val rootSpan: Span = Span.getInvalid()
) : Context {
    override fun <V> get(key: ContextKey<V>): V? {
        if (OTEL_CONTEXT_SPAN_KEY == key.toString()) {
            return currentSpan as V
        } else if (OTEL_CONTEXT_ROOT_SPAN_KEY == key.toString()) {
            return rootSpan as V
        }
        return wrapped.get(key)
    }

    override fun <V> with(k1: ContextKey<V>, v1: V): Context {
        if (OTEL_CONTEXT_SPAN_KEY == k1.toString()) {
            return OtelContext(wrapped, v1 as Span, rootSpan)
        } else if (OTEL_CONTEXT_ROOT_SPAN_KEY == k1.toString()) {
            return OtelContext(wrapped, currentSpan, v1 as Span)
        }
        return OtelContext(wrapped.with(k1, v1), currentSpan, rootSpan)
    }

    override fun makeCurrent(): Scope {
        var scope = super.makeCurrent()
        if (currentSpan is OtelSpan) {
            val agentScope = currentSpan.activate()
            scope = OtelScope(scope, agentScope)
        }
        return scope
    }

    companion object {
        internal const val OTEL_CONTEXT_SPAN_KEY = "opentelemetry-trace-span-key"
        internal const val OTEL_CONTEXT_ROOT_SPAN_KEY = "opentelemetry-traces-local-root-span"
    }
}
