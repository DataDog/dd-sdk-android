package com.example.model

import com.google.gson.annotations.SerializedName
import kotlin.Double
import kotlin.Long

internal class Version {
    @SerializedName("version")
    val version: Long = 42L

    @SerializedName("delta")
    val delta: Double = 3.1415
}
