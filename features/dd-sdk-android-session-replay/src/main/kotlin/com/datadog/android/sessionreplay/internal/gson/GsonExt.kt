/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.gson

import com.datadog.android.api.InternalLogger
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import java.util.Locale

internal const val BROKEN_JSON_ERROR_MESSAGE_FORMAT =
    "SR GsonExt: Unable parse the batch data into a JsonObject: expected to parse [%s] as %s"
internal const val JSON_OBJECT_TYPE = "JsonObject"
internal const val JSON_ARRAY_TYPE = "JsonArray"
internal const val JSON_PRIMITIVE_TYPE = "JsonPrimitive"

@Suppress("SwallowedException")
internal fun JsonElement.safeGetAsJsonObject(internalLogger: InternalLogger): JsonObject? {
    return if (isJsonObject) {
        asJsonObject
    } else {
        internalLogger.log(
            InternalLogger.Level.ERROR,
            InternalLogger.Target.TELEMETRY,
            {
                BROKEN_JSON_ERROR_MESSAGE_FORMAT.format(
                    Locale.ENGLISH,
                    this.toString(),
                    JSON_OBJECT_TYPE
                )
            }
        )
        null
    }
}

@Suppress("SwallowedException")
internal fun JsonPrimitive.safeGetAsLong(internalLogger: InternalLogger): Long? {
    return try {
        asLong
    } catch (e: NumberFormatException) {
        internalLogger.log(
            InternalLogger.Level.ERROR,
            InternalLogger.Target.TELEMETRY,
            {
                BROKEN_JSON_ERROR_MESSAGE_FORMAT.format(
                    Locale.ENGLISH,
                    this.toString(),
                    JSON_PRIMITIVE_TYPE
                )
            },
            e
        )
        null
    }
}

@Suppress("SwallowedException")
internal fun JsonElement.safeGetAsJsonArray(internalLogger: InternalLogger): JsonArray? {
    return if (isJsonArray) {
        asJsonArray
    } else {
        internalLogger.log(
            InternalLogger.Level.ERROR,
            InternalLogger.Target.TELEMETRY,
            {
                BROKEN_JSON_ERROR_MESSAGE_FORMAT.format(
                    Locale.ENGLISH,
                    this.toString(),
                    JSON_ARRAY_TYPE
                )
            }
        )
        null
    }
}
