package com.example.model

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import java.lang.IllegalStateException
import java.lang.NullPointerException
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
        public fun fromJson(jsonString: String): Product {
            try {
                val jsonObject = JsonParser.parseString(jsonString).asJsonObject
                val productId = jsonObject.get("productId").asLong
                val productName = jsonObject.get("productName").asString
                val price = jsonObject.get("price").asNumber
                return Product(productId, productName, price)
            } catch (e: IllegalStateException) {
                throw JsonParseException(
                    "Unable to parse json into type Product",
                    e
                )
            } catch (e: NumberFormatException) {
                throw JsonParseException(
                    "Unable to parse json into type Product",
                    e
                )
            } catch (e: NullPointerException) {
                throw JsonParseException(
                    "Unable to parse json into type Product",
                    e
                )
            }
        }
    }
}
