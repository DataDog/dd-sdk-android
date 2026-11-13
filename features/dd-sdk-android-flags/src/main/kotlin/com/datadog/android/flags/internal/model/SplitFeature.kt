/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal.model

/**
 * A feature of the split selected for an assignment.
 *
 * @param key the feature key, unique within the split.
 * @param value a [String], [Double] or [Boolean].
 * @param destinations every non-empty destination received, including ones this SDK does not know.
 */
internal data class SplitFeature(
    val key: String,
    val value: Any,
    val destinations: List<String>
) {
    internal companion object {
        const val DESTINATION_HOOK = "HOOK"
        const val DESTINATION_EXPOSURE = "EXPOSURE"
        const val DESTINATION_EVALUATION = "EVALUATION"

        val KNOWN_DESTINATIONS = setOf(DESTINATION_HOOK, DESTINATION_EXPOSURE, DESTINATION_EVALUATION)
    }
}

/** Features routed to [destination], keyed by feature key. */
internal fun List<SplitFeature>.forDestination(destination: String): Map<String, Any> {
    if (isEmpty()) return emptyMap()
    val routed = mutableMapOf<String, Any>()
    forEach { feature ->
        if (destination in feature.destinations) routed[feature.key] = feature.value
    }
    return routed
}
