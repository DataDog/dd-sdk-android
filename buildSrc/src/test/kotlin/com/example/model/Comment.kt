package com.example.model

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import java.lang.IllegalStateException
import java.lang.NullPointerException
import java.lang.NumberFormatException
import kotlin.Array
import kotlin.Boolean
import kotlin.Long
import kotlin.String
import kotlin.collections.MutableMap
import kotlin.jvm.JvmStatic
import kotlin.jvm.Throws

public data class Comment(
    public val message: String? = null,
    public val ratings: Ratings? = null,
    public val flags: Flags? = null,
    public val tags: Tags? = null,
) {
    public fun toJson(): JsonElement {
        val json = JsonObject()
        message?.let { messageNonNull ->
            json.addProperty("message", messageNonNull)
        }
        ratings?.let { ratingsNonNull ->
            json.add("ratings", ratingsNonNull.toJson())
        }
        flags?.let { flagsNonNull ->
            json.add("flags", flagsNonNull.toJson())
        }
        tags?.let { tagsNonNull ->
            json.add("tags", tagsNonNull.toJson())
        }
        return json
    }

    public companion object {
        @JvmStatic
        @Throws(JsonParseException::class)
        public fun fromJson(jsonString: String): Comment {
            val jsonObject = JsonParser.parseString(jsonString).asJsonObject
            return fromJsonElement(jsonObject)
        }

        @JvmStatic
        @Throws(JsonParseException::class)
        public fun fromJsonElement(jsonObject: JsonObject): Comment {
            try {
                val message = jsonObject.get("message")?.asString
                val ratings = (jsonObject.get("ratings") as? JsonObject)?.let {
                    Ratings.fromJsonElement(it)
                }
                val flags = (jsonObject.get("flags") as? JsonObject)?.let {
                    Flags.fromJsonElement(it)
                }
                val tags = (jsonObject.get("tags") as? JsonObject)?.let {
                    Tags.fromJsonElement(it)
                }
                return Comment(message, ratings, flags, tags)
            } catch (e: IllegalStateException) {
                throw JsonParseException(
                    "Unable to parse json into type Comment",
                    e
                )
            } catch (e: NumberFormatException) {
                throw JsonParseException(
                    "Unable to parse json into type Comment",
                    e
                )
            } catch (e: NullPointerException) {
                throw JsonParseException(
                    "Unable to parse json into type Comment",
                    e
                )
            }
        }
    }

    public data class Ratings(
        public val global: Long,
        public val additionalProperties: MutableMap<String, Long> = mutableMapOf(),
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
            public fun fromJson(jsonString: String): Ratings {
                val jsonObject = JsonParser.parseString(jsonString).asJsonObject
                return fromJsonElement(jsonObject)
            }

            @JvmStatic
            @Throws(JsonParseException::class)
            public fun fromJsonElement(jsonObject: JsonObject): Ratings {
                try {
                    val global = jsonObject.get("global").asLong
                    val additionalProperties = mutableMapOf<String, Long>()
                    for (entry in jsonObject.entrySet()) {
                        if (entry.key !in RESERVED_PROPERTIES) {
                            additionalProperties[entry.key] = entry.value.asLong
                        }
                    }
                    return Ratings(global, additionalProperties)
                } catch (e: IllegalStateException) {
                    throw JsonParseException(
                        "Unable to parse json into type Ratings",
                        e
                    )
                } catch (e: NumberFormatException) {
                    throw JsonParseException(
                        "Unable to parse json into type Ratings",
                        e
                    )
                } catch (e: NullPointerException) {
                    throw JsonParseException(
                        "Unable to parse json into type Ratings",
                        e
                    )
                }
            }
        }
    }

    public data class Flags(
        public val additionalProperties: MutableMap<String, Boolean> = mutableMapOf(),
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
            public fun fromJson(jsonString: String): Flags {
                val jsonObject = JsonParser.parseString(jsonString).asJsonObject
                return fromJsonElement(jsonObject)
            }

            @JvmStatic
            @Throws(JsonParseException::class)
            public fun fromJsonElement(jsonObject: JsonObject): Flags {
                try {
                    val additionalProperties = mutableMapOf<String, Boolean>()
                    for (entry in jsonObject.entrySet()) {
                        additionalProperties[entry.key] = entry.value.asBoolean
                    }
                    return Flags(additionalProperties)
                } catch (e: IllegalStateException) {
                    throw JsonParseException(
                        "Unable to parse json into type Flags",
                        e
                    )
                } catch (e: NumberFormatException) {
                    throw JsonParseException(
                        "Unable to parse json into type Flags",
                        e
                    )
                } catch (e: NullPointerException) {
                    throw JsonParseException(
                        "Unable to parse json into type Flags",
                        e
                    )
                }
            }
        }
    }

    public data class Tags(
        public val additionalProperties: MutableMap<String, String> = mutableMapOf(),
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
            public fun fromJson(jsonString: String): Tags {
                val jsonObject = JsonParser.parseString(jsonString).asJsonObject
                return fromJsonElement(jsonObject)
            }

            @JvmStatic
            @Throws(JsonParseException::class)
            public fun fromJsonElement(jsonObject: JsonObject): Tags {
                try {
                    val additionalProperties = mutableMapOf<String, String>()
                    for (entry in jsonObject.entrySet()) {
                        additionalProperties[entry.key] = entry.value.asString
                    }
                    return Tags(additionalProperties)
                } catch (e: IllegalStateException) {
                    throw JsonParseException(
                        "Unable to parse json into type Tags",
                        e
                    )
                } catch (e: NumberFormatException) {
                    throw JsonParseException(
                        "Unable to parse json into type Tags",
                        e
                    )
                } catch (e: NullPointerException) {
                    throw JsonParseException(
                        "Unable to parse json into type Tags",
                        e
                    )
                }
            }
        }
    }
}
