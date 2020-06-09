package com.example.model

import com.google.gson.annotations.SerializedName
import kotlin.String

data class Shipping(
    @SerializedName("item")
    val item: String,
    @SerializedName("destination")
    val destination: Address
) {
    data class Address(
        @SerializedName("street_address")
        val streetAddress: String,
        @SerializedName("city")
        val city: String,
        @SerializedName("state")
        val state: String
    )
}
