package com.example.model

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import java.lang.IllegalStateException
import java.lang.NullPointerException
import java.lang.NumberFormatException
import kotlin.Long
import kotlin.String
import kotlin.jvm.JvmStatic
import kotlin.jvm.Throws

public data class Person(
    public val firstName: String? = null,
    public val lastName: String? = null,
    public val age: Long? = null
) {
    public fun toJson(): JsonElement {
        val json = JsonObject()
        firstName?.let { firstNameNonNull ->
            json.addProperty("firstName", firstNameNonNull)
        }
        lastName?.let { lastNameNonNull ->
            json.addProperty("lastName", lastNameNonNull)
        }
        age?.let { ageNonNull ->
            json.addProperty("age", ageNonNull)
        }
        return json
    }

    public companion object {
        @JvmStatic
        @Throws(JsonParseException::class)
        public fun fromJson(jsonString: String): Person {
            try {
                val jsonObject = JsonParser.parseString(jsonString).asJsonObject
                val firstName = jsonObject.get("firstName")?.asString
                val lastName = jsonObject.get("lastName")?.asString
                val age = jsonObject.get("age")?.asLong
                return Person(firstName, lastName, age)
            } catch (e: IllegalStateException) {
                throw JsonParseException(
                    "Unable to parse json into type Person",
                    e
                )
            } catch (e: NumberFormatException) {
                throw JsonParseException(
                    "Unable to parse json into type Person",
                    e
                )
            } catch (e: NullPointerException) {
                throw JsonParseException(
                    "Unable to parse json into type Person",
                    e
                )
            }
        }
    }
}
