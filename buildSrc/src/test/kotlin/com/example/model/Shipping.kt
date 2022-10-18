package com.example.model

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import java.lang.IllegalStateException
import java.lang.NullPointerException
import java.lang.NumberFormatException
import kotlin.String
import kotlin.jvm.JvmStatic
import kotlin.jvm.Throws

public data class Shipping(
    public val item: String,
    public val destination: Address
) {
    public fun toJson(): JsonElement {
        val json = JsonObject()
        json.addProperty("item", item)
        json.add("destination", destination.toJson())
        return json
    }

    public companion object {
        @JvmStatic
        @Throws(JsonParseException::class)
        public fun fromJson(jsonString: String): Shipping {
            try {
                val jsonObject = JsonParser.parseString(jsonString).asJsonObject
                val item = jsonObject.get("item").asString
                val destination = jsonObject.get("destination").toString().let {
                    Address.fromJson(it)
                }
                return Shipping(item, destination)
            } catch (e: IllegalStateException) {
                throw JsonParseException(
                    "Unable to parse json into type Shipping",
                    e
                )
            } catch (e: NumberFormatException) {
                throw JsonParseException(
                    "Unable to parse json into type Shipping",
                    e
                )
            } catch (e: NullPointerException) {
                throw JsonParseException(
                    "Unable to parse json into type Shipping",
                    e
                )
            }
        }
    }

    public data class Address(
        public val streetAddress: String,
        public val city: String,
        public val state: String
    ) {
        public fun toJson(): JsonElement {
            val json = JsonObject()
            json.addProperty("street_address", streetAddress)
            json.addProperty("city", city)
            json.addProperty("state", state)
            return json
        }

        public companion object {
            @JvmStatic
            @Throws(JsonParseException::class)
            public fun fromJson(jsonString: String): Address {
                try {
                    val jsonObject = JsonParser.parseString(jsonString).asJsonObject
                    val streetAddress = jsonObject.get("street_address").asString
                    val city = jsonObject.get("city").asString
                    val state = jsonObject.get("state").asString
                    return Address(streetAddress, city, state)
                } catch (e: IllegalStateException) {
                    throw JsonParseException(
                        "Unable to parse json into type Address",
                        e
                    )
                } catch (e: NumberFormatException) {
                    throw JsonParseException(
                        "Unable to parse json into type Address",
                        e
                    )
                } catch (e: NullPointerException) {
                    throw JsonParseException(
                        "Unable to parse json into type Address",
                        e
                    )
                }
            }
        }
    }
}
