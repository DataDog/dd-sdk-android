package com.example.model

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import kotlin.Long

internal data class DateTime(
    val date: Date? = null,
    val time: Time? = null
) {
    fun toJson(): JsonElement {
        val json = JsonObject()
        if (date != null) json.add("date", date.toJson())
        if (time != null) json.add("time", time.toJson())
        return json
    }

    data class Date(
        val year: Long? = null,
        val month: Month? = null,
        val day: Long? = null
    ) {
        fun toJson(): JsonElement {
            val json = JsonObject()
            if (year != null) json.addProperty("year", year)
            if (month != null) json.add("month", month.toJson())
            if (day != null) json.addProperty("day", day)
            return json
        }
    }

    data class Time(
        val hour: Long? = null,
        val minute: Long? = null,
        val seconds: Long? = null
    ) {
        fun toJson(): JsonElement {
            val json = JsonObject()
            if (hour != null) json.addProperty("hour", hour)
            if (minute != null) json.addProperty("minute", minute)
            if (seconds != null) json.addProperty("seconds", seconds)
            return json
        }
    }

    enum class Month {
        JAN,

        FEB,

        MAR,

        APR,

        MAY,

        JUN,

        JUL,

        AUG,

        SEP,

        OCT,

        NOV,

        DEC;

        fun toJson(): JsonElement = when (this) {
            JAN -> JsonPrimitive("jan")
            FEB -> JsonPrimitive("feb")
            MAR -> JsonPrimitive("mar")
            APR -> JsonPrimitive("apr")
            MAY -> JsonPrimitive("may")
            JUN -> JsonPrimitive("jun")
            JUL -> JsonPrimitive("jul")
            AUG -> JsonPrimitive("aug")
            SEP -> JsonPrimitive("sep")
            OCT -> JsonPrimitive("oct")
            NOV -> JsonPrimitive("nov")
            DEC -> JsonPrimitive("dec")
        }
    }
}
