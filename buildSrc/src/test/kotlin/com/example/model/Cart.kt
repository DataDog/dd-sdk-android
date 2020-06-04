package com.example.model

import com.google.gson.annotations.SerializedName
import kotlin.String
import kotlin.collections.List

data class Cart(
    @SerializedName("fruits")
    val fruits: List<String>?,
    @SerializedName("vegetables")
    val vegetables: List<Veggie>?
)

data class Veggie(
    @SerializedName("veggieName")
    val veggieName: String,
    @SerializedName("veggieLike")
    val veggieLike: Boolean
)
