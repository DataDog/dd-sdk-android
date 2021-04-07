package com.example.model

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import java.lang.IllegalStateException
import java.lang.NumberFormatException
import kotlin.Boolean
import kotlin.Long
import kotlin.Number
import kotlin.String
import kotlin.jvm.JvmStatic
import kotlin.jvm.Throws

data class Bike(
    val productId: Long = 1L,
    val productName: String,
    val type: String? = "road",
    val price: Number = 55.5,
    val frameMaterial: FrameMaterial? = FrameMaterial.LIGHT_ALUMINIUM,
    val inStock: Boolean = true,
    val color: Color = Color.LIME_GREEN
) {
    fun toJson(): JsonElement {
        val json = JsonObject()
        json.addProperty("productId", productId)
        json.addProperty("productName", productName)
        type?.let { json.addProperty("type", it) }
        json.addProperty("price", price)
        frameMaterial?.let { json.add("frameMaterial", it.toJson()) }
        json.addProperty("inStock", inStock)
        json.add("color", color.toJson())
        return json
    }

    companion object {
        @JvmStatic
        @Throws(JsonParseException::class)
        fun fromJson(serializedObject: String): Bike {
            try {
                val jsonObject = JsonParser.parseString(serializedObject).asJsonObject
                val productId = jsonObject.get("productId").asLong
                val productName = jsonObject.get("productName").asString
                val type = jsonObject.get("type")?.asString
                val price = jsonObject.get("price").asNumber
                val frameMaterial = jsonObject.get("frameMaterial")?.asString?.let {
                    FrameMaterial.fromJson(it)
                }
                val inStock = jsonObject.get("inStock").asBoolean
                val color = jsonObject.get("color").asString.let {
                    Color.fromJson(it)
                }
                return Bike(productId, productName, type, price, frameMaterial, inStock, color)
            } catch (e: IllegalStateException) {
                throw JsonParseException(e.message)
            } catch (e: NumberFormatException) {
                throw JsonParseException(e.message)
            }
        }
    }

    enum class FrameMaterial(
        private val jsonValue: String
    ) {
        CARBON("carbon"),

        LIGHT_ALUMINIUM("light_aluminium"),

        IRON("iron");

        fun toJson(): JsonElement = JsonPrimitive(jsonValue)

        companion object {
            @JvmStatic
            fun fromJson(serializedObject: String): FrameMaterial = values().first { it.jsonValue ==
                    serializedObject }
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
