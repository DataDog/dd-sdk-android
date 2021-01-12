package com.example.model

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import java.lang.IllegalStateException
import java.lang.NumberFormatException
import kotlin.String
import kotlin.jvm.JvmStatic
import kotlin.jvm.Throws

data class Shipping(
    val item: String,
    val destination: Address
) {
    fun toJson(): JsonElement {
        val json = JsonObject()
        json.addProperty("item", item)
        json.add("destination", destination.toJson())
        return json
    }

    companion object {
        @JvmStatic
        @Throws(JsonParseException::class)
        fun fromJson(serializedObject: String): Shipping {
            try {
                val jsonObject = JsonParser.parseString(serializedObject).asJsonObject
                val item = jsonObject.getAsJsonPrimitive("item").asString
                val destination = jsonObject.getAsJsonObject("destination").toString().let {
                    Address.fromJson(it)
                }
                return Shipping(
                    item,
                    destination
                )
            } catch(e:IllegalStateException) {
                throw JsonParseException(e.message)
            } catch(e:NumberFormatException) {
                throw JsonParseException(e.message)
            }
        }
    }

    data class Address(
        val streetAddress: String,
        val city: String,
        val state: String
    ) {
        fun toJson(): JsonElement {
            val json = JsonObject()
            json.addProperty("street_address", streetAddress)
            json.addProperty("city", city)
            json.addProperty("state", state)
            return json
        }

        companion object {
            @JvmStatic
            @Throws(JsonParseException::class)
            fun fromJson(serializedObject: String): Address {
                try {
                    val jsonObject = JsonParser.parseString(serializedObject).asJsonObject
                    val streetAddress = jsonObject.getAsJsonPrimitive("street_address").asString
                    val city = jsonObject.getAsJsonPrimitive("city").asString
                    val state = jsonObject.getAsJsonPrimitive("state").asString
                    return Address(
                        streetAddress,
                        city,
                        state
                    )
                } catch(e:IllegalStateException) {
                    throw JsonParseException(e.message)
                } catch(e:NumberFormatException) {
                    throw JsonParseException(e.message)
                }
            }
        }
    }
}
