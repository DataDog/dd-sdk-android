package com.example.model

import com.google.gson.annotations.SerializedName
import kotlin.String

internal data class UserMerged(
    @SerializedName("email")
    val email: String? = null,
    @SerializedName("phone")
    val phone: String? = null,
    @SerializedName("info")
    val info: Info? = null,
    @SerializedName("firstname")
    val firstname: String? = null,
    @SerializedName("lastname")
    val lastname: String
) {
    data class Info(
        @SerializedName("notes")
        val notes: String? = null,
        @SerializedName("source")
        val source: String? = null
    )
}
