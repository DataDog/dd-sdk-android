/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal.model

internal enum class JsonKeys(val value: String) {
    // EvaluationContext keys
    EVALUATION_CONTEXT("evaluationContext"),
    TARGETING_KEY("targetingKey"),
    ATTRIBUTES("attributes"),

    // FlagsStateEntry keys
    FLAGS("flags"),
    LAST_UPDATE_TIMESTAMP("lastUpdateTimestamp"),

    // PrecomputedFlag keys
    VARIATION_TYPE("variationType"),
    VARIATION_VALUE("variationValue"),
    DO_LOG("doLog"),
    ALLOCATION_KEY("allocationKey"),
    VARIATION_KEY("variationKey"),
    EXTRA_LOGGING("extraLogging"),
    REASON("reason")
}

internal enum class VariationType(val value: String) {
    BOOLEAN("boolean"),
    STRING("string"),
    INTEGER("integer"),
    NUMBER("number"),
    FLOAT("float"),
    OBJECT("object")
}

internal enum class FlagReason(val value: String) {
    DEFAULT("DEFAULT"),
    TARGETING_MATCH("TARGETING_MATCH"),
    RULE_MATCH("RULE_MATCH"),
    PREREQUISITE_FAILED("PREREQUISITE_FAILED"),
    ERROR("ERROR")
}
