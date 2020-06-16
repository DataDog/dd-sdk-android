package com.example.model

import com.google.gson.annotations.SerializedName
import kotlin.String

data class User(
    @SerializedName("username")
    val username: String,
    @SerializedName("host")
    val host: String,
    @SerializedName("firstname")
    val firstname: String?,
    @SerializedName("lastname")
    val lastname: String,
    @SerializedName("contact_type")
    val contactType: ContactType
) {
    enum class ContactType {
        @SerializedName("personal")
        PERSONAL,

        @SerializedName("professional")
        PROFESSIONAL
    }
}
