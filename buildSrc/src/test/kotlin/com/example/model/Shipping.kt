package com.example.model

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import kotlin.String

internal data class Shipping(
    val item: String,
    val destination: Address
) {
    fun toJson(): JsonElement {
        val json = JsonObject()
        json.addProperty("item", item)
        json.add("destination", destination.toJson())
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
