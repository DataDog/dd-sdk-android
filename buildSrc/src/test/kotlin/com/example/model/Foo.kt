package com.example.model

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import kotlin.Long
import kotlin.String

internal data class Foo(
    val bar: String? = null,
    val baz: Long? = null
) {
    fun toJson(): JsonElement {
        val json = JsonObject()
        if (bar != null) json.addProperty("bar", bar)
        if (baz != null) json.addProperty("baz", baz)
        return json
    }
}
