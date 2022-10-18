package com.example.model

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import java.lang.IllegalStateException
import java.lang.NullPointerException
import java.lang.NumberFormatException
import kotlin.String
import kotlin.collections.HashSet
import kotlin.collections.Set
import kotlin.jvm.JvmStatic
import kotlin.jvm.Throws

public data class Order(
    public val sizes: Set<Size>
) {
    public fun toJson(): JsonElement {
        val json = JsonObject()
        val sizesArray = JsonArray(sizes.size)
        sizes.forEach { sizesArray.add(it.toJson()) }
        json.add("sizes", sizesArray)
        return json
    }

    public companion object {
        @JvmStatic
        @Throws(JsonParseException::class)
        public fun fromJson(jsonString: String): Order {
            try {
                val jsonObject = JsonParser.parseString(jsonString).asJsonObject
                val sizes = jsonObject.get("sizes").asJsonArray.let { jsonArray ->
                    val collection = HashSet<Size>(jsonArray.size())
                    jsonArray.forEach {
                        collection.add(Size.fromJson(it.asString))
                    }
                    collection
                }
                return Order(sizes)
            } catch (e: IllegalStateException) {
                throw JsonParseException(
                    "Unable to parse json into type Order",
                    e
                )
            } catch (e: NumberFormatException) {
                throw JsonParseException(
                    "Unable to parse json into type Order",
                    e
                )
            } catch (e: NullPointerException) {
                throw JsonParseException(
                    "Unable to parse json into type Order",
                    e
                )
            }
        }
    }

    public enum class Size(
        private val jsonValue: String
    ) {
        X_SMALL("x small"),
        SMALL("small"),
        MEDIUM("medium"),
        LARGE("large"),
        X_LARGE("x large")
        ;

        public fun toJson(): JsonElement = JsonPrimitive(jsonValue)

        public companion object {
            @JvmStatic
            public fun fromJson(jsonString: String): Size = values().first {
                it.jsonValue == jsonString
            }
        }
    }
}
