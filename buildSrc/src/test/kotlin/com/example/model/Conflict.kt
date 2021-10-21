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

public data class Conflict(
    public val type: ConflictType? = null,
    public val user: User? = null
) {
    public fun toJson(): JsonElement {
        val json = JsonObject()
        type?.let { json.add("type", it.toJson()) }
        user?.let { json.add("user", it.toJson()) }
        return json
    }

    public companion object {
        @JvmStatic
        @Throws(JsonParseException::class)
        public fun fromJson(serializedObject: String): Conflict {
            try {
                val jsonObject = JsonParser.parseString(serializedObject).asJsonObject
                val type = jsonObject.get("type")?.toString()?.let {
                    ConflictType.fromJson(it)
                }
                val user = jsonObject.get("user")?.toString()?.let {
                    User.fromJson(it)
                }
                return Conflict(type, user)
            } catch (e: IllegalStateException) {
                throw JsonParseException(e.message)
            } catch (e: NumberFormatException) {
                throw JsonParseException(e.message)
            }
        }
    }

    public data class ConflictType(
        public val id: String? = null
    ) {
        public fun toJson(): JsonElement {
            val json = JsonObject()
            id?.let { json.addProperty("id", it) }
            return json
        }

        public companion object {
            @JvmStatic
            @Throws(JsonParseException::class)
            public fun fromJson(serializedObject: String): ConflictType {
                try {
                    val jsonObject = JsonParser.parseString(serializedObject).asJsonObject
                    val id = jsonObject.get("id")?.asString
                    return ConflictType(id)
                } catch (e: IllegalStateException) {
                    throw JsonParseException(e.message)
                } catch (e: NumberFormatException) {
                    throw JsonParseException(e.message)
                }
            }
        }
    }

    public data class User(
        public val name: String? = null,
        public val type: UserType? = null
    ) {
        public fun toJson(): JsonElement {
            val json = JsonObject()
            name?.let { json.addProperty("name", it) }
            type?.let { json.add("type", it.toJson()) }
            return json
        }

        public companion object {
            @JvmStatic
            @Throws(JsonParseException::class)
            public fun fromJson(serializedObject: String): User {
                try {
                    val jsonObject = JsonParser.parseString(serializedObject).asJsonObject
                    val name = jsonObject.get("name")?.asString
                    val type = jsonObject.get("type")?.asString?.let {
                        UserType.fromJson(it)
                    }
                    return User(name, type)
                } catch (e: IllegalStateException) {
                    throw JsonParseException(e.message)
                } catch (e: NumberFormatException) {
                    throw JsonParseException(e.message)
                }
            }
        }
    }

    public enum class UserType(
        private val jsonValue: String
    ) {
        UNKNOWN("unknown"),
        CUSTOMER("customer"),
        PARTNER("partner"),
        ;

        public fun toJson(): JsonElement = JsonPrimitive(jsonValue)

        public companion object {
            @JvmStatic
            public fun fromJson(serializedObject: String): UserType = values().first {
                it.jsonValue == serializedObject
            }
        }
    }
}
