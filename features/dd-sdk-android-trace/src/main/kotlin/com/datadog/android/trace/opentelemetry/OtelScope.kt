package com.datadog.android.trace.opentelemetry

import com.datadog.trace.bootstrap.instrumentation.api.AgentScope
import io.opentelemetry.context.Scope

internal class OtelScope(internal val scope: Scope, internal val delegate: AgentScope) : Scope {
    override fun close() {
        delegate.close()
        scope.close()
    }
}
