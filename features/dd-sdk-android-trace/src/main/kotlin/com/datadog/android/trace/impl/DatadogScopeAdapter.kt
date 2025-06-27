/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.trace.impl

import com.datadog.android.trace.api.scope.DatadogScope
import com.datadog.trace.bootstrap.instrumentation.api.AgentScope

internal class DatadogScopeAdapter(private val delegate: AgentScope) : DatadogScope {
    override fun close() = delegate.close()
}
