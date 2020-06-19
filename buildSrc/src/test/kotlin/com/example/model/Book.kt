package com.example.model

import com.google.gson.annotations.SerializedName
import kotlin.Double
import kotlin.Long
import kotlin.String

internal data class Book(
    @SerializedName("bookId")
    val bookId: Long,
    @SerializedName("title")
    val title: String,
    @SerializedName("price")
    val price: Double,
    @SerializedName("author")
    val author: Author
) {
    data class Author(
        @SerializedName("firstName")
        val firstName: String,
        @SerializedName("lastName")
        val lastName: String,
        @SerializedName("contact")
        val contact: Contact
    )

    data class Contact(
        @SerializedName("phone")
        val phone: String? = null,
        @SerializedName("email")
        val email: String? = null
    )
}
