/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.model

/** A primitive event metadata value. Nested values are not supported. */
sealed class FlagsEventMetadataValue {
    /** A string metadata [value]. */
    data class StringValue(val value: String) : FlagsEventMetadataValue()

    /** A boolean metadata [value]. */
    data class BooleanValue(val value: Boolean) : FlagsEventMetadataValue()

    /** An integral metadata [value], retaining its full precision. */
    data class LongValue(val value: Long) : FlagsEventMetadataValue()

    /** A floating point metadata [value]. */
    data class DoubleValue(val value: Double) : FlagsEventMetadataValue()
}
