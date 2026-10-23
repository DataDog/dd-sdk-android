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
import kotlin.collections.Map
import kotlin.collections.MutableMap
import kotlin.jvm.JvmStatic
import kotlin.jvm.Throws

public data class ExplicitArguments(
    public val optional: String?,
    public val bike: Bike?,
    public val attributes: Attributes?,
    public val mutableAttributes: MutableAttributes?,
) {
    public val type: String = "explicit"

    public fun toJson(): JsonElement {
        val json = JsonObject()
        optional?.let { optionalNonNull ->
            json.addProperty("optional", optionalNonNull)
        }
        bike?.let { bikeNonNull ->
            json.add("bike", bikeNonNull.toJson())
        }
        attributes?.let { attributesNonNull ->
            json.add("attributes", attributesNonNull.toJson())
        }
        mutableAttributes?.let { mutableAttributesNonNull ->
            json.add("mutableAttributes", mutableAttributesNonNull.toJson())
        }
        json.addProperty("type", type)
        return json
    }

    public companion object {
        @JvmStatic
        @Throws(JsonParseException::class)
        public fun fromJson(jsonString: String): ExplicitArguments {
            try {
                val jsonObject = JsonParser.parseString(jsonString).asJsonObject
                return fromJsonObject(jsonObject)
            } catch (e: IllegalStateException) {
                throw JsonParseException(
                    "Unable to parse json into type ExplicitArguments",
                    e
                )
            }
        }

        @JvmStatic
        @Throws(JsonParseException::class)
        public fun fromJsonObject(jsonObject: JsonObject): ExplicitArguments {
            try {
                val optional = jsonObject.get("optional")?.asString
                val bike = jsonObject.get("bike")?.asJsonObject?.let {
                    Bike.fromJsonObject(it)
                }
                val attributes = jsonObject.get("attributes")?.asJsonObject?.let {
                    Attributes.fromJsonObject(it)
                }
                val mutableAttributes = jsonObject.get("mutableAttributes")?.asJsonObject?.let {
                    MutableAttributes.fromJsonObject(it)
                }
                val type = jsonObject.get("type")?.asString
                if (type != null) {
                    check(type == "explicit")
                }
                return ExplicitArguments(optional, bike, attributes, mutableAttributes)
            } catch (e: IllegalStateException) {
                throw JsonParseException(
                    "Unable to parse json into type ExplicitArguments",
                    e
                )
            } catch (e: NumberFormatException) {
                throw JsonParseException(
                    "Unable to parse json into type ExplicitArguments",
                    e
                )
            } catch (e: NullPointerException) {
                throw JsonParseException(
                    "Unable to parse json into type ExplicitArguments",
                    e
                )
            }
        }
    }

    public data class Bike(
        public val productId: Long,
        public val productName: String,
        public val type: String?,
        public val price: Number,
        public val frameMaterial: FrameMaterial?,
        public val inStock: Boolean,
        public val color: Color,
    ) {
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
    }

    public data class Attributes(
        public val additionalProperties: Map<String, String>,
    ) {
        public fun toJson(): JsonElement {
            val json = JsonObject()
            additionalProperties.forEach { (k, v) ->
                json.addProperty(k, v)
            }
            return json
        }

        public companion object {
            @JvmStatic
            @Throws(JsonParseException::class)
            public fun fromJson(jsonString: String): Attributes {
                try {
                    val jsonObject = JsonParser.parseString(jsonString).asJsonObject
                    return fromJsonObject(jsonObject)
                } catch (e: IllegalStateException) {
                    throw JsonParseException(
                        "Unable to parse json into type Attributes",
                        e
                    )
                }
            }

            @JvmStatic
            @Throws(JsonParseException::class)
            public fun fromJsonObject(jsonObject: JsonObject): Attributes {
                try {
                    val additionalProperties = mutableMapOf<String, String>()
                    for (entry in jsonObject.entrySet()) {
                        additionalProperties[entry.key] = entry.value.asString
                    }
                    return Attributes(additionalProperties)
                } catch (e: IllegalStateException) {
                    throw JsonParseException(
                        "Unable to parse json into type Attributes",
                        e
                    )
                } catch (e: NumberFormatException) {
                    throw JsonParseException(
                        "Unable to parse json into type Attributes",
                        e
                    )
                } catch (e: NullPointerException) {
                    throw JsonParseException(
                        "Unable to parse json into type Attributes",
                        e
                    )
                }
            }
        }
    }

    public data class MutableAttributes(
        public val additionalProperties: MutableMap<String, String>,
    ) {
        public fun toJson(): JsonElement {
            val json = JsonObject()
            additionalProperties.forEach { (k, v) ->
                json.addProperty(k, v)
            }
            return json
        }

        public companion object {
            @JvmStatic
            @Throws(JsonParseException::class)
            public fun fromJson(jsonString: String): MutableAttributes {
                try {
                    val jsonObject = JsonParser.parseString(jsonString).asJsonObject
                    return fromJsonObject(jsonObject)
                } catch (e: IllegalStateException) {
                    throw JsonParseException(
                        "Unable to parse json into type MutableAttributes",
                        e
                    )
                }
            }

            @JvmStatic
            @Throws(JsonParseException::class)
            public fun fromJsonObject(jsonObject: JsonObject): MutableAttributes {
                try {
                    val additionalProperties = mutableMapOf<String, String>()
                    for (entry in jsonObject.entrySet()) {
                        additionalProperties[entry.key] = entry.value.asString
                    }
                    return MutableAttributes(additionalProperties)
                } catch (e: IllegalStateException) {
                    throw JsonParseException(
                        "Unable to parse json into type MutableAttributes",
                        e
                    )
                } catch (e: NumberFormatException) {
                    throw JsonParseException(
                        "Unable to parse json into type MutableAttributes",
                        e
                    )
                } catch (e: NullPointerException) {
                    throw JsonParseException(
                        "Unable to parse json into type MutableAttributes",
                        e
                    )
                }
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
            public fun fromJson(jsonString: String): FrameMaterial = entries.first {
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
            public fun fromJson(jsonString: String): Color = entries.first {
                it.jsonValue == jsonString
            }
        }
    }
}
