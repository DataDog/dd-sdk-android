package com.example.model

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import java.lang.IllegalStateException
import java.lang.NullPointerException
import java.lang.NumberFormatException
import java.lang.UnsupportedOperationException
import kotlin.Long
import kotlin.String
import kotlin.collections.ArrayList
import kotlin.collections.List
import kotlin.jvm.JvmStatic
import kotlin.jvm.Throws

public data class PathArrayWithNumber(
    public val path: List<Path>,
) {
    public fun toJson(): JsonElement {
        val json = JsonObject()
        val pathArray = JsonArray(path.size)
        path.forEach { pathArray.add(it.toJson()) }
        json.add("path", pathArray)
        return json
    }

    public companion object {
        @JvmStatic
        @Throws(JsonParseException::class)
        public fun fromJson(jsonString: String): PathArrayWithNumber {
            try {
                val jsonObject = JsonParser.parseString(jsonString).asJsonObject
                return fromJsonObject(jsonObject)
            } catch (e: IllegalStateException) {
                throw JsonParseException(
                    "Unable to parse json into type PathArrayWithNumber",
                    e
                )
            }
        }

        @JvmStatic
        @Throws(JsonParseException::class)
        public fun fromJsonObject(jsonObject: JsonObject): PathArrayWithNumber {
            try {
                val path = jsonObject.get("path").asJsonArray.let { jsonArray ->
                    val collection = ArrayList<Path>(jsonArray.size())
                    jsonArray.forEach {
                        collection.add(Path.fromJsonElement(it))
                    }
                    collection
                }
                return PathArrayWithNumber(path)
            } catch (e: IllegalStateException) {
                throw JsonParseException(
                    "Unable to parse json into type PathArrayWithNumber",
                    e
                )
            } catch (e: NumberFormatException) {
                throw JsonParseException(
                    "Unable to parse json into type PathArrayWithNumber",
                    e
                )
            } catch (e: NullPointerException) {
                throw JsonParseException(
                    "Unable to parse json into type PathArrayWithNumber",
                    e
                )
            }
        }
    }

    /**
     * This is a definition of a path
     */
    public sealed class Path {
        public abstract fun toJson(): JsonElement

        /**
         * boolean element
         */
        public data class Boolean(
            public val item: kotlin.Boolean,
        ) : Path() {
            override fun toJson(): JsonElement = JsonPrimitive(item)

            public companion object {
                @JvmStatic
                @Throws(JsonParseException::class)
                public fun fromJson(jsonString: kotlin.String): Boolean {
                    val jsonElement = JsonParser.parseString(jsonString)
                    try {
                        return fromJsonPrimitive(jsonElement.asJsonPrimitive)
                    } catch (e: IllegalStateException) {
                        throw JsonParseException("Unable to parse json into type Boolean", e)
                    }
                }

                @JvmStatic
                @Throws(JsonParseException::class)
                public fun fromJsonPrimitive(jsonPrimitive: JsonPrimitive): Boolean {
                    try {
                        if (jsonPrimitive.isBoolean) {
                            return Boolean(jsonPrimitive.asBoolean)
                        } else {
                            throw JsonParseException("Can't convert jsonPrimitive to Boolean")
                        }
                    } catch (e: IllegalStateException) {
                        throw JsonParseException("Unable to parse json into type Boolean", e)
                    } catch (e: UnsupportedOperationException) {
                        throw JsonParseException("Unable to parse json into type Boolean", e)
                    }
                }
            }
        }

        /**
         * string element
         */
        public data class String(
            public val item: kotlin.String,
        ) : Path() {
            override fun toJson(): JsonElement = JsonPrimitive(item)

            public companion object {
                @JvmStatic
                @Throws(JsonParseException::class)
                public fun fromJson(jsonString: kotlin.String): String {
                    val jsonElement = JsonParser.parseString(jsonString)
                    try {
                        return fromJsonPrimitive(jsonElement.asJsonPrimitive)
                    } catch (e: IllegalStateException) {
                        throw JsonParseException("Unable to parse json into type String", e)
                    }
                }

                @JvmStatic
                @Throws(JsonParseException::class)
                public fun fromJsonPrimitive(jsonPrimitive: JsonPrimitive): String {
                    try {
                        if (jsonPrimitive.isString) {
                            return String(jsonPrimitive.asString)
                        } else {
                            throw JsonParseException("Can't convert jsonPrimitive to String")
                        }
                    } catch (e: IllegalStateException) {
                        throw JsonParseException("Unable to parse json into type String", e)
                    } catch (e: UnsupportedOperationException) {
                        throw JsonParseException("Unable to parse json into type String", e)
                    }
                }
            }
        }

        /**
         * number element
         */
        public data class Number(
            public val item: kotlin.Number,
        ) : Path() {
            override fun toJson(): JsonElement = JsonPrimitive(item)

            public companion object {
                @JvmStatic
                @Throws(JsonParseException::class)
                public fun fromJson(jsonString: kotlin.String): Number {
                    val jsonElement = JsonParser.parseString(jsonString)
                    try {
                        return fromJsonPrimitive(jsonElement.asJsonPrimitive)
                    } catch (e: IllegalStateException) {
                        throw JsonParseException("Unable to parse json into type Number", e)
                    }
                }

                @JvmStatic
                @Throws(JsonParseException::class)
                public fun fromJsonPrimitive(jsonPrimitive: JsonPrimitive): Number {
                    try {
                        if (jsonPrimitive.isNumber) {
                            return Number(jsonPrimitive.asNumber)
                        } else {
                            throw JsonParseException("Can't convert jsonPrimitive to Number")
                        }
                    } catch (e: IllegalStateException) {
                        throw JsonParseException("Unable to parse json into type Number", e)
                    } catch (e: NumberFormatException) {
                        throw JsonParseException("Unable to parse json into type Number", e)
                    } catch (e: UnsupportedOperationException) {
                        throw JsonParseException("Unable to parse json into type Number", e)
                    }
                }
            }
        }

        /**
         * object element
         */
        public data class Point(
            public val x: Long,
            public val y: Long,
        ) : Path() {
            override fun toJson(): JsonElement {
                val json = JsonObject()
                json.addProperty("x", x)
                json.addProperty("y", y)
                return json
            }

            public companion object {
                @JvmStatic
                @Throws(JsonParseException::class)
                public fun fromJson(jsonString: kotlin.String): Point {
                    try {
                        val jsonObject = JsonParser.parseString(jsonString).asJsonObject
                        return fromJsonObject(jsonObject)
                    } catch (e: IllegalStateException) {
                        throw JsonParseException(
                            "Unable to parse json into type Point",
                            e
                        )
                    }
                }

                @JvmStatic
                @Throws(JsonParseException::class)
                public fun fromJsonObject(jsonObject: JsonObject): Point {
                    try {
                        val x = jsonObject.get("x").asLong
                        val y = jsonObject.get("y").asLong
                        return Point(x, y)
                    } catch (e: IllegalStateException) {
                        throw JsonParseException(
                            "Unable to parse json into type Point",
                            e
                        )
                    } catch (e: NumberFormatException) {
                        throw JsonParseException(
                            "Unable to parse json into type Point",
                            e
                        )
                    } catch (e: NullPointerException) {
                        throw JsonParseException(
                            "Unable to parse json into type Point",
                            e
                        )
                    }
                }
            }
        }

        public companion object {
            @JvmStatic
            @Throws(JsonParseException::class)
            public fun fromJson(jsonString: kotlin.String): Path {
                try {
                    val jsonElement = JsonParser.parseString(jsonString)
                    return fromJsonElement(jsonElement)
                } catch (e: IllegalStateException) {
                    throw JsonParseException(
                        "Unable to parse json into one of type Path",
                        e
                    )
                }
            }

            @JvmStatic
            @Throws(JsonParseException::class)
            public fun fromJsonElement(jsonElement: JsonElement): Path {
                val errors = mutableListOf<Throwable>()
                val asBoolean = try {
                    if (jsonElement is JsonPrimitive) {
                        Boolean.fromJsonPrimitive(jsonElement)
                    } else {
                        throw JsonParseException("Unable to parse json into type "
                                 + "kotlin.Boolean")
                    }
                } catch (e: JsonParseException) {
                    errors.add(e)
                    null
                }
                val asString = try {
                    if (jsonElement is JsonPrimitive) {
                        String.fromJsonPrimitive(jsonElement)
                    } else {
                        throw JsonParseException("Unable to parse json into type "
                                 + "kotlin.String")
                    }
                } catch (e: JsonParseException) {
                    errors.add(e)
                    null
                }
                val asNumber = try {
                    if (jsonElement is JsonPrimitive) {
                        Number.fromJsonPrimitive(jsonElement)
                    } else {
                        throw JsonParseException("Unable to parse json into type "
                                 + "kotlin.Number")
                    }
                } catch (e: JsonParseException) {
                    errors.add(e)
                    null
                }
                val asPoint = try {
                    if (jsonElement is JsonObject) {
                        Point.fromJsonObject(jsonElement)
                    } else {
                        throw JsonParseException("Unable to parse json into type "
                                 + "Point")
                    }
                } catch (e: JsonParseException) {
                    errors.add(e)
                    null
                }
                val result = arrayOf(
                    asBoolean,
                    asString,
                    asNumber,
                    asPoint,
                ).firstOrNull { it != null }
                if (result == null) {
                    val message = "Unable to parse json into one of type \n" + "Path\n" +
                        errors.joinToString("\n") { it.message.toString() }
                    throw JsonParseException(message)
                }
                return result
            }
        }
    }
}
