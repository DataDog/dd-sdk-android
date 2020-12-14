package com.example.model

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import kotlin.Double
import kotlin.Long
import kotlin.String

data class Book(
    val bookId: Long,
    val title: String,
    val price: Double,
    val author: Author
) {
    fun toJson(): JsonElement {
        val json = JsonObject()
        json.addProperty("bookId", bookId)
        json.addProperty("title", title)
        json.addProperty("price", price)
        json.add("author", author.toJson())
        return json
    }

    data class Author(
        val firstName: String,
        val lastName: String,
        val contact: Contact
    ) {
        fun toJson(): JsonElement {
            val json = JsonObject()
            json.addProperty("firstName", firstName)
            json.addProperty("lastName", lastName)
            json.add("contact", contact.toJson())
            return json
        }
    }

    data class Contact(
        val phone: String? = null,
        val email: String? = null
    ) {
        fun toJson(): JsonElement {
            val json = JsonObject()
            phone?.let { json.addProperty("phone", it) }
            email?.let { json.addProperty("email", it) }
            return json
        }
    }
}
