package com.example.model

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import java.lang.IllegalStateException
import java.lang.NullPointerException
import java.lang.NumberFormatException
import kotlin.String
import kotlin.jvm.JvmStatic
import kotlin.jvm.Throws

public data class UserMerged(
    public val email: String? = null,
    public val phone: String? = null,
    public val info: Info? = null,
    public val firstname: String? = null,
    public val lastname: String
) {
    public fun toJson(): JsonElement {
        val json = JsonObject()
        email?.let { emailNonNull ->
            json.addProperty("email", emailNonNull)
        }
        phone?.let { phoneNonNull ->
            json.addProperty("phone", phoneNonNull)
        }
        info?.let { infoNonNull ->
            json.add("info", infoNonNull.toJson())
        }
        firstname?.let { firstnameNonNull ->
            json.addProperty("firstname", firstnameNonNull)
        }
        json.addProperty("lastname", lastname)
        return json
    }

    public companion object {
        @JvmStatic
        @Throws(JsonParseException::class)
        public fun fromJson(jsonString: String): UserMerged {
            try {
                val jsonObject = JsonParser.parseString(jsonString).asJsonObject
                val email = jsonObject.get("email")?.asString
                val phone = jsonObject.get("phone")?.asString
                val info = jsonObject.get("info")?.toString()?.let {
                    Info.fromJson(it)
                }
                val firstname = jsonObject.get("firstname")?.asString
                val lastname = jsonObject.get("lastname").asString
                return UserMerged(email, phone, info, firstname, lastname)
            } catch (e: IllegalStateException) {
                throw JsonParseException(
                    "Unable to parse json into type UserMerged",
                    e
                )
            } catch (e: NumberFormatException) {
                throw JsonParseException(
                    "Unable to parse json into type UserMerged",
                    e
                )
            } catch (e: NullPointerException) {
                throw JsonParseException(
                    "Unable to parse json into type UserMerged",
                    e
                )
            }
        }
    }

    public data class Info(
        public val notes: String? = null,
        public val source: String? = null
    ) {
        public fun toJson(): JsonElement {
            val json = JsonObject()
            notes?.let { notesNonNull ->
                json.addProperty("notes", notesNonNull)
            }
            source?.let { sourceNonNull ->
                json.addProperty("source", sourceNonNull)
            }
            return json
        }

        public companion object {
            @JvmStatic
            @Throws(JsonParseException::class)
            public fun fromJson(jsonString: String): Info {
                try {
                    val jsonObject = JsonParser.parseString(jsonString).asJsonObject
                    val notes = jsonObject.get("notes")?.asString
                    val source = jsonObject.get("source")?.asString
                    return Info(notes, source)
                } catch (e: IllegalStateException) {
                    throw JsonParseException(
                        "Unable to parse json into type Info",
                        e
                    )
                } catch (e: NumberFormatException) {
                    throw JsonParseException(
                        "Unable to parse json into type Info",
                        e
                    )
                } catch (e: NullPointerException) {
                    throw JsonParseException(
                        "Unable to parse json into type Info",
                        e
                    )
                }
            }
        }
    }
}
