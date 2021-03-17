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

data class Version(
    val id: Id,
    val date: Date? = null
) {
    val version: Long = 42L

    val delta: Double = 3.1415

    fun toJson(): JsonElement {
        val json = JsonObject()
        json.addProperty("version", version)
        json.addProperty("delta", delta)
        json.add("id", id.toJson())
        date?.let { json.add("date", it.toJson()) }
        return json
    }

    companion object {
        @JvmStatic
        @Throws(JsonParseException::class)
        fun fromJson(serializedObject: String): Version {
            try {
                val jsonObject = JsonParser.parseString(serializedObject).asJsonObject
                val id=Id()
                val date = jsonObject.get("date")?.toString()?.let {
                    Date()
                }
                return Version(id, date)
            } catch (e: IllegalStateException) {
                throw JsonParseException(e.message)
            } catch (e: NumberFormatException) {
                throw JsonParseException(e.message)
            }
        }
    }

    class Id {
        val serialNumber: Double = 12112.0

        fun toJson(): JsonElement {
            val json = JsonObject()
            json.addProperty("serialNumber", serialNumber)
            return json
        }
    }

    class Date {
        val year: Long = 2021L

        val month: Long = 3L

        fun toJson(): JsonElement {
            val json = JsonObject()
            json.addProperty("year", year)
            json.addProperty("month", month)
            return json
        }
    }
}
