package com.example.model

import com.google.gson.annotations.SerializedName
import kotlin.String

data class Customer(
    @SerializedName("name")
    val name: String?,
    @SerializedName("billing_address")
    val billing_address: Address?,
    @SerializedName("shipping_address")
    val shipping_address: Address?
)

data class Address(
    @SerializedName("street_address")
    val street_address: String,
    @SerializedName("city")
    val city: String,
    @SerializedName("state")
    val state: String
)
