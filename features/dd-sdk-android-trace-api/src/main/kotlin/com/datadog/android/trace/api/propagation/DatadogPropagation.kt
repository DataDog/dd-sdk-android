/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.trace.api.propagation

import com.datadog.android.trace.api.span.DatadogSpanContext

/**
 * Provides an interface for injecting and extracting span context to/from specified carriers.
 * Used for propagating context to http headers
 */
interface DatadogPropagation {

    /**
     * Injects the span context into a specified carrier using the provided setter function.
     * This method facilitates the propagation of trace information across process boundaries
     * or systems by embedding relevant span context data into the carrier.
     *
     * @param C The type of the carrier containing the span context information.
     * @param context The [DatadogSpanContext] containing the trace and span information to inject.
     * @param carrier The carrier object where the trace information will be injected.
     * @param setter A function used to set key-value pairs into the carrier. It takes the carrier,
     *               a key, and a value as parameters.
     */
    fun <C> inject(
        context: DatadogSpanContext,
        carrier: C,
        setter: (carrier: C, key: String, value: String) -> Unit
    )

    /**
     * Extracts a [DatadogSpanContext] from the provided carrier using the specified getter function.
     * This method is used to propagate Datadog span context information across systems by retrieving
     * trace and span information from the carrier.
     *
     * @param C The type of the carrier containing the span context information.
     * @param carrier The carrier object from which the span context will be extracted.
     * @param getter A function that retrieves key-value pairs from the carrier, using a classifier function
     *               to match desired key-value pairs.
     *               The classifier function takes two strings (key and value) and returns a boolean
     *               indicating whether the key-value pair belongs to the span context.
     * @return The extracted [DatadogSpanContext] if one could be retrieved, or null otherwise.
     */
    fun <C> extract(
        carrier: C,
        getter: (carrier: C, classifier: (String, String) -> Boolean) -> Unit
    ): DatadogSpanContext?
}
