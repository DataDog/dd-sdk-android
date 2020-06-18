package com.example.model

import com.google.gson.annotations.SerializedName
import kotlin.String

data class Conflict(
    @SerializedName("type")
    val type: Type? = null,
    @SerializedName("user")
    val user: User? = null
) {
    data class Type(
        @SerializedName("id")
        val id: String? = null
    )

    data class User(
        @SerializedName("name")
        val name: String? = null,
        @SerializedName("type")
        val type: Type1? = null
    )

    enum class Type1 {
        @SerializedName("unknown")
        UNKNOWN,

        @SerializedName("customer")
        CUSTOMER,

        @SerializedName("partner")
        PARTNER
    }
}
