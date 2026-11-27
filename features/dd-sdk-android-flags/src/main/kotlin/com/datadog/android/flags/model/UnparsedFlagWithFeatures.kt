/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.model

import com.datadog.android.lint.InternalApi

/** The normalized features of the selected split, including their original destinations. */
@InternalApi
interface UnparsedFlagWithFeatures : UnparsedFlag {
    /** First valid feature per key in the evaluated split, in wire order. */
    val features: List<FlagSnapshotFeature>
}

/**
 * A feature transported with a snapshot assignment.
 * @property key Unique within the selected split.
 * @property value A string, boolean or finite double in its original wire type.
 * @property destinations Case-sensitive routes, including unknown destinations to preserve during replay.
 */
@InternalApi
data class FlagSnapshotFeature(
    val key: String,
    val value: Value,
    val destinations: List<String>
) {
    /** Scalar values retain their wire type when passed through a cross-platform bridge. */
    sealed class Value(internal val scalar: Any) {
        /** @property text Literal string, including numeric-looking text. */
        data class Text(val text: String) : Value(text)

        /** @property boolean The original boolean, including false. */
        data class Bool(val boolean: Boolean) : Value(boolean)

        /** @property number A finite wire number represented as a double. */
        data class Numeric(val number: Double) : Value(number)
    }

    constructor(key: String, value: String, destinations: List<String>) : this(key, Value.Text(value), destinations)
    constructor(key: String, value: Boolean, destinations: List<String>) : this(key, Value.Bool(value), destinations)
    constructor(key: String, value: Double, destinations: List<String>) : this(key, Value.Numeric(value), destinations)

    internal companion object {
        const val DESTINATION_HOOK = "HOOK"
        const val DESTINATION_EXPOSURE = "EXPOSURE"
        const val DESTINATION_EVALUATION = "EVALUATION"

        val KNOWN_DESTINATIONS = setOf(DESTINATION_HOOK, DESTINATION_EXPOSURE, DESTINATION_EVALUATION)
    }
}
