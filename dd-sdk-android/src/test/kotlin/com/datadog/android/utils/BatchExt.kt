package com.datadog.android.utils

import com.datadog.android.core.internal.domain.Batch
import com.google.gson.JsonArray
import com.google.gson.JsonParser

internal val Batch.logs: JsonArray
    get() {
        return JsonParser.parseString(String(data)).asJsonArray
    }
