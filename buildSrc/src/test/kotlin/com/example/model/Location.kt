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

class Location {
    val planet: String = "earth"

    fun toJson(): JsonElement {
        val json = JsonObject()
        json.addProperty("planet", planet)
        return json
    }

    companion object {
        @JvmStatic
        @Throws(JsonParseException::class)
        fun fromJson(serializedObject: String): Location {
            try {
                val jsonObject = JsonParser.parseString(serializedObject).asJsonObject
                return Location()
            } catch (e: IllegalStateException) {
                throw JsonParseException(e.message)
            } catch (e: NumberFormatException) {
                throw JsonParseException(e.message)
            }
        }
    }
}
