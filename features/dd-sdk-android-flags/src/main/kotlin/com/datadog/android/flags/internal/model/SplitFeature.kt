/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal.model

import com.datadog.android.flags.model.FlagSnapshotFeature

internal typealias SplitFeature = FlagSnapshotFeature

/** Features routed to [destination], keyed by feature key. */
internal fun List<SplitFeature>.forDestination(destination: String): Map<String, Any> {
    if (isEmpty()) return emptyMap()
    val routed = mutableMapOf<String, Any>()
    forEach { feature ->
        if (destination in feature.destinations) routed[feature.key] = feature.value.scalar
    }
    return routed
}
