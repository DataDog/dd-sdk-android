package com.example.model

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import kotlin.Long
import kotlin.String

internal data class Person(
    val firstName: String? = null,
    val lastName: String? = null,
    val age: Long? = null
) {
    fun toJson(): JsonElement {
        val json = JsonObject()
        firstName?.let { json.addProperty("firstName", it) }
        lastName?.let { json.addProperty("lastName", it) }
        age?.let { json.addProperty("age", it) }
        return json
    }
}
