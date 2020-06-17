package com.example.model

import com.google.gson.annotations.SerializedName
import kotlin.String

data class Conflict(
    @SerializedName("type")
    val type: Type?,
    @SerializedName("user")
    val user: User?
) {
    data class Type(
        @SerializedName("id")
        val id: String?
    )

    data class User(
        @SerializedName("name")
        val name: String?,
        @SerializedName("type")
        val type: Type1?
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
