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
        if (firstName != null) json.addProperty("firstName", firstName)
        if (lastName != null) json.addProperty("lastName", lastName)
        if (age != null) json.addProperty("age", age)
        return json
    }
}
