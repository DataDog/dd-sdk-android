/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2020 Datadog, Inc.
 */

package com.datadog.android.utils.forge

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import fr.xgouchet.elmyr.Forge
import java.io.File
import java.util.Date
import java.util.Locale
import java.util.TimeZone

internal fun Forge.exhaustiveAttributes(): Map<String, Any?> {
    val map = listOf(
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
        null
    ).map { anAlphabeticalString() to it }
        .toMap().toMutableMap()
    map[""] = anHexadecimalString()
    map[aWhitespaceString()] = anHexadecimalString()
    return map
}
