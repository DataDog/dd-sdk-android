package com.example.model

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import java.lang.IllegalStateException
import java.lang.NullPointerException
import java.lang.NumberFormatException
import kotlin.Long
import kotlin.Number
import kotlin.String
import kotlin.jvm.JvmStatic
import kotlin.jvm.Throws

public data class Book(
    public val bookId: Long,
    public val title: String,
    public val price: Number,
    public val author: Author,
) {
    public fun toJson(): JsonElement {
        val json = JsonObject()
        json.addProperty("bookId", bookId)
        json.addProperty("title", title)
        json.addProperty("price", price)
        json.add("author", author.toJson())
        return json
    }

    public companion object {
        @JvmStatic
        @Throws(JsonParseException::class)
        public fun fromJson(jsonString: String): Book {
            try {
                val jsonObject = JsonParser.parseString(jsonString).asJsonObject
                val bookId = jsonObject.get("bookId").asLong
                val title = jsonObject.get("title").asString
                val price = jsonObject.get("price").asNumber
                val author = jsonObject.get("author").toString().let {
                    Author.fromJson(it)
                }
                return Book(bookId, title, price, author)
            } catch (e: IllegalStateException) {
                throw JsonParseException("Unable to parse json into type Book", e)
            } catch (e: NumberFormatException) {
                throw JsonParseException("Unable to parse json into type Book", e)
            } catch (e: NullPointerException) {
                throw JsonParseException("Unable to parse json into type Book", e)
            }
        }
    }

    public data class Author(
        public val firstName: String,
        public val lastName: String,
        public val contact: Contact,
    ) {
        public fun toJson(): JsonElement {
            val json = JsonObject()
            json.addProperty("firstName", firstName)
            json.addProperty("lastName", lastName)
            json.add("contact", contact.toJson())
            return json
        }

        public companion object {
            @JvmStatic
            @Throws(JsonParseException::class)
            public fun fromJson(jsonString: String): Author {
                try {
                    val jsonObject = JsonParser.parseString(jsonString).asJsonObject
                    val firstName = jsonObject.get("firstName").asString
                    val lastName = jsonObject.get("lastName").asString
                    val contact = jsonObject.get("contact").toString().let {
                        Contact.fromJson(it)
                    }
                    return Author(firstName, lastName, contact)
                } catch (e: IllegalStateException) {
                    throw JsonParseException("Unable to parse json into type Author", e)
                } catch (e: NumberFormatException) {
                    throw JsonParseException("Unable to parse json into type Author", e)
                } catch (e: NullPointerException) {
                    throw JsonParseException("Unable to parse json into type Author", e)
                }
            }
        }
    }

    public data class Contact(
        public val phone: String? = null,
        public val email: String? = null,
    ) {
        public fun toJson(): JsonElement {
            val json = JsonObject()
            phone?.let { phoneNonNull ->
                json.addProperty("phone", phoneNonNull)
            }
            email?.let { emailNonNull ->
                json.addProperty("email", emailNonNull)
            }
            return json
        }

        public companion object {
            @JvmStatic
            @Throws(JsonParseException::class)
            public fun fromJson(jsonString: String): Contact {
                try {
                    val jsonObject = JsonParser.parseString(jsonString).asJsonObject
                    val phone = jsonObject.get("phone")?.asString
                    val email = jsonObject.get("email")?.asString
                    return Contact(phone, email)
                } catch (e: IllegalStateException) {
                    throw JsonParseException("Unable to parse json into type Contact", e)
                } catch (e: NumberFormatException) {
                    throw JsonParseException("Unable to parse json into type Contact", e)
                } catch (e: NullPointerException) {
                    throw JsonParseException("Unable to parse json into type Contact", e)
                }
            }
        }
    }
}
