/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.utils

/**
 * @return a new mutable map containing all key-value pairs from the given array of pairs.
 *
 * The returned map preserves the entry iteration order of the original array.
 * If any of two pairs would have the same key the last one gets added to the map.
 */
internal fun <K, V> Iterable<Pair<K, V>>.toMutableMap(): MutableMap<K, V> {
    return toMap(mutableMapOf())
}

/**
 * @return the [MutableMap] if its not `null`, or the empty [MutableMap] otherwise.
 */
internal fun <K, V> MutableMap<K, V>?.orEmpty(): MutableMap<K, V> {
    return this ?: mutableMapOf()
}
