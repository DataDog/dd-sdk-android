/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.featureflags.internal.model

/**
 * Constants for PrecomputedFlag field values.
 */
internal object PrecomputedFlagConstants {

    /**
     * Valid variation type values.
     */
    object VariationType {
        const val BOOLEAN = "boolean"
        const val STRING = "string"
        const val INTEGER = "integer"
        const val DOUBLE = "double"
        const val JSON = "json"

        val ALL_VALUES = listOf(BOOLEAN, STRING, INTEGER, DOUBLE, JSON)
    }

    /**
     * Valid reason values.
     */
    object Reason {
        const val DEFAULT = "DEFAULT"
        const val TARGETING_MATCH = "TARGETING_MATCH"
        const val RULE_MATCH = "RULE_MATCH"
        const val PREREQUISITE_FAILED = "PREREQUISITE_FAILED"
        const val ERROR = "ERROR"

        val ALL_VALUES = listOf(DEFAULT, TARGETING_MATCH, RULE_MATCH, PREREQUISITE_FAILED, ERROR)
    }
}

