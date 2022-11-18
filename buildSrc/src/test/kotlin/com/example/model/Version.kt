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

public data class Version(
    public val id: Id,
    public val date: Date? = null,
) {
    public val version: Long = 42L

    public val delta: Number = 3.1415

    public fun toJson(): JsonElement {
        val json = JsonObject()
        json.addProperty("version", version)
        json.addProperty("delta", delta)
        json.add("id", id.toJson())
        date?.let { dateNonNull ->
            json.add("date", dateNonNull.toJson())
        }
        return json
    }

    public companion object {
        @JvmStatic
        @Throws(JsonParseException::class)
        public fun fromJson(jsonString: String): Version {
            try {
                val jsonObject = JsonParser.parseString(jsonString).asJsonObject
                return fromJsonObject(jsonObject)
            } catch (e: IllegalStateException) {
                throw JsonParseException(
                    "Unable to parse json into type Version",
                    e
                )
            }
        }

        @JvmStatic
        @Throws(JsonParseException::class)
        public fun fromJsonObject(jsonObject: JsonObject): Version {
            try {
                val id = Id()
                val date = jsonObject.get("date")?.toString()?.let {
                    Date()
                }
                return Version(id, date)
            } catch (e: IllegalStateException) {
                throw JsonParseException(
                    "Unable to parse json into type Version",
                    e
                )
            } catch (e: NumberFormatException) {
                throw JsonParseException(
                    "Unable to parse json into type Version",
                    e
                )
            } catch (e: NullPointerException) {
                throw JsonParseException(
                    "Unable to parse json into type Version",
                    e
                )
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
