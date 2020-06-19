package com.example.model

import com.google.gson.annotations.SerializedName
import kotlin.Long

internal data class DateTime(
    @SerializedName("date")
    val date: Date? = null,
    @SerializedName("time")
    val time: Time? = null
) {
    data class Date(
        @SerializedName("year")
        val year: Long? = null,
        @SerializedName("month")
        val month: Month? = null,
        @SerializedName("day")
        val day: Long? = null
    )

    data class Time(
        @SerializedName("hour")
        val hour: Long? = null,
        @SerializedName("minute")
        val minute: Long? = null,
        @SerializedName("seconds")
        val seconds: Long? = null
    )

    enum class Month {
        @SerializedName("jan")
        JAN,

        @SerializedName("feb")
        FEB,

        @SerializedName("mar")
        MAR,

        @SerializedName("apr")
        APR,

        @SerializedName("may")
        MAY,

        @SerializedName("jun")
        JUN,

        @SerializedName("jul")
        JUL,

        @SerializedName("aug")
        AUG,

        @SerializedName("sep")
        SEP,

        @SerializedName("oct")
        OCT,

        @SerializedName("nov")
        NOV,

        @SerializedName("dec")
        DEC
    }
}
