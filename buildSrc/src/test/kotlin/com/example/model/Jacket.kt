package com.example.model

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import java.lang.IllegalStateException
import java.lang.NumberFormatException
import kotlin.Number
import kotlin.String
import kotlin.jvm.JvmStatic
import kotlin.jvm.Throws

data class Jacket(
    val size: Size = Size.SIZE_1
) {
    fun toJson(): JsonElement {
        val json = JsonObject()
        json.add("size", size.toJson())
        return json
    }

    companion object {
        @JvmStatic
        @Throws(JsonParseException::class)
        fun fromJson(serializedObject: String): Jacket {
            try {
                val jsonObject = JsonParser.parseString(serializedObject).asJsonObject
                val size = jsonObject.get("size").asString.let {
                    Size.fromJson(it)
                }
                return Jacket(size)
            } catch (e: IllegalStateException) {
                throw JsonParseException(e.message)
            } catch (e: NumberFormatException) {
                throw JsonParseException(e.message)
            }
        }
    }

    enum class Size(
        private val jsonValue: Number
    ) {
        SIZE_1(1),

        SIZE_2(2),

        SIZE_3(3),

        SIZE_4(4);

        fun toJson(): JsonElement = JsonPrimitive(jsonValue)

        companion object {
            @JvmStatic
            fun fromJson(serializedObject: String): Size = values().first {
                it.jsonValue.toString() == serializedObject
            }
        }
    }
}
