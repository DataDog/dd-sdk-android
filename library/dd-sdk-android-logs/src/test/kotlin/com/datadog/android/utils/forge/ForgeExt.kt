/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.utils.forge

import com.datadog.android.log.internal.utils.ISO_8601
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.jvm.ext.aTimestamp
import org.json.JSONArray
import org.json.JSONObject
import org.junit.jupiter.api.Assumptions
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

internal fun Forge.aFormattedTimestamp(format: String = ISO_8601): String {
    val simpleDateFormat = SimpleDateFormat(format, Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
    return simpleDateFormat.format(this.aTimestamp())
}

// TODO RUMM-2949 Share forgeries/test configurations between modules
/**
 * Will generate a map with different value types with a possibility to filter out given keys,
 * see [aFilteredMap] for details on filtering.
 */
internal fun Forge.exhaustiveAttributes(
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

private fun assumeDifferenceIsNoMore(result: Int, base: Int, maxDifference: Float) {
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
