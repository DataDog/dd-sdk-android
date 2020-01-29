package com.datadog.android.utils.extension

import com.google.gson.JsonObject

fun JsonObject.getString(key: String): String {
    return get(key).asString
}
