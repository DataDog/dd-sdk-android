/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.gson

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive

@Suppress("SwallowedException")
internal fun JsonElement.safeGetAsJsonObject(): JsonObject? {
    return try {
        asJsonObject
    } catch (e: IllegalStateException) {
        // TODO: RUMM-2397 Add the proper logs here once the sdkLogger will be added
        null
    }
}

@Suppress("SwallowedException")
internal fun JsonPrimitive.safeGetAsLong(): Long? {
    return try {
        asLong
    } catch (e: NumberFormatException) {
        // TODO: RUMM-2397 Add the proper logs here once the sdkLogger will be added
        null
    }
}

@Suppress("SwallowedException")
internal fun JsonElement.safeGetAsJsonArray(): JsonArray? {
    return try {
        asJsonArray
    } catch (e: IllegalStateException) {
        // TODO: RUMM-2397 Add the proper logs here once the sdkLogger will be added
        null
    }
}
