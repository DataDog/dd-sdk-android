package com.example.model

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import java.lang.IllegalStateException
import java.lang.NumberFormatException
import kotlin.Long
import kotlin.String
import kotlin.jvm.JvmStatic
import kotlin.jvm.Throws

data class DateTime(
    val date: Date? = null,
    val time: Time? = null
) {
    fun toJson(): JsonElement {
        val json = JsonObject()
        date?.let { json.add("date", it.toJson()) }
        time?.let { json.add("time", it.toJson()) }
        return json
    }

    companion object {
        @JvmStatic
        @Throws(JsonParseException::class)
        fun fromJson(serializedObject: String): DateTime {
            try {
                val jsonObject = JsonParser.parseString(serializedObject).asJsonObject
                val date = jsonObject.get("date")?.toString()?.let {
                    Date.fromJson(it)
                }
                val time = jsonObject.get("time")?.toString()?.let {
                    Time.fromJson(it)
                }
                return DateTime(date, time)
            } catch (e: IllegalStateException) {
                throw JsonParseException(e.message)
            } catch (e: NumberFormatException) {
                throw JsonParseException(e.message)
            }
        }
    }

    data class Date(
        val year: Long? = null,
        val month: Month? = null,
        val day: Long? = null
    ) {
        fun toJson(): JsonElement {
            val json = JsonObject()
            year?.let { json.addProperty("year", it) }
            month?.let { json.add("month", it.toJson()) }
            day?.let { json.addProperty("day", it) }
            return json
        }

        companion object {
            @JvmStatic
            @Throws(JsonParseException::class)
            fun fromJson(serializedObject: String): Date {
                try {
                    val jsonObject = JsonParser.parseString(serializedObject).asJsonObject
                    val year = jsonObject.get("year")?.asLong
                    val month = jsonObject.get("month")?.asString?.let {
                        Month.fromJson(it)
                    }
                    val day = jsonObject.get("day")?.asLong
                    return Date(year, month, day)
                } catch (e: IllegalStateException) {
                    throw JsonParseException(e.message)
                } catch (e: NumberFormatException) {
                    throw JsonParseException(e.message)
                }
            }
        }
    }

    data class Time(
        val hour: Long? = null,
        val minute: Long? = null,
        val seconds: Long? = null
    ) {
        fun toJson(): JsonElement {
            val json = JsonObject()
            hour?.let { json.addProperty("hour", it) }
            minute?.let { json.addProperty("minute", it) }
            seconds?.let { json.addProperty("seconds", it) }
            return json
        }

        companion object {
            @JvmStatic
            @Throws(JsonParseException::class)
            fun fromJson(serializedObject: String): Time {
                try {
                    val jsonObject = JsonParser.parseString(serializedObject).asJsonObject
                    val hour = jsonObject.get("hour")?.asLong
                    val minute = jsonObject.get("minute")?.asLong
                    val seconds = jsonObject.get("seconds")?.asLong
                    return Time(hour, minute, seconds)
                } catch (e: IllegalStateException) {
                    throw JsonParseException(e.message)
                } catch (e: NumberFormatException) {
                    throw JsonParseException(e.message)
                }
            }
        }
    }

    enum class Month(
        private val jsonValue: String
    ) {
        JAN("jan"),

        FEB("feb"),

        MAR("mar"),

        APR("apr"),

        MAY("may"),

        JUN("jun"),

        JUL("jul"),

        AUG("aug"),

        SEP("sep"),

        OCT("oct"),

        NOV("nov"),

        DEC("dec");

        fun toJson(): JsonElement = JsonPrimitive(jsonValue)

        companion object {
            @JvmStatic
            fun fromJson(serializedObject: String): Month = values().first { it.jsonValue ==
                    serializedObject }
        }
    }
}
