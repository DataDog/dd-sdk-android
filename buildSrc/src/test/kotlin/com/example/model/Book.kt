package com.example.model

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import java.lang.IllegalStateException
import java.lang.NumberFormatException
import kotlin.Double
import kotlin.Long
import kotlin.String
import kotlin.jvm.JvmStatic
import kotlin.jvm.Throws

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

    companion object {
        @JvmStatic
        @Throws(JsonParseException::class)
        fun fromJson(serializedObject: String): Book {
            try {
                val jsonObject = JsonParser.parseString(serializedObject).asJsonObject
                val bookId = jsonObject.getAsJsonPrimitive("bookId").asLong
                val title = jsonObject.getAsJsonPrimitive("title").asString
                val price = jsonObject.getAsJsonPrimitive("price").asDouble
                val author = jsonObject.getAsJsonObject("author").toString().let {
                    Author.fromJson(it)
                }
                return Book(
                    bookId,
                    title,
                    price,
                    author
                )
            } catch(e:IllegalStateException) {
                throw JsonParseException(e.message)
            } catch(e:NumberFormatException) {
                throw JsonParseException(e.message)
            }
        }
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

        companion object {
            @JvmStatic
            @Throws(JsonParseException::class)
            fun fromJson(serializedObject: String): Author {
                try {
                    val jsonObject = JsonParser.parseString(serializedObject).asJsonObject
                    val firstName = jsonObject.getAsJsonPrimitive("firstName").asString
                    val lastName = jsonObject.getAsJsonPrimitive("lastName").asString
                    val contact = jsonObject.getAsJsonObject("contact").toString().let {
                        Contact.fromJson(it)
                    }
                    return Author(
                        firstName,
                        lastName,
                        contact
                    )
                } catch(e:IllegalStateException) {
                    throw JsonParseException(e.message)
                } catch(e:NumberFormatException) {
                    throw JsonParseException(e.message)
                }
            }
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

        companion object {
            @JvmStatic
            @Throws(JsonParseException::class)
            fun fromJson(serializedObject: String): Contact {
                try {
                    val jsonObject = JsonParser.parseString(serializedObject).asJsonObject
                    val phone = jsonObject.getAsJsonPrimitive("phone")?.asString
                    val email = jsonObject.getAsJsonPrimitive("email")?.asString
                    return Contact(
                        phone,
                        email
                    )
                } catch(e:IllegalStateException) {
                    throw JsonParseException(e.message)
                } catch(e:NumberFormatException) {
                    throw JsonParseException(e.message)
                }
            }
        }
    }
}
