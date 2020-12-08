package com.example.model

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive

data class Style(
    val color: Color
) {
    internal fun toJson(): JsonElement {
        val json = JsonObject()
        json.add("color", color.toJson())
        return json
    }

    enum class Color {
        RED,

        AMBER,

        GREEN,

        DARK_BLUE,

        LIME_GREEN,

        SUNBURST_YELLOW;

        internal fun toJson(): JsonElement = when (this) {
            RED -> JsonPrimitive("red")
            AMBER -> JsonPrimitive("amber")
            GREEN -> JsonPrimitive("green")
            DARK_BLUE -> JsonPrimitive("dark_blue")
            LIME_GREEN -> JsonPrimitive("lime green")
            SUNBURST_YELLOW -> JsonPrimitive("sunburst-yellow")
        }
    }
}
