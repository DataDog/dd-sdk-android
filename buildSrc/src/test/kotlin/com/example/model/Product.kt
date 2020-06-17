package com.example.model

import com.google.gson.annotations.SerializedName
import kotlin.Double
import kotlin.Long
import kotlin.String

data class Product(
    @SerializedName("productId")
    val productId: Long,
    @SerializedName("productName")
    val productName: String,
    @SerializedName("price")
    val price: Double
)
