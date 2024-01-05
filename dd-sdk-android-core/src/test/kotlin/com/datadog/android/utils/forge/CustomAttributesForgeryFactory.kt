/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.utils.forge

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory
import java.io.File
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import org.json.JSONArray
import org.json.JSONObject

internal class CustomAttributesForgeryFactory : ForgeryFactory<CustomAttributes> {
    override fun getForgery(forge: Forge): CustomAttributes {
        return CustomAttributes(
            nullableData = forge.run {
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
            },
            nonNullData = forge.run {
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
                    aList { aDouble() }
                )
                    .map { anAlphaNumericalString() to it }
                    .toMap()
            }
        )
    }
}
