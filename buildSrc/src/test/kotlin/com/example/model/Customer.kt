package com.example.model

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import kotlin.String

internal data class Customer(
    val name: String? = null,
    val billingAddress: Address? = null,
    val shippingAddress: Address? = null
) {
    fun toJson(): JsonElement {
        val json = JsonObject()
        if (name != null) json.addProperty("name", name)
        if (billingAddress != null) json.add("billing_address", billingAddress.toJson())
        if (shippingAddress != null) json.add("shipping_address", shippingAddress.toJson())
        return json
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
    }
}
