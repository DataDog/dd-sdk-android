package com.example.model

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import java.lang.IllegalStateException
import java.lang.NullPointerException
import java.lang.NumberFormatException
import kotlin.String
import kotlin.jvm.JvmStatic
import kotlin.jvm.Throws

public data class Conflict(
    public val type: ConflictType? = null,
    public val user: User? = null,
) {
    public fun toJson(): JsonElement {
        val json = JsonObject()
        type?.let { typeNonNull ->
            json.add("type", typeNonNull.toJson())
        }
        user?.let { userNonNull ->
            json.add("user", userNonNull.toJson())
        }
        return json
    }

    public companion object {
        @JvmStatic
        @Throws(JsonParseException::class)
        public fun fromJson(jsonString: String): Conflict {
            val jsonObject = JsonParser.parseString(jsonString).asJsonObject
            return fromJsonElement(jsonObject)
        }

        @JvmStatic
        @Throws(JsonParseException::class)
        public fun fromJsonElement(jsonObject: JsonObject): Conflict {
            try {
                val type = (jsonObject.get("type") as? JsonObject)?.let {
                    ConflictType.fromJsonElement(it)
                }
                val user = (jsonObject.get("user") as? JsonObject)?.let {
                    User.fromJsonElement(it)
                }
                return Conflict(type, user)
            } catch (e: IllegalStateException) {
                throw JsonParseException(
                    "Unable to parse json into type Conflict",
                    e
                )
            } catch (e: NumberFormatException) {
                throw JsonParseException(
                    "Unable to parse json into type Conflict",
                    e
                )
            } catch (e: NullPointerException) {
                throw JsonParseException(
                    "Unable to parse json into type Conflict",
                    e
                )
            }
        }
    }

    public data class ConflictType(
        public val id: String? = null,
    ) {
        public fun toJson(): JsonElement {
            val json = JsonObject()
            id?.let { idNonNull ->
                json.addProperty("id", idNonNull)
            }
            return json
        }

        public companion object {
            @JvmStatic
            @Throws(JsonParseException::class)
            public fun fromJson(jsonString: String): ConflictType {
                val jsonObject = JsonParser.parseString(jsonString).asJsonObject
                return fromJsonElement(jsonObject)
            }

            @JvmStatic
            @Throws(JsonParseException::class)
            public fun fromJsonElement(jsonObject: JsonObject): ConflictType {
                try {
                    val id = jsonObject.get("id")?.asString
                    return ConflictType(id)
                } catch (e: IllegalStateException) {
                    throw JsonParseException(
                        "Unable to parse json into type ConflictType",
                        e
                    )
                } catch (e: NumberFormatException) {
                    throw JsonParseException(
                        "Unable to parse json into type ConflictType",
                        e
                    )
                } catch (e: NullPointerException) {
                    throw JsonParseException(
                        "Unable to parse json into type ConflictType",
                        e
                    )
                }
            }
        }
    }

    public data class User(
        public val name: String? = null,
        public val type: UserType? = null,
    ) {
        public fun toJson(): JsonElement {
            val json = JsonObject()
            name?.let { nameNonNull ->
                json.addProperty("name", nameNonNull)
            }
            type?.let { typeNonNull ->
                json.add("type", typeNonNull.toJson())
            }
            return json
        }

        public companion object {
            @JvmStatic
            @Throws(JsonParseException::class)
            public fun fromJson(jsonString: String): User {
                val jsonObject = JsonParser.parseString(jsonString).asJsonObject
                return fromJsonElement(jsonObject)
            }

            @JvmStatic
            @Throws(JsonParseException::class)
            public fun fromJsonElement(jsonObject: JsonObject): User {
                try {
                    val name = jsonObject.get("name")?.asString
                    val type = jsonObject.get("type")?.asString?.let {
                        UserType.fromJson(it)
                    }
                    return User(name, type)
                } catch (e: IllegalStateException) {
                    throw JsonParseException(
                        "Unable to parse json into type User",
                        e
                    )
                } catch (e: NumberFormatException) {
                    throw JsonParseException(
                        "Unable to parse json into type User",
                        e
                    )
                } catch (e: NullPointerException) {
                    throw JsonParseException(
                        "Unable to parse json into type User",
                        e
                    )
                }
            }
        }
    }

    public enum class UserType(
        private val jsonValue: String,
    ) {
        UNKNOWN("unknown"),
        CUSTOMER("customer"),
        PARTNER("partner"),
        ;

        public fun toJson(): JsonElement = JsonPrimitive(jsonValue)

        public companion object {
            @JvmStatic
            public fun fromJson(jsonString: String): UserType = values().first {
                it.jsonValue == jsonString
            }
        }
    }
}
