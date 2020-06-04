package com.example.model

import com.google.gson.annotations.SerializedName
import kotlin.Double
import kotlin.Int
import kotlin.String

data class Product(
    @SerializedName("productId")
    val productId: Int,
    @SerializedName("productName")
    val productName: String,
    @SerializedName("price")
    val price: Double
)
