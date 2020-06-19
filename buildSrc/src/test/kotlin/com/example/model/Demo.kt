package com.example.model

import com.google.gson.annotations.SerializedName
import kotlin.Any
import kotlin.Boolean
import kotlin.Double
import kotlin.Long
import kotlin.String

internal data class Demo(
    @SerializedName("s")
    val s: String,
    @SerializedName("i")
    val i: Long,
    @SerializedName("n")
    val n: Double,
    @SerializedName("b")
    val b: Boolean,
    @SerializedName("l")
    val l: Any? = null
)
