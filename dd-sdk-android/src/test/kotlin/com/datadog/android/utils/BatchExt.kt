/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

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
