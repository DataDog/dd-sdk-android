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

data class Customer(
    val name: String? = null,
    val billingAddress: Address? = null,
    val shippingAddress: Address? = null
) {
    fun toJson(): JsonElement {
        val json = JsonObject()
        name?.let { json.addProperty("name", it) }
        billingAddress?.let { json.add("billing_address", it.toJson()) }
        shippingAddress?.let { json.add("shipping_address", it.toJson()) }
        return json
    }

    companion object {
        @JvmStatic
        @Throws(JsonParseException::class)
        fun fromJson(serializedObject: String): Customer {
            try {
                val jsonObject = JsonParser.parseString(serializedObject).asJsonObject
                val name = jsonObject.getAsJsonPrimitive("name")?.asString
                val billingAddress =
                        jsonObject.getAsJsonObject("billing_address")?.toString()?.let {
                    Address.fromJson(it)
                }
                val shippingAddress =
                        jsonObject.getAsJsonObject("shipping_address")?.toString()?.let {
                    Address.fromJson(it)
                }
                return Customer(
                    name,
                    billingAddress,
                    shippingAddress
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
