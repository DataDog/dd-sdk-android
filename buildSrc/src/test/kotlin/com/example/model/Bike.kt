package com.example.model

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import java.lang.IllegalStateException
import java.lang.NullPointerException
import java.lang.NumberFormatException
import kotlin.Boolean
import kotlin.Long
import kotlin.Number
import kotlin.String
import kotlin.Suppress
import kotlin.jvm.JvmStatic
import kotlin.jvm.Throws

@Suppress("StringLiteralDuplication", "EmptyDefaultConstructor", "MagicNumber")
public data class Bike(
    public val productId: Long = 1L,
    public val productName: String,
    public val type: String? = "road",
    public val price: Number = 55.5,
    public val frameMaterial: FrameMaterial? = FrameMaterial.LIGHT_ALUMINIUM,
    public val inStock: Boolean = true,
    public val color: Color = Color.LIME_GREEN,
) {
    @Suppress("CyclomaticComplexMethod", "LongMethod", "ThrowsCount", "TooGenericExceptionCaught")
    public fun toJson(): JsonElement {
        val json = JsonObject()
        json.addProperty("productId", productId)
        json.addProperty("productName", productName)
        type?.let { typeNonNull ->
            json.addProperty("type", typeNonNull)
        }
        json.addProperty("price", price)
        frameMaterial?.let { frameMaterialNonNull ->
            json.add("frameMaterial", frameMaterialNonNull.toJson())
        }
        json.addProperty("inStock", inStock)
        json.add("color", color.toJson())
        return json
    }

    public companion object {
        @JvmStatic
        @Throws(JsonParseException::class)
        public fun fromJson(jsonString: String): Bike {
            try {
                val jsonObject = JsonParser.parseString(jsonString).asJsonObject
                return fromJsonObject(jsonObject)
            } catch (e: IllegalStateException) {
                throw JsonParseException(
                    "Unable to parse json into type Bike",
                    e
                )
            }
        }

        @JvmStatic
        @Suppress("CyclomaticComplexMethod", "LongMethod", "ThrowsCount",
                "TooGenericExceptionCaught")
        @Throws(JsonParseException::class)
        public fun fromJsonObject(jsonObject: JsonObject): Bike {
            try {
                val productId = jsonObject.get("productId").asLong
                val productName = jsonObject.get("productName").asString
                val type = jsonObject.get("type")?.asString
                val price = jsonObject.get("price").asNumber
                val frameMaterial = jsonObject.get("frameMaterial")?.asString?.let {
                    FrameMaterial.fromJson(it)
                }
                val inStock = jsonObject.get("inStock").asBoolean
                val color = Color.fromJson(jsonObject.get("color").asString)
                return Bike(productId, productName, type, price, frameMaterial, inStock, color)
            } catch (e: IllegalStateException) {
                throw JsonParseException(
                    "Unable to parse json into type Bike",
                    e
                )
            } catch (e: NumberFormatException) {
                throw JsonParseException(
                    "Unable to parse json into type Bike",
                    e
                )
            } catch (e: NullPointerException) {
                throw JsonParseException(
                    "Unable to parse json into type Bike",
                    e
                )
            }
        }
    }

    public enum class FrameMaterial(
        private val jsonValue: String,
    ) {
        CARBON("carbon"),
        LIGHT_ALUMINIUM("light_aluminium"),
        IRON("iron"),
        ;

        public fun toJson(): JsonElement = JsonPrimitive(jsonValue)

        public companion object {
            @JvmStatic
            public fun fromJson(jsonString: String): FrameMaterial = values().first {
                it.jsonValue == jsonString
            }
        }
    }

    public enum class Color(
        private val jsonValue: String,
    ) {
        RED("red"),
        AMBER("amber"),
        GREEN("green"),
        DARK_BLUE("dark_blue"),
        LIME_GREEN("lime green"),
        SUNBURST_YELLOW("sunburst-yellow"),
        ;

        public fun toJson(): JsonElement = JsonPrimitive(jsonValue)

        public companion object {
            @JvmStatic
            public fun fromJson(jsonString: String): Color = values().first {
                it.jsonValue == jsonString
            }
        }
    }
}
