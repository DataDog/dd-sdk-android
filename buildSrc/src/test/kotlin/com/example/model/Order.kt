package com.example.model

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import java.lang.IllegalStateException
import java.lang.NumberFormatException
import kotlin.String
import kotlin.collections.HashSet
import kotlin.collections.Set
import kotlin.jvm.JvmStatic
import kotlin.jvm.Throws

data class Order(
    val sizes: Set<Size>
) {
    fun toJson(): JsonElement {
        val json = JsonObject()
        val sizesArray = JsonArray(sizes.size)
        sizes.forEach { sizesArray.add(it.toJson()) }
        json.add("sizes", sizesArray)
        return json
    }

    companion object {
        @JvmStatic
        @Throws(JsonParseException::class)
        fun fromJson(serializedObject: String): Order {
            try {
                val jsonObject = JsonParser.parseString(serializedObject).asJsonObject
                val sizes = jsonObject.get("sizes").asJsonArray.let {
                    jsonArray -> 
                    val collection = HashSet<Size>(jsonArray.size())
                    jsonArray.forEach {
                        collection.add(Size.fromJson(it.asString))
                    }
                    collection
                }
                return Order(
                    sizes
                )
            } catch(e:IllegalStateException) {
                throw JsonParseException(e.message)
            } catch(e:NumberFormatException) {
                throw JsonParseException(e.message)
            }
        }
    }

    enum class Size(
        private val jsonValue: String
    ) {
        X_SMALL("x small"),

        SMALL("small"),

        MEDIUM("medium"),

        LARGE("large"),

        X_LARGE("x large");

        fun toJson(): JsonElement = JsonPrimitive(jsonValue)

        companion object {
            @JvmStatic
            fun fromJson(serializedObject: String): Size = values().first{it.jsonValue ==
                    serializedObject}
        }
    }
}
