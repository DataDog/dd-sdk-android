package com.example.model

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import java.lang.IllegalStateException
import java.lang.NullPointerException
import java.lang.NumberFormatException
import kotlin.String
import kotlin.jvm.JvmStatic
import kotlin.jvm.Throws

public data class User(
    public val username: String,
    public val host: String,
    public val firstname: String? = null,
    public val lastname: String,
    public val contactType: ContactType,
) {
    public fun toJson(): JsonElement {
        val json = JsonObject()
        json.addProperty("username", username)
        json.addProperty("host", host)
        firstname?.let { firstnameNonNull ->
            json.addProperty("firstname", firstnameNonNull)
        }
        json.addProperty("lastname", lastname)
        json.add("contact_type", contactType.toJson())
        return json
    }

    public companion object {
        @JvmStatic
        @Throws(JsonParseException::class)
        public fun fromJson(jsonString: String): User {
            val jsonObject = JsonParser.parseString(jsonString).asJsonObject
            return fromJsonElement(jsonObject)
        }

        @JvmStatic
        @Throws(JsonParseException::class)
        public fun fromJsonElement(jsonObject: JsonObject): User {
            try {
                val username = jsonObject.get("username").asString
                val host = jsonObject.get("host").asString
                val firstname = jsonObject.get("firstname")?.asString
                val lastname = jsonObject.get("lastname").asString
                val contactType = ContactType.fromJson(jsonObject.get("contact_type").asString)
                return User(username, host, firstname, lastname, contactType)
            } catch (e: IllegalStateException) {
                throw JsonParseException(
                    "Unable to parse json into type User",
                    e
                )
            } catch (e: NumberFormatException) {
                throw JsonParseException(
                    "Unable to parse json into type User",
                    e
                )
            } catch (e: NullPointerException) {
                throw JsonParseException(
                    "Unable to parse json into type User",
                    e
                )
            }
        }
    }

    public enum class ContactType(
        private val jsonValue: String,
    ) {
        PERSONAL("personal"),
        PROFESSIONAL("professional"),
        ;

        public fun toJson(): JsonElement = JsonPrimitive(jsonValue)

        public companion object {
            @JvmStatic
            public fun fromJson(jsonString: String): ContactType = values().first {
                it.jsonValue == jsonString
            }
        }
    }
}
