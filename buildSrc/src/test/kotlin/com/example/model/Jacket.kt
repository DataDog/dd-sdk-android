package com.example.model

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import java.lang.IllegalStateException
import java.lang.NullPointerException
import java.lang.NumberFormatException
import kotlin.Number
import kotlin.String
import kotlin.Suppress
import kotlin.jvm.JvmStatic
import kotlin.jvm.Throws

@Suppress("StringLiteralDuplication", "EmptyDefaultConstructor", "MagicNumber")
public data class Jacket(
    public val size: Size = Size.SIZE_1,
) {
    @Suppress("CyclomaticComplexMethod", "LongMethod", "ThrowsCount", "TooGenericExceptionCaught")
    public fun toJson(): JsonElement {
        val json = JsonObject()
        json.add("size", size.toJson())
        return json
    }

    public companion object {
        @JvmStatic
        @Throws(JsonParseException::class)
        public fun fromJson(jsonString: String): Jacket {
            try {
                val jsonObject = JsonParser.parseString(jsonString).asJsonObject
                return fromJsonObject(jsonObject)
            } catch (e: IllegalStateException) {
                throw JsonParseException(
                    "Unable to parse json into type Jacket",
                    e
                )
            }
        }

        @JvmStatic
        @Suppress("CyclomaticComplexMethod", "LongMethod", "ThrowsCount",
                "TooGenericExceptionCaught")
        @Throws(JsonParseException::class)
        public fun fromJsonObject(jsonObject: JsonObject): Jacket {
            try {
                val size = Size.fromJson(jsonObject.get("size").asString)
                return Jacket(size)
            } catch (e: IllegalStateException) {
                throw JsonParseException(
                    "Unable to parse json into type Jacket",
                    e
                )
            } catch (e: NumberFormatException) {
                throw JsonParseException(
                    "Unable to parse json into type Jacket",
                    e
                )
            } catch (e: NullPointerException) {
                throw JsonParseException(
                    "Unable to parse json into type Jacket",
                    e
                )
            }
        }
    }

    public enum class Size(
        private val jsonValue: Number,
    ) {
        SIZE_1(1),
        SIZE_2(2),
        SIZE_3(3),
        SIZE_4(4),
        ;

        public fun toJson(): JsonElement = JsonPrimitive(jsonValue)

        public companion object {
            @JvmStatic
            public fun fromJson(jsonString: String): Size = values().first {
                it.jsonValue.toString() == jsonString
            }
        }
    }
}
