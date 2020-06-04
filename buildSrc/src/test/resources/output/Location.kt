package com.example.model

import com.google.gson.annotations.SerializedName

data class Location(
    @SerializedName("planet")
    val planet: Planet
)

enum class Planet {
    @SerializedName("earth")
    EARTH
}
