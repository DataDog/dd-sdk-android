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

public data class Version(
    public val id: Id,
    public val date: Date? = null
) {
    public val version: Long = 42L

    public val delta: Number = 3.1415

    public fun toJson(): JsonElement {
        val json = JsonObject()
        json.addProperty("version", version)
        json.addProperty("delta", delta)
        json.add("id", id.toJson())
        date?.let { json.add("date", it.toJson()) }
        return json
    }

    public companion object {
        @JvmStatic
        @Throws(JsonParseException::class)
        public fun fromJson(serializedObject: String): Version {
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

    public class Id() {
        public val serialNumber: Number = 12112.0

        public fun toJson(): JsonElement {
            val json = JsonObject()
            json.addProperty("serialNumber", serialNumber)
            return json
        }
    }

    public class Date() {
        public val year: Long = 2021L

        public val month: Long = 3L

        public fun toJson(): JsonElement {
            val json = JsonObject()
            json.addProperty("year", year)
            json.addProperty("month", month)
            return json
        }
    }
}
