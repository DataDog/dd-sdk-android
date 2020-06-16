package com.example.model

import com.google.gson.annotations.SerializedName
import kotlin.String

data class Delivery(
    @SerializedName("item")
    val item: String,
    @SerializedName("customer")
    val customer: Customer
) {
    data class Customer(
        @SerializedName("name")
        val name: String?,
        @SerializedName("billing_address")
        val billingAddress: Address?,
        @SerializedName("shipping_address")
        val shippingAddress: Address?
    )

    data class Address(
        @SerializedName("street_address")
        val streetAddress: String,
        @SerializedName("city")
        val city: String,
        @SerializedName("state")
        val state: String
    )
}
