package com.example.model

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
import kotlin.jvm.JvmStatic
import kotlin.jvm.Throws

/**
 * @param from This is a definition of a path
 * @param to This is a definition of a path
 */
public data class OneOfPrimitiveRef(
    public val from: Path,
    public val to: Path? = null,
) {
    public fun toJson(): JsonElement {
        val json = JsonObject()
        json.add("from", from.toJson())
        to?.let { toNonNull ->
            json.add("to", toNonNull.toJson())
        }
        return json
    }

    public companion object {
        @JvmStatic
        @Throws(JsonParseException::class)
        public fun fromJson(jsonString: String): OneOfPrimitiveRef {
            try {
                val jsonObject = JsonParser.parseString(jsonString).asJsonObject
                return fromJsonObject(jsonObject)
            } catch (e: IllegalStateException) {
                throw JsonParseException(
                    "Unable to parse json into type OneOfPrimitiveRef",
                    e
                )
            }
        }

        @JvmStatic
        @Throws(JsonParseException::class)
        public fun fromJsonObject(jsonObject: JsonObject): OneOfPrimitiveRef {
            try {
                val from = jsonObject.get("from").let {
                    Path.fromJsonElement(it)
                }
                val to = jsonObject.get("to")?.let {
                    Path.fromJsonElement(it)
                }
                return OneOfPrimitiveRef(from, to)
            } catch (e: IllegalStateException) {
                throw JsonParseException(
                    "Unable to parse json into type OneOfPrimitiveRef",
                    e
                )
            } catch (e: NumberFormatException) {
                throw JsonParseException(
                    "Unable to parse json into type OneOfPrimitiveRef",
                    e
                )
            } catch (e: NullPointerException) {
                throw JsonParseException(
                    "Unable to parse json into type OneOfPrimitiveRef",
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
                val result = arrayOf(
                    asString,
                    asLong,
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
