package com.example.model

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import java.lang.IllegalStateException
import java.lang.NumberFormatException
import kotlin.String
import kotlin.jvm.JvmStatic
import kotlin.jvm.Throws

data class UserMerged(
    val email: String? = null,
    val phone: String? = null,
    val info: Info? = null,
    val firstname: String? = null,
    val lastname: String
) {
    fun toJson(): JsonElement {
        val json = JsonObject()
        email?.let { json.addProperty("email", it) }
        phone?.let { json.addProperty("phone", it) }
        info?.let { json.add("info", it.toJson()) }
        firstname?.let { json.addProperty("firstname", it) }
        json.addProperty("lastname", lastname)
        return json
    }

    companion object {
        @JvmStatic
        @Throws(JsonParseException::class)
        fun fromJson(serializedObject: String): UserMerged {
            try {
                val jsonObject = JsonParser.parseString(serializedObject).asJsonObject
                val email = jsonObject.get("email")?.asString
                val phone = jsonObject.get("phone")?.asString
                val info = jsonObject.get("info")?.toString()?.let {
                    Info.fromJson(it)
                }
                val firstname = jsonObject.get("firstname")?.asString
                val lastname = jsonObject.get("lastname").asString
                return UserMerged(email, phone, info, firstname, lastname)
            } catch (e: IllegalStateException) {
                throw JsonParseException(e.message)
            } catch (e: NumberFormatException) {
                throw JsonParseException(e.message)
            }
        }
    }

    data class Info(
        val notes: String? = null,
        val source: String? = null
    ) {
        fun toJson(): JsonElement {
            val json = JsonObject()
            notes?.let { json.addProperty("notes", it) }
            source?.let { json.addProperty("source", it) }
            return json
        }

        companion object {
            @JvmStatic
            @Throws(JsonParseException::class)
            fun fromJson(serializedObject: String): Info {
                try {
                    val jsonObject = JsonParser.parseString(serializedObject).asJsonObject
                    val notes = jsonObject.get("notes")?.asString
                    val source = jsonObject.get("source")?.asString
                    return Info(notes, source)
                } catch (e: IllegalStateException) {
                    throw JsonParseException(e.message)
                } catch (e: NumberFormatException) {
                    throw JsonParseException(e.message)
                }
            }
        }
    }
}
