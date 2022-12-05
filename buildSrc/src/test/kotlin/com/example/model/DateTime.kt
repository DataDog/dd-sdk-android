package com.example.model

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import java.lang.IllegalStateException
import java.lang.NullPointerException
import java.lang.NumberFormatException
import kotlin.Long
import kotlin.String
import kotlin.Suppress
import kotlin.jvm.JvmStatic
import kotlin.jvm.Throws

@Suppress("StringLiteralDuplication", "EmptyDefaultConstructor", "MagicNumber")
public data class DateTime(
    public val date: Date? = null,
    public val time: Time? = null,
) {
    @Suppress("CyclomaticComplexMethod", "LongMethod", "ThrowsCount", "TooGenericExceptionCaught")
    public fun toJson(): JsonElement {
        val json = JsonObject()
        date?.let { dateNonNull ->
            json.add("date", dateNonNull.toJson())
        }
        time?.let { timeNonNull ->
            json.add("time", timeNonNull.toJson())
        }
        return json
    }

    public companion object {
        @JvmStatic
        @Throws(JsonParseException::class)
        public fun fromJson(jsonString: String): DateTime {
            try {
                val jsonObject = JsonParser.parseString(jsonString).asJsonObject
                return fromJsonObject(jsonObject)
            } catch (e: IllegalStateException) {
                throw JsonParseException(
                    "Unable to parse json into type DateTime",
                    e
                )
            }
        }

        @JvmStatic
        @Suppress("CyclomaticComplexMethod", "LongMethod", "ThrowsCount",
                "TooGenericExceptionCaught")
        @Throws(JsonParseException::class)
        public fun fromJsonObject(jsonObject: JsonObject): DateTime {
            try {
                val date = jsonObject.get("date")?.asJsonObject?.let {
                    Date.fromJsonObject(it)
                }
                val time = jsonObject.get("time")?.asJsonObject?.let {
                    Time.fromJsonObject(it)
                }
                return DateTime(date, time)
            } catch (e: IllegalStateException) {
                throw JsonParseException(
                    "Unable to parse json into type DateTime",
                    e
                )
            } catch (e: NumberFormatException) {
                throw JsonParseException(
                    "Unable to parse json into type DateTime",
                    e
                )
            } catch (e: NullPointerException) {
                throw JsonParseException(
                    "Unable to parse json into type DateTime",
                    e
                )
            }
        }
    }

    public data class Date(
        public val year: Long? = null,
        public val month: Month? = null,
        public val day: Long? = null,
    ) {
        @Suppress("CyclomaticComplexMethod", "LongMethod", "ThrowsCount",
                "TooGenericExceptionCaught")
        public fun toJson(): JsonElement {
            val json = JsonObject()
            year?.let { yearNonNull ->
                json.addProperty("year", yearNonNull)
            }
            month?.let { monthNonNull ->
                json.add("month", monthNonNull.toJson())
            }
            day?.let { dayNonNull ->
                json.addProperty("day", dayNonNull)
            }
            return json
        }

        public companion object {
            @JvmStatic
            @Throws(JsonParseException::class)
            public fun fromJson(jsonString: String): Date {
                try {
                    val jsonObject = JsonParser.parseString(jsonString).asJsonObject
                    return fromJsonObject(jsonObject)
                } catch (e: IllegalStateException) {
                    throw JsonParseException(
                        "Unable to parse json into type Date",
                        e
                    )
                }
            }

            @JvmStatic
            @Suppress("CyclomaticComplexMethod", "LongMethod", "ThrowsCount",
                    "TooGenericExceptionCaught")
            @Throws(JsonParseException::class)
            public fun fromJsonObject(jsonObject: JsonObject): Date {
                try {
                    val year = jsonObject.get("year")?.asLong
                    val month = jsonObject.get("month")?.asString?.let {
                        Month.fromJson(it)
                    }
                    val day = jsonObject.get("day")?.asLong
                    return Date(year, month, day)
                } catch (e: IllegalStateException) {
                    throw JsonParseException(
                        "Unable to parse json into type Date",
                        e
                    )
                } catch (e: NumberFormatException) {
                    throw JsonParseException(
                        "Unable to parse json into type Date",
                        e
                    )
                } catch (e: NullPointerException) {
                    throw JsonParseException(
                        "Unable to parse json into type Date",
                        e
                    )
                }
            }
        }
    }

    public data class Time(
        public val hour: Long? = null,
        public val minute: Long? = null,
        public val seconds: Long? = null,
    ) {
        @Suppress("CyclomaticComplexMethod", "LongMethod", "ThrowsCount",
                "TooGenericExceptionCaught")
        public fun toJson(): JsonElement {
            val json = JsonObject()
            hour?.let { hourNonNull ->
                json.addProperty("hour", hourNonNull)
            }
            minute?.let { minuteNonNull ->
                json.addProperty("minute", minuteNonNull)
            }
            seconds?.let { secondsNonNull ->
                json.addProperty("seconds", secondsNonNull)
            }
            return json
        }

        public companion object {
            @JvmStatic
            @Throws(JsonParseException::class)
            public fun fromJson(jsonString: String): Time {
                try {
                    val jsonObject = JsonParser.parseString(jsonString).asJsonObject
                    return fromJsonObject(jsonObject)
                } catch (e: IllegalStateException) {
                    throw JsonParseException(
                        "Unable to parse json into type Time",
                        e
                    )
                }
            }

            @JvmStatic
            @Suppress("CyclomaticComplexMethod", "LongMethod", "ThrowsCount",
                    "TooGenericExceptionCaught")
            @Throws(JsonParseException::class)
            public fun fromJsonObject(jsonObject: JsonObject): Time {
                try {
                    val hour = jsonObject.get("hour")?.asLong
                    val minute = jsonObject.get("minute")?.asLong
                    val seconds = jsonObject.get("seconds")?.asLong
                    return Time(hour, minute, seconds)
                } catch (e: IllegalStateException) {
                    throw JsonParseException(
                        "Unable to parse json into type Time",
                        e
                    )
                } catch (e: NumberFormatException) {
                    throw JsonParseException(
                        "Unable to parse json into type Time",
                        e
                    )
                } catch (e: NullPointerException) {
                    throw JsonParseException(
                        "Unable to parse json into type Time",
                        e
                    )
                }
            }
        }
    }

    public enum class Month(
        private val jsonValue: String,
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
        DEC("dec"),
        ;

        public fun toJson(): JsonElement = JsonPrimitive(jsonValue)

        public companion object {
            @JvmStatic
            public fun fromJson(jsonString: String): Month = values().first {
                it.jsonValue == jsonString
            }
        }
    }
}
