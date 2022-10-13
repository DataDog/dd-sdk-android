/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.utils.forge

import com.datadog.android.log.internal.utils.ISO_8601
import com.datadog.android.rum.model.ActionEvent
import com.datadog.android.rum.model.ErrorEvent
import com.datadog.android.rum.model.LongTaskEvent
import com.datadog.android.rum.model.ResourceEvent
import com.datadog.android.rum.model.ViewEvent
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.jvm.ext.aTimestamp
import org.json.JSONArray
import org.json.JSONObject
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Will generate an alphaNumericalString which is not matching any values provided in the set.
 */
internal fun Forge.aStringNotMatchingSet(set: Set<String>): String {
    var aString = anAlphaNumericalString()
    while (set.contains(aString)) {
        aString = anAlphaNumericalString()
    }
    return aString
}

/**
 * Will generate a map with different value types with a possibility to filter out given keys,
 * see [aFilteredMap] for details on filtering.
 */
internal fun Forge.exhaustiveAttributes(
    excludedKeys: Set<String> = emptySet(),
    filterThreshold: Float = 0.5f
): Map<String, Any?> {
    val map = generateMapWithExhaustiveValues(this).toMutableMap()

    map[""] = anHexadecimalString()
    map[aWhitespaceString()] = anHexadecimalString()
    map[anAlphabeticalString()] = generateMapWithExhaustiveValues(this).toMutableMap().apply {
        this[anAlphabeticalString()] = generateMapWithExhaustiveValues(this@exhaustiveAttributes)
    }

    val filtered = map.filterKeys { it !in excludedKeys }

    assumeDifferenceIsNoMore(filtered.size, map.size, filterThreshold)

    return filtered
}

/**
 * Creates a map just like [Forge#aMap], but it won't include given keys.
 * @param excludedKeys Keys to exclude from generated map.
 * @param filterThreshold Max ratio of keys removed from originally generated map. If ratio
 * is more than that, [Assume] mechanism will be used.
 */
internal fun <K, V> Forge.aFilteredMap(
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

internal fun Forge.aFormattedTimestamp(format: String = ISO_8601): String {
    val simpleDateFormat = SimpleDateFormat(format, Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
    return simpleDateFormat.format(this.aTimestamp())
}

internal fun Forge.aRumEvent(): Any {
    return this.anElementFrom(
        this.getForgery<ViewEvent>(),
        this.getForgery<ActionEvent>(),
        this.getForgery<ResourceEvent>(),
        this.getForgery<ErrorEvent>(),
        this.getForgery<LongTaskEvent>()
    )
}

internal fun Forge.aRumEventAsJson(): JsonObject {
    return anElementFrom(
        this.getForgery<ViewEvent>().toJson().asJsonObject,
        this.getForgery<LongTaskEvent>().toJson().asJsonObject,
        this.getForgery<ActionEvent>().toJson().asJsonObject,
        this.getForgery<ResourceEvent>().toJson().asJsonObject,
        this.getForgery<ErrorEvent>().toJson().asJsonObject
    )
}

private fun assumeDifferenceIsNoMore(result: Int, base: Int, maxDifference: Float) {
    check(result <= base) {
        "Number of elements after filtering cannot exceed the number of original elements."
    }

    val diff = (base - result).toFloat() / base
    assumeTrue(
        diff <= maxDifference,
        "Too many elements removed, condition cannot be satisfied."
    )
}

private fun generateMapWithExhaustiveValues(forge: Forge): Map<String, Any?> {
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
            .toMap()
    }
}
