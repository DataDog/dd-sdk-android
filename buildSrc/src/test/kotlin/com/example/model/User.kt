package com.example.model

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import kotlin.String

internal data class User(
    val username: String,
    val host: String,
    val firstname: String? = null,
    val lastname: String,
    val contactType: ContactType
) {
    fun toJson(): JsonElement {
        val json = JsonObject()
        json.addProperty("username", username)
        json.addProperty("host", host)
        firstname?.let { json.addProperty("firstname", it) }
        json.addProperty("lastname", lastname)
        json.add("contact_type", contactType.toJson())
        return json
    }

    enum class ContactType {
        PERSONAL,

        PROFESSIONAL;

        fun toJson(): JsonElement = when (this) {
            PERSONAL -> JsonPrimitive("personal")
            PROFESSIONAL -> JsonPrimitive("professional")
        }
    }
}
