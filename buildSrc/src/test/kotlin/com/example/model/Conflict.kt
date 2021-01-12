package com.example.model

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import java.lang.IllegalStateException
import java.lang.NumberFormatException
import kotlin.String
import kotlin.jvm.JvmStatic
import kotlin.jvm.Throws

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

    companion object {
        @JvmStatic
        @Throws(JsonParseException::class)
        fun fromJson(serializedObject: String): Conflict {
            try {
                val jsonObject = JsonParser.parseString(serializedObject).asJsonObject
                val type = jsonObject.getAsJsonObject("type")?.toString()?.let {
                    ConflictType.fromJson(it)
                }
                val user = jsonObject.getAsJsonObject("user")?.toString()?.let {
                    User.fromJson(it)
                }
                return Conflict(
                    type,
                    user
                )
            } catch(e:IllegalStateException) {
                throw JsonParseException(e.message)
            } catch(e:NumberFormatException) {
                throw JsonParseException(e.message)
            }
        }
    }

    data class ConflictType(
        val id: String? = null
    ) {
        fun toJson(): JsonElement {
            val json = JsonObject()
            id?.let { json.addProperty("id", it) }
            return json
        }

        companion object {
            @JvmStatic
            @Throws(JsonParseException::class)
            fun fromJson(serializedObject: String): ConflictType {
                try {
                    val jsonObject = JsonParser.parseString(serializedObject).asJsonObject
                    val id = jsonObject.getAsJsonPrimitive("id")?.asString
                    return ConflictType(
                        id
                    )
                } catch(e:IllegalStateException) {
                    throw JsonParseException(e.message)
                } catch(e:NumberFormatException) {
                    throw JsonParseException(e.message)
                }
            }
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

        companion object {
            @JvmStatic
            @Throws(JsonParseException::class)
            fun fromJson(serializedObject: String): User {
                try {
                    val jsonObject = JsonParser.parseString(serializedObject).asJsonObject
                    val name = jsonObject.getAsJsonPrimitive("name")?.asString
                    val type = jsonObject.get("type")?.asString?.let {
                        UserType.fromJson(it)
                    }
                    return User(
                        name,
                        type
                    )
                } catch(e:IllegalStateException) {
                    throw JsonParseException(e.message)
                } catch(e:NumberFormatException) {
                    throw JsonParseException(e.message)
                }
            }
        }
    }

    enum class UserType(
        private val jsonValue: String
    ) {
        UNKNOWN("unknown"),

        CUSTOMER("customer"),

        PARTNER("partner");

        fun toJson(): JsonElement = JsonPrimitive(jsonValue)

        companion object {
            @JvmStatic
            fun fromJson(serializedObject: String): UserType = values().first{it.jsonValue ==
                    serializedObject}
        }
    }
}
