package com.example.model

import com.google.gson.annotations.SerializedName
import kotlin.Long

data class DateTime(
    @SerializedName("date")
    val date: Date?,
    @SerializedName("time")
    val time: Time?
) {
    data class Date(
        @SerializedName("year")
        val year: Long?,
        @SerializedName("month")
        val month: Month?,
        @SerializedName("day")
        val day: Long?
    )

    data class Time(
        @SerializedName("hour")
        val hour: Long?,
        @SerializedName("minute")
        val minute: Long?,
        @SerializedName("seconds")
        val seconds: Long?
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
