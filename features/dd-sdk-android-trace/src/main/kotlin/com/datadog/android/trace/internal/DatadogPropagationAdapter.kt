/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.trace.internal

import com.datadog.android.trace.api.propagation.DatadogPropagation
import com.datadog.android.trace.api.span.DatadogSpanContext
import com.datadog.trace.bootstrap.instrumentation.api.AgentPropagation

internal class DatadogPropagationAdapter(private val delegate: AgentPropagation) : DatadogPropagation {

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
}
