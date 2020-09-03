package com.example.model

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import kotlin.String

internal data class Conflict(
    val type: Type? = null,
    val user: User? = null
) {
    fun toJson(): JsonElement {
        val json = JsonObject()
        if (type != null) json.add("type", type.toJson())
        if (user != null) json.add("user", user.toJson())
        return json
    }

    data class Type(
        val id: String? = null
    ) {
        fun toJson(): JsonElement {
            val json = JsonObject()
            if (id != null) json.addProperty("id", id)
            return json
        }
    }

    data class User(
        val name: String? = null,
        val type: Type1? = null
    ) {
        fun toJson(): JsonElement {
            val json = JsonObject()
            if (name != null) json.addProperty("name", name)
            if (type != null) json.add("type", type.toJson())
            return json
        }
    }

    enum class Type1 {
        UNKNOWN,

        CUSTOMER,

        PARTNER;

        fun toJson(): JsonElement = when (this) {
            UNKNOWN -> JsonPrimitive("unknown")
            CUSTOMER -> JsonPrimitive("customer")
            PARTNER -> JsonPrimitive("partner")
        }
    }
}
