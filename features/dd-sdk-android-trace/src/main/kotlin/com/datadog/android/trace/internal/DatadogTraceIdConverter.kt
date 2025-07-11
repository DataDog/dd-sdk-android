/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.trace.internal

import com.datadog.android.lint.InternalApi
import com.datadog.android.trace.api.trace.DatadogTraceId
import com.datadog.trace.api.DDTraceId

/**
 * For library usage only.
 * Factory interface for converting `DatadogTraceId` instances to Long/String and vice-versa.
 */
@InternalApi
interface DatadogTraceIdConverter {
    /**
     * Creates a [DatadogTraceId] instance representing the zero value.
     *
     * @return a [DatadogTraceId] instance initialized to the zero value.
     */
    fun zero(): DatadogTraceId

    /**
     * Creates a [DatadogTraceId] instance from the given numeric representation of a trace ID.
     *
     * @param id the numeric value of the trace ID as a `Long`.
     * @return a [DatadogTraceId] instance representing the given trace ID.
     */
    fun from(id: Long): DatadogTraceId

    /**
     * Creates a [DatadogTraceId] instance from the given string representation of a trace ID.
     *
     * @param id the string representation of the trace ID.
     * @return a [DatadogTraceId] instance representing the given trace ID.
     */
    fun from(id: String): DatadogTraceId

    /**
     * Creates a [DatadogTraceId] instance from the given hexadecimal string representation of a trace ID.
     *
     * @param id the hexadecimal string representation of the trace ID.
     * @return a [DatadogTraceId] instance representing the given trace ID.
     */
    fun fromHex(id: String): DatadogTraceId

    /**
     * Converts the Datadog trace ID to its numeric representation as a `Long`.
     *
     * @return the numeric value of the trace ID as a `Long`.
     */
    fun toLong(traceId: DatadogTraceId): Long

    /**
     * Converts the current trace ID into its hexadecimal string representation.
     *
     * @return the hexadecimal string representation of the trace ID.
     */
    fun toHexString(traceId: DatadogTraceId): String
}

internal object DatadogTraceIdConverterAdapter : DatadogTraceIdConverter {
    override fun zero(): DatadogTraceId = DatadogTraceIdAdapter.ZERO
    override fun from(id: Long): DatadogTraceId = DatadogTraceIdAdapter(DDTraceId.from(id))
    override fun from(id: String): DatadogTraceId = DatadogTraceIdAdapter(DDTraceId.from(id))
    override fun fromHex(id: String): DatadogTraceId = DatadogTraceIdAdapter(DDTraceId.fromHex(id))
    override fun toLong(traceId: DatadogTraceId): Long = (traceId as DatadogTraceIdAdapter).toLong()
    override fun toHexString(traceId: DatadogTraceId): String = (traceId as DatadogTraceIdAdapter).toHexString()
}
