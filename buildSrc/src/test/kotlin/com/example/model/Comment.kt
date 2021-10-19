package com.example.model

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import java.lang.IllegalStateException
import java.lang.NumberFormatException
import kotlin.Array
import kotlin.Boolean
import kotlin.Long
import kotlin.String
import kotlin.collections.Map
import kotlin.jvm.JvmStatic
import kotlin.jvm.Throws

public data class Comment(
    public val message: String? = null,
    public val ratings: Ratings? = null,
    public val flags: Flags? = null,
    public val tags: Tags? = null
) {
    public fun toJson(): JsonElement {
        val json = JsonObject()
        message?.let { json.addProperty("message", it) }
        ratings?.let { json.add("ratings", it.toJson()) }
        flags?.let { json.add("flags", it.toJson()) }
        tags?.let { json.add("tags", it.toJson()) }
        return json
    }

    public companion object {
        @JvmStatic
        @Throws(JsonParseException::class)
        public fun fromJson(serializedObject: String): Comment {
            try {
                val jsonObject = JsonParser.parseString(serializedObject).asJsonObject
                val message = jsonObject.get("message")?.asString
                val ratings = jsonObject.get("ratings")?.toString()?.let {
                    Ratings.fromJson(it)
                }
                val flags = jsonObject.get("flags")?.toString()?.let {
                    Flags.fromJson(it)
                }
                val tags = jsonObject.get("tags")?.toString()?.let {
                    Tags.fromJson(it)
                }
                return Comment(message, ratings, flags, tags)
            } catch (e: IllegalStateException) {
                throw JsonParseException(e.message)
            } catch (e: NumberFormatException) {
                throw JsonParseException(e.message)
            }
        }
    }

    public data class Ratings(
        public val global: Long,
        public val additionalProperties: Map<String, Long> = emptyMap()
    ) {
        public fun toJson(): JsonElement {
            val json = JsonObject()
            json.addProperty("global", global)
            additionalProperties.forEach { (k, v) ->
                if (k !in RESERVED_PROPERTIES) {
                    json.addProperty(k, v)
                }
            }
            return json
        }

        public companion object {
            internal val RESERVED_PROPERTIES: Array<String> = arrayOf("global")

            @JvmStatic
            @Throws(JsonParseException::class)
            public fun fromJson(serializedObject: String): Ratings {
                try {
                    val jsonObject = JsonParser.parseString(serializedObject).asJsonObject
                    val global = jsonObject.get("global").asLong
                    val additionalProperties = mutableMapOf<String, Long>()
                    for (entry in jsonObject.entrySet()) {
                        if (entry.key !in RESERVED_PROPERTIES) {
                            additionalProperties[entry.key] = entry.value.asLong
                        }
                    }
                    return Ratings(global, additionalProperties)
                } catch (e: IllegalStateException) {
                    throw JsonParseException(e.message)
                } catch (e: NumberFormatException) {
                    throw JsonParseException(e.message)
                }
            }
        }
    }

    public data class Flags(
        public val additionalProperties: Map<String, Boolean> = emptyMap()
    ) {
        public fun toJson(): JsonElement {
            val json = JsonObject()
            additionalProperties.forEach { (k, v) ->
                json.addProperty(k, v)
            }
            return json
        }

        public companion object {
            @JvmStatic
            @Throws(JsonParseException::class)
            public fun fromJson(serializedObject: String): Flags {
                try {
                    val jsonObject = JsonParser.parseString(serializedObject).asJsonObject
                    val additionalProperties = mutableMapOf<String, Boolean>()
                    for (entry in jsonObject.entrySet()) {
                        additionalProperties[entry.key] = entry.value.asBoolean
                    }
                    return Flags(additionalProperties)
                } catch (e: IllegalStateException) {
                    throw JsonParseException(e.message)
                } catch (e: NumberFormatException) {
                    throw JsonParseException(e.message)
                }
            }
        }
    }

    public data class Tags(
        public val additionalProperties: Map<String, String> = emptyMap()
    ) {
        public fun toJson(): JsonElement {
            val json = JsonObject()
            additionalProperties.forEach { (k, v) ->
                json.addProperty(k, v)
            }
            return json
        }

        public companion object {
            @JvmStatic
            @Throws(JsonParseException::class)
            public fun fromJson(serializedObject: String): Tags {
                try {
                    val jsonObject = JsonParser.parseString(serializedObject).asJsonObject
                    val additionalProperties = mutableMapOf<String, String>()
                    for (entry in jsonObject.entrySet()) {
                        additionalProperties[entry.key] = entry.value.asString
                    }
                    return Tags(additionalProperties)
                } catch (e: IllegalStateException) {
                    throw JsonParseException(e.message)
                } catch (e: NumberFormatException) {
                    throw JsonParseException(e.message)
                }
            }
        }
    }
}
