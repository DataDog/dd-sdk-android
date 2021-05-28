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

data class Comment(
    val message: String? = null,
    val ratings: Ratings? = null,
    val flags: Flags? = null,
    val tags: Tags? = null
) {
    fun toJson(): JsonElement {
        val json = JsonObject()
        message?.let { json.addProperty("message", it) }
        ratings?.let { json.add("ratings", it.toJson()) }
        flags?.let { json.add("flags", it.toJson()) }
        tags?.let { json.add("tags", it.toJson()) }
        return json
    }

    companion object {
        @JvmStatic
        @Throws(JsonParseException::class)
        fun fromJson(serializedObject: String): Comment {
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

    data class Ratings(
        val global: Long,
        val additionalProperties: Map<String, Long> = emptyMap()
    ) {
        fun toJson(): JsonElement {
            val json = JsonObject()
            json.addProperty("global", global)
            additionalProperties.forEach { (k, v) ->
                if (k !in RESERVED_PROPERTIES) {
                    json.addProperty(k, v)
                }
            }
            return json
        }

        companion object {
            private val RESERVED_PROPERTIES: Array<String> = arrayOf("global")

            @JvmStatic
            @Throws(JsonParseException::class)
            fun fromJson(serializedObject: String): Ratings {
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

    data class Flags(
        val additionalProperties: Map<String, Boolean> = emptyMap()
    ) {
        fun toJson(): JsonElement {
            val json = JsonObject()
            additionalProperties.forEach { (k, v) ->
                json.addProperty(k, v)
            }
            return json
        }

        companion object {
            @JvmStatic
            @Throws(JsonParseException::class)
            fun fromJson(serializedObject: String): Flags {
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

    data class Tags(
        val additionalProperties: Map<String, String> = emptyMap()
    ) {
        fun toJson(): JsonElement {
            val json = JsonObject()
            additionalProperties.forEach { (k, v) ->
                json.addProperty(k, v)
            }
            return json
        }

        companion object {
            @JvmStatic
            @Throws(JsonParseException::class)
            fun fromJson(serializedObject: String): Tags {
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
