package com.example.model

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import kotlin.Long

data class DateTime(
    val date: Date? = null,
    val time: Time? = null
) {
    internal fun toJson(): JsonElement {
        val json = JsonObject()
        date?.let { json.add("date", it.toJson()) }
        time?.let { json.add("time", it.toJson()) }
        return json
    }

    data class Date(
        val year: Long? = null,
        val month: Month? = null,
        val day: Long? = null
    ) {
        internal fun toJson(): JsonElement {
            val json = JsonObject()
            year?.let { json.addProperty("year", it) }
            month?.let { json.add("month", it.toJson()) }
            day?.let { json.addProperty("day", it) }
            return json
        }
    }

    data class Time(
        val hour: Long? = null,
        val minute: Long? = null,
        val seconds: Long? = null
    ) {
        internal fun toJson(): JsonElement {
            val json = JsonObject()
            hour?.let { json.addProperty("hour", it) }
            minute?.let { json.addProperty("minute", it) }
            seconds?.let { json.addProperty("seconds", it) }
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

        internal fun toJson(): JsonElement = when (this) {
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
