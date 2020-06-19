package com.example.model

import com.google.gson.annotations.SerializedName

internal data class Style(
    @SerializedName("color")
    val color: Color
) {
    enum class Color {
        @SerializedName("red")
        RED,

        @SerializedName("amber")
        AMBER,

        @SerializedName("green")
        GREEN,

        @SerializedName("dark_blue")
        DARK_BLUE
    }
}
