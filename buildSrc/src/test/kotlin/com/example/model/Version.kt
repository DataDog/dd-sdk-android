package com.example.model

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import java.lang.IllegalStateException
import java.lang.NumberFormatException
import kotlin.Double
import kotlin.Long
import kotlin.String
import kotlin.jvm.JvmStatic
import kotlin.jvm.Throws

class Version {
    val version: Long = 42L

    val delta: Double = 3.1415

    fun toJson(): JsonElement {
        val json = JsonObject()
        json.addProperty("version", version)
        json.addProperty("delta", delta)
        return json
    }

    companion object {
        @JvmStatic
        @Throws(JsonParseException::class)
        fun fromJson(serializedObject: String): Version {
            try {
                val jsonObject = JsonParser.parseString(serializedObject).asJsonObject
                return Version()
            } catch (e: IllegalStateException) {
                throw JsonParseException(e.message)
            } catch (e: NumberFormatException) {
                throw JsonParseException(e.message)
            }
        }
    }
}
