package com.example.model

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import kotlin.String

data class Conflict(
    val type: ConflictType? = null,
    val user: User? = null
) {
    fun toJson(): JsonElement {
        val json = JsonObject()
        type?.let { json.add("type", it.toJson()) }
        user?.let { json.add("user", it.toJson()) }
        return json
    }

    data class ConflictType(
        val id: String? = null
    ) {
        fun toJson(): JsonElement {
            val json = JsonObject()
            id?.let { json.addProperty("id", it) }
            return json
        }
    }

    data class User(
        val name: String? = null,
        val type: UserType? = null
    ) {
        fun toJson(): JsonElement {
            val json = JsonObject()
            name?.let { json.addProperty("name", it) }
            type?.let { json.add("type", it.toJson()) }
            return json
        }
    }

    enum class UserType {
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
