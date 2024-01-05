/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.tools.unit.forge

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import fr.xgouchet.elmyr.Forge
import java.io.File
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import org.json.JSONArray
import org.json.JSONObject
import org.junit.jupiter.api.Assumptions

/**
 * Will generate a map with different value types with a possibility to filter out given keys,
 * see [aFilteredMap] for details on filtering.
 *
 * @param excludedKeys Keys to exclude from generated map.
 * @param filterThreshold Max ratio of keys removed from originally generated map. If ratio
 * is more than that, [Assume] mechanism will be used.
 */
fun Forge.exhaustiveAttributes(
    excludedKeys: Set<String> = emptySet(),
    filterThreshold: Float = 0.5f
): MutableMap<String, Any?> {
    val map = generateMapWithExhaustiveValues(this).toMutableMap()

    map[""] = anHexadecimalString()
    map[aWhitespaceString()] = anHexadecimalString()
    map[anAlphabeticalString()] = generateMapWithExhaustiveValues(this).toMutableMap().apply {
        this[anAlphabeticalString()] = generateMapWithExhaustiveValues(this@exhaustiveAttributes)
    }

    val filtered = map.filterKeys { it !in excludedKeys }

    assumeDifferenceIsNoMore(filtered.size, map.size, filterThreshold)

    return filtered.toMutableMap()
}

/**
 * Creates a map just like [Forge#aMap], but it won't include given keys.
 *
 * @param K key type
 * @param V value type
 * @param size Map size, otherwise it will be of random size.
 * @param excludedKeys Keys to exclude from generated map.
 * @param filterThreshold Max ratio of keys removed from originally generated map. If ratio
 * is more than that, [Assume] mechanism will be used.
 * @param forging Key-Value forgery.
 */
fun <K, V> Forge.aFilteredMap(
    size: Int = -1,
    excludedKeys: Set<K>,
    filterThreshold: Float = 0.5f,
    forging: Forge.() -> Pair<K, V>
): Map<K, V> {
    val base = aMap(size, forging)

    val filtered = base.filterKeys { it !in excludedKeys }

    if (base.isNotEmpty()) {
        assumeDifferenceIsNoMore(filtered.size, base.size, filterThreshold)
    }

    return filtered
}

private fun assumeDifferenceIsNoMore(result: Int, base: Int, maxDifference: Float) {
    @Suppress("CheckInternal")
    check(result <= base) {
        "Number of elements after filtering cannot exceed the number of original elements."
    }

    val diff = (base - result).toFloat() / base
    Assumptions.assumeTrue(
        diff <= maxDifference,
        "Too many elements removed, condition cannot be satisfied."
    )
}

private fun generateMapWithExhaustiveValues(forge: Forge): MutableMap<String, Any?> {
    return forge.run {
        listOf(
            aBool(),
            anInt(),
            aLong(),
            aFloat(),
            aDouble(),
            anAsciiString(),
            getForgery<Date>(),
            getForgery<Locale>(),
            getForgery<TimeZone>(),
            getForgery<File>(),
            getForgery<JsonObject>(),
            getForgery<JsonArray>(),
            getForgery<JSONObject>(),
            getForgery<JSONArray>(),
            aList { anAlphabeticalString() },
            aList { aDouble() },
            null
        )
            .map { anAlphaNumericalString() to it }
            .toMap(mutableMapOf())
    }
}
