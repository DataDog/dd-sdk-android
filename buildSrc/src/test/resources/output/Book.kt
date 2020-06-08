package com.example.model

import com.google.gson.annotations.SerializedName
import kotlin.Double
import kotlin.Int
import kotlin.String

data class Book(
    @SerializedName("bookId")
    val bookId: Int,
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
        val phone: String?,
        @SerializedName("email")
        val email: String?
    )
}
