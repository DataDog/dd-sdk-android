package com.example.model

import com.google.gson.annotations.SerializedName
import kotlin.String

internal data class Customer(
    @SerializedName("name")
    val name: String? = null,
    @SerializedName("billing_address")
    val billingAddress: Address? = null,
    @SerializedName("shipping_address")
    val shippingAddress: Address? = null
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
