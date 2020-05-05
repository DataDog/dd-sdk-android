package com.datadog.android.utils

import com.datadog.android.core.internal.domain.Batch
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser

internal val Batch.asJsonArray: JsonArray
    get() {
        val jsonElement = JsonParser.parseString(String(data))
        if (jsonElement is JsonArray) {
            return jsonElement
        } else if (jsonElement is JsonObject) {
            return jsonElement.getAsJsonArray("spans")
        } else {
            return JsonArray()
        }
    }

internal val Batch.lines: List<String>
    get() {
        return String(data).split('\n')
    }
