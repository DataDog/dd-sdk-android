package com.example.model

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import kotlin.Double
import kotlin.Long
import kotlin.String

internal data class Product(
    val productId: Long,
    val productName: String,
    val price: Double
) {
    fun toJson(): JsonElement {
        val json = JsonObject()
        json.addProperty("productId", productId)
        json.addProperty("productName", productName)
        json.addProperty("price", price)
        return json
    }
}
