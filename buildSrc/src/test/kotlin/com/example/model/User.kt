package com.example.model

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import java.lang.IllegalStateException
import java.lang.NumberFormatException
import kotlin.String
import kotlin.jvm.JvmStatic
import kotlin.jvm.Throws

data class User(
    val username: String,
    val host: String,
    val firstname: String? = null,
    val lastname: String,
    val contactType: ContactType
) {
    fun toJson(): JsonElement {
        val json = JsonObject()
        json.addProperty("username", username)
        json.addProperty("host", host)
        firstname?.let { json.addProperty("firstname", it) }
        json.addProperty("lastname", lastname)
        json.add("contact_type", contactType.toJson())
        return json
    }

    companion object {
        @JvmStatic
        @Throws(JsonParseException::class)
        fun fromJson(serializedObject: String): User {
            try {
                val jsonObject = JsonParser.parseString(serializedObject).asJsonObject
                val username = jsonObject.get("username").asString
                val host = jsonObject.get("host").asString
                val firstname = jsonObject.get("firstname")?.asString
                val lastname = jsonObject.get("lastname").asString
                val contactType = jsonObject.get("contact_type").asString.let {
                    ContactType.fromJson(it)
                }
                return User(username, host, firstname, lastname, contactType)
            } catch (e: IllegalStateException) {
                throw JsonParseException(e.message)
            } catch (e: NumberFormatException) {
                throw JsonParseException(e.message)
            }
        }
    }

    enum class ContactType(
        private val jsonValue: String
    ) {
        PERSONAL("personal"),

        PROFESSIONAL("professional");

        fun toJson(): JsonElement = JsonPrimitive(jsonValue)

        companion object {
            @JvmStatic
            fun fromJson(serializedObject: String): ContactType = values().first { it.jsonValue ==
                    serializedObject }
        }
    }
}
