/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.trace.internal

import com.datadog.android.lint.InternalApi
import com.datadog.trace.api.DDSpanId

/**
 * For library usage only.
 * Interface for converting Datadog span IDs between hexadecimal and decimal representations.
 */
@InternalApi
class DatadogSpanIdConverter internal constructor() {

    /**
     * Converts a hexadecimal string representation of an spanId into its equivalent long value.
     *
     * @param spanId The hexadecimal string to convert.
     * @return The long value corresponding to the hexadecimal string.
     * @throws NumberFormatException if the input string is not a valid hexadecimal representation or is null.
     */
    @Throws(NumberFormatException::class)
    fun fromHex(spanId: String): Long = DDSpanId.fromHex(spanId)

    /**
     * Converts the given long-based span ID into a hexadecimal string representation,
     * ensuring that the resulting string has a padded length sufficient for hexadecimal IDs.
     *
     * @param spanId The span ID to convert, represented as a long value.
     * @return A hexadecimal string representation of the given span ID, padded as necessary.
     */
    fun toHexStringPadded(spanId: Long): String = DDSpanId.toHexStringPadded(spanId)
}
