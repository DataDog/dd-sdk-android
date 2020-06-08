package com.example.model

import com.google.gson.annotations.SerializedName
import kotlin.Int

data class DateTime(
    @SerializedName("date")
    val date: Date?,
    @SerializedName("time")
    val time: Time?
) {
    data class Date(
        @SerializedName("year")
        val year: Int?,
        @SerializedName("month")
        val month: Month?,
        @SerializedName("day")
        val day: Int?
    )

    data class Time(
        @SerializedName("hour")
        val hour: Int?,
        @SerializedName("minute")
        val minute: Int?,
        @SerializedName("seconds")
        val seconds: Int?
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
