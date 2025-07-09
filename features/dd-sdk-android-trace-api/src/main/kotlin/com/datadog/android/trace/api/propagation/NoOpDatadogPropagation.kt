/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.trace.api.propagation

import com.datadog.android.trace.api.span.DatadogSpanContext

/**
 * A no-operation implementation of the `DatadogPropagation` interface.
 *
 * This implementation is intended as a placeholder making possible to create other NoOp.* classes.
 */
// TODO RUM-10573 - replace with @NoOpImplementation when method-level generics will be supported in noopfactory
internal class NoOpDatadogPropagation : DatadogPropagation {

    override fun <C> inject(
        context: DatadogSpanContext,
        carrier: C,
        setter: (carrier: C, key: String, value: String) -> Unit
    ) = Unit // Do nothing

    override fun <C> extract(
        carrier: C,
        getter: (carrier: C, classifier: (String, String) -> Boolean) -> Unit
    ): DatadogSpanContext? = null

    override fun isExtractedContext(context: DatadogSpanContext) = false
}
