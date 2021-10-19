package com.example.model

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import java.lang.IllegalStateException
import java.lang.NumberFormatException
import kotlin.Long
import kotlin.Number
import kotlin.String
import kotlin.jvm.JvmStatic
import kotlin.jvm.Throws

public data class Product(
    public val productId: Long,
    public val productName: String,
    public val price: Number
) {
    public fun toJson(): JsonElement {
        val json = JsonObject()
        json.addProperty("productId", productId)
        json.addProperty("productName", productName)
        json.addProperty("price", price)
        return json
    }

    public companion object {
        @JvmStatic
        @Throws(JsonParseException::class)
        public fun fromJson(serializedObject: String): Product {
            try {
                val jsonObject = JsonParser.parseString(serializedObject).asJsonObject
                val productId = jsonObject.get("productId").asLong
                val productName = jsonObject.get("productName").asString
                val price = jsonObject.get("price").asNumber
                return Product(productId, productName, price)
            } catch (e: IllegalStateException) {
                throw JsonParseException(e.message)
            } catch (e: NumberFormatException) {
                throw JsonParseException(e.message)
            }
        }
    }
}
