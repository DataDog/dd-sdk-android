package com.example.model

import com.google.gson.annotations.SerializedName
import kotlin.String

class Location {
    @SerializedName("planet")
    val planet: String = "earth"
}
