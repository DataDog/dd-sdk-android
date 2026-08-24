package com.example.model

import com.datadog.android.core.`internal`.utils.JsonSerializer
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import java.lang.IllegalStateException
import java.lang.NullPointerException
import java.lang.NumberFormatException
import kotlin.Any
import kotlin.Array
import kotlin.Long
import kotlin.String
import kotlin.collections.MutableMap
import kotlin.jvm.JvmStatic
import kotlin.jvm.Throws

public data class Company(
    public val name: String? = null,
    public val ratings: Ratings? = null,
    public val information: Information? = null,
    public val additionalProperties: MutableMap<String, Any?> = mutableMapOf(),
) {
    public fun toJson(): JsonElement {
        val json = JsonObject()
        name?.let { nameNonNull ->
            json.addProperty("name", nameNonNull)
        }
        ratings?.let { ratingsNonNull ->
            json.add("ratings", ratingsNonNull.toJson())
        }
        information?.let { informationNonNull ->
            json.add("information", informationNonNull.toJson())
        }
        additionalProperties.forEach { (k, v) ->
            if (k !in RESERVED_PROPERTIES) {
                json.add(k, JsonSerializer.toJsonElement(v))
            }
        }
        return json
    }

    public companion object {
        internal val RESERVED_PROPERTIES: Array<String> = arrayOf("name", "ratings", "information")

        @JvmStatic
        @Throws(JsonParseException::class)
        public fun fromJson(jsonString: String): Company {
            try {
                val jsonObject = JsonParser.parseString(jsonString).asJsonObject
                return fromJsonObject(jsonObject)
            } catch (e: IllegalStateException) {
                throw JsonParseException(
                    "Unable to parse json into type Company",
                    e
                )
            }
        }

        @JvmStatic
        @Throws(JsonParseException::class)
        public fun fromJsonObject(jsonObject: JsonObject): Company {
            try {
                val name = jsonObject.get("name")?.asString
                val ratings = jsonObject.get("ratings")?.asJsonObject?.let {
                    Ratings.fromJsonObject(it)
                }
                val information = jsonObject.get("information")?.asJsonObject?.let {
                    Information.fromJsonObject(it)
                }
                val additionalProperties = mutableMapOf<String, Any?>()
                for (entry in jsonObject.entrySet()) {
                    if (entry.key !in RESERVED_PROPERTIES) {
                        additionalProperties[entry.key] = entry.value
                    }
                }
                return Company(name, ratings, information, additionalProperties)
            } catch (e: IllegalStateException) {
                throw JsonParseException(
                    "Unable to parse json into type Company",
                    e
                )
            } catch (e: NumberFormatException) {
                throw JsonParseException(
                    "Unable to parse json into type Company",
                    e
                )
            } catch (e: NullPointerException) {
                throw JsonParseException(
                    "Unable to parse json into type Company",
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
                try {
                    val jsonObject = JsonParser.parseString(jsonString).asJsonObject
                    return fromJsonObject(jsonObject)
                } catch (e: IllegalStateException) {
                    throw JsonParseException(
                        "Unable to parse json into type Ratings",
                        e
                    )
                }
            }

            @JvmStatic
            @Throws(JsonParseException::class)
            public fun fromJsonObject(jsonObject: JsonObject): Ratings {
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

    public data class Information(
        public val date: Long? = null,
        public val priority: Long? = null,
        public val additionalProperties: MutableMap<String, Any?> = mutableMapOf(),
    ) {
        public fun toJson(): JsonElement {
            val json = JsonObject()
            date?.let { dateNonNull ->
                json.addProperty("date", dateNonNull)
            }
            priority?.let { priorityNonNull ->
                json.addProperty("priority", priorityNonNull)
            }
            additionalProperties.forEach { (k, v) ->
                if (k !in RESERVED_PROPERTIES) {
                    json.add(k, JsonSerializer.toJsonElement(v))
                }
            }
            return json
        }

        public companion object {
            internal val RESERVED_PROPERTIES: Array<String> = arrayOf("date", "priority")

            @JvmStatic
            @Throws(JsonParseException::class)
            public fun fromJson(jsonString: String): Information {
                try {
                    val jsonObject = JsonParser.parseString(jsonString).asJsonObject
                    return fromJsonObject(jsonObject)
                } catch (e: IllegalStateException) {
                    throw JsonParseException(
                        "Unable to parse json into type Information",
                        e
                    )
                }
            }

            @JvmStatic
            @Throws(JsonParseException::class)
            public fun fromJsonObject(jsonObject: JsonObject): Information {
                try {
                    val date = jsonObject.get("date")?.asLong
                    val priority = jsonObject.get("priority")?.asLong
                    val additionalProperties = mutableMapOf<String, Any?>()
                    for (entry in jsonObject.entrySet()) {
                        if (entry.key !in RESERVED_PROPERTIES) {
                            additionalProperties[entry.key] = entry.value
                        }
                    }
                    return Information(date, priority, additionalProperties)
                } catch (e: IllegalStateException) {
                    throw JsonParseException(
                        "Unable to parse json into type Information",
                        e
                    )
                } catch (e: NumberFormatException) {
                    throw JsonParseException(
                        "Unable to parse json into type Information",
                        e
                    )
                } catch (e: NullPointerException) {
                    throw JsonParseException(
                        "Unable to parse json into type Information",
                        e
                    )
                }
            }
        }
    }
}
