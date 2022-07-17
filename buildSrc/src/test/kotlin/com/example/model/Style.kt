package com.example.model

import com.google.gson.JsonElement
import com.google.gson.JsonNull
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

public data class Style(
    public val color: Color,
) {
    public fun toJson(): JsonElement {
        val json = JsonObject()
        json.add("color", color.toJson())
        return json
    }

    public companion object {
        @JvmStatic
        @Throws(JsonParseException::class)
        public fun fromJson(jsonString: String): Style {
            try {
                val jsonObject = JsonParser.parseString(jsonString).asJsonObject
                val jsonColor = jsonObject.get("color")
                val color = if (jsonColor is JsonNull || jsonColor == null) {
                    Color.fromJson(null)
                } else {
                    Color.fromJson(jsonColor.asString)
                }
                return Style(color)
            } catch (e: IllegalStateException) {
                throw JsonParseException(
                    "Unable to parse json into type Style",
                    e
                )
            } catch (e: NumberFormatException) {
                throw JsonParseException(
                    "Unable to parse json into type Style",
                    e
                )
            } catch (e: NullPointerException) {
                throw JsonParseException(
                    "Unable to parse json into type Style",
                    e
                )
            }
        }
    }

    public enum class Color(
        private val jsonValue: String?,
    ) {
        RED("red"),
        AMBER("amber"),
        GREEN("green"),
        DARK_BLUE("dark_blue"),
        LIME_GREEN("lime green"),
        SUNBURST_YELLOW("sunburst-yellow"),
        COLOR_NULL(null),
        ;

        public fun toJson(): JsonElement {
            if (jsonValue == null) {
                return JsonNull.INSTANCE
            } else {
                return JsonPrimitive(jsonValue)
            }
        }

        public companion object {
            @JvmStatic
            public fun fromJson(jsonString: String?): Color = values().first {
                it.jsonValue == jsonString
            }
        }
    }
}
