package com.example.model

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import java.lang.IllegalStateException
import java.lang.NumberFormatException
import kotlin.String
import kotlin.jvm.JvmStatic
import kotlin.jvm.Throws

data class Style(
    val color: Color
) {
    fun toJson(): JsonElement {
        val json = JsonObject()
        json.add("color", color.toJson())
        return json
    }

    companion object {
        @JvmStatic
        @Throws(JsonParseException::class)
        fun fromJson(serializedObject: String): Style {
            try {
                val jsonObject = JsonParser.parseString(serializedObject).asJsonObject
                val color = jsonObject.get("color").asString.let {
                    Color.fromJson(it)
                }
                return Style(color)
            } catch (e: IllegalStateException) {
                throw JsonParseException(e.message)
            } catch (e: NumberFormatException) {
                throw JsonParseException(e.message)
            }
        }
    }

    enum class Color(
        private val jsonValue: String
    ) {
        RED("red"),

        AMBER("amber"),

        GREEN("green"),

        DARK_BLUE("dark_blue"),

        LIME_GREEN("lime green"),

        SUNBURST_YELLOW("sunburst-yellow");

        fun toJson(): JsonElement = JsonPrimitive(jsonValue)

        companion object {
            @JvmStatic
            fun fromJson(serializedObject: String): Color = values().first { it.jsonValue ==
                    serializedObject }
        }
    }
}
