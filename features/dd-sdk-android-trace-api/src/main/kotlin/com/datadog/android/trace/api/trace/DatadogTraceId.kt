/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.trace.api.trace

import com.datadog.tools.annotation.NoOpImplementation

/**
 * Represents a Datadog trace ID, which is a unique identifier for a specific trace.
 */
@NoOpImplementation
interface DatadogTraceId {
    /**
     * Returns the lower-case zero-padded hexadecimal String representation of the trace ID.
     * The size will be rounded up to 16 or 32 characters.
     *
     * @param size The size in characters of the zero-padded String (rounded up to 16 or 32)
     * @return A lower-case zero-padded hexadecimal string representation of this trace ID
     */
    fun toHexStringPadded(size: Int): String

    /**
     * Converts the current trace ID into its hexadecimal string representation.
     *
     * @return the hexadecimal string representation of the trace ID.
     */
    fun toHexString(): String

    /**
     * Converts the Datadog trace ID to its numeric representation as a `Long`.
     *
     * @return the numeric value of the trace ID as a `Long`.
     */
    fun toLong(): Long

    companion object
}
