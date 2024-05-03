/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.trace.opentelemetry

import com.datadog.trace.bootstrap.instrumentation.api.AgentScope
import io.opentelemetry.context.Scope

internal class OtelScope(internal val scope: Scope, internal val delegate: AgentScope) : Scope {
    override fun close() {
        delegate.close()
        scope.close()
    }
}
