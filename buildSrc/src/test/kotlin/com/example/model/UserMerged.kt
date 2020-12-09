package com.example.model

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import kotlin.String

data class UserMerged(
    val email: String? = null,
    val phone: String? = null,
    val info: Info? = null,
    val firstname: String? = null,
    val lastname: String
) {
    fun toJson(): JsonElement {
        val json = JsonObject()
        email?.let { json.addProperty("email", it) }
        phone?.let { json.addProperty("phone", it) }
        info?.let { json.add("info", it.toJson()) }
        firstname?.let { json.addProperty("firstname", it) }
        json.addProperty("lastname", lastname)
        return json
    }

    data class Info(
        val notes: String? = null,
        val source: String? = null
    ) {
        fun toJson(): JsonElement {
            val json = JsonObject()
            notes?.let { json.addProperty("notes", it) }
            source?.let { json.addProperty("source", it) }
            return json
        }
    }
}
