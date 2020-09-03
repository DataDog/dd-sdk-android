package com.example.model

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import kotlin.String

internal data class UserMerged(
    val email: String? = null,
    val phone: String? = null,
    val info: Info? = null,
    val firstname: String? = null,
    val lastname: String
) {
    fun toJson(): JsonElement {
        val json = JsonObject()
        if (email != null) json.addProperty("email", email)
        if (phone != null) json.addProperty("phone", phone)
        if (info != null) json.add("info", info.toJson())
        if (firstname != null) json.addProperty("firstname", firstname)
        json.addProperty("lastname", lastname)
        return json
    }

    data class Info(
        val notes: String? = null,
        val source: String? = null
    ) {
        fun toJson(): JsonElement {
            val json = JsonObject()
            if (notes != null) json.addProperty("notes", notes)
            if (source != null) json.addProperty("source", source)
            return json
        }
    }
}
