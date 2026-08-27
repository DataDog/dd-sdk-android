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
import kotlin.String
import kotlin.collections.ArrayList
import kotlin.collections.List
import kotlin.jvm.JvmStatic
import kotlin.jvm.Throws

public data class PathArrayWithInteger(
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
        public fun fromJson(jsonString: String): PathArrayWithInteger {
            try {
                val jsonObject = JsonParser.parseString(jsonString).asJsonObject
                return fromJsonObject(jsonObject)
            } catch (e: IllegalStateException) {
                throw JsonParseException(
                    "Unable to parse json into type PathArrayWithInteger",
                    e
                )
            }
        }

        @JvmStatic
        @Throws(JsonParseException::class)
        public fun fromJsonObject(jsonObject: JsonObject): PathArrayWithInteger {
            try {
                val path = jsonObject.get("path").asJsonArray.let { jsonArray ->
                    val collection = ArrayList<Path>(jsonArray.size())
                    jsonArray.forEach {
                        collection.add(Path.fromJsonElement(it))
                    }
                    collection
                }
                return PathArrayWithInteger(path)
            } catch (e: IllegalStateException) {
                throw JsonParseException(
                    "Unable to parse json into type PathArrayWithInteger",
                    e
                )
            } catch (e: NumberFormatException) {
                throw JsonParseException(
                    "Unable to parse json into type PathArrayWithInteger",
                    e
                )
            } catch (e: NullPointerException) {
                throw JsonParseException(
                    "Unable to parse json into type PathArrayWithInteger",
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
         * integer element
         */
        public data class Long(
            public val item: kotlin.Long,
        ) : Path() {
            override fun toJson(): JsonElement = JsonPrimitive(item)

            public companion object {
                @JvmStatic
                @Throws(JsonParseException::class)
                public fun fromJson(jsonString: kotlin.String): Long {
                    val jsonElement = JsonParser.parseString(jsonString)
                    try {
                        return fromJsonPrimitive(jsonElement.asJsonPrimitive)
                    } catch (e: IllegalStateException) {
                        throw JsonParseException("Unable to parse json into type Long", e)
                    }
                }

                @JvmStatic
                @Throws(JsonParseException::class)
                public fun fromJsonPrimitive(jsonPrimitive: JsonPrimitive): Long {
                    try {
                        if (jsonPrimitive.isNumber) {
                            return Long(jsonPrimitive.asLong)
                        } else {
                            throw JsonParseException("Can't convert jsonPrimitive to Long")
                        }
                    } catch (e: IllegalStateException) {
                        throw JsonParseException("Unable to parse json into type Long", e)
                    } catch (e: NumberFormatException) {
                        throw JsonParseException("Unable to parse json into type Long", e)
                    } catch (e: UnsupportedOperationException) {
                        throw JsonParseException("Unable to parse json into type Long", e)
                    }
                }
            }
        }

        /**
         * object element
         */
        public data class Point(
            public val x: kotlin.Long,
            public val y: kotlin.Long,
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
                val asLong = try {
                    if (jsonElement is JsonPrimitive) {
                        Long.fromJsonPrimitive(jsonElement)
                    } else {
                        throw JsonParseException("Unable to parse json into type "
                                 + "kotlin.Long")
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
                    asLong,
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
