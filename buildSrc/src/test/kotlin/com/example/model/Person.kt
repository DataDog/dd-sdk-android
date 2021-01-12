package com.example.model

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import java.lang.IllegalStateException
import java.lang.NumberFormatException
import kotlin.Long
import kotlin.String
import kotlin.jvm.JvmStatic
import kotlin.jvm.Throws

data class Person(
    val firstName: String? = null,
    val lastName: String? = null,
    val age: Long? = null
) {
    fun toJson(): JsonElement {
        val json = JsonObject()
        firstName?.let { json.addProperty("firstName", it) }
        lastName?.let { json.addProperty("lastName", it) }
        age?.let { json.addProperty("age", it) }
        return json
    }

    companion object {
        @JvmStatic
        @Throws(JsonParseException::class)
        fun fromJson(serializedObject: String): Person {
            try {
                val jsonObject = JsonParser.parseString(serializedObject).asJsonObject
                val firstName = jsonObject.getAsJsonPrimitive("firstName")?.asString
                val lastName = jsonObject.getAsJsonPrimitive("lastName")?.asString
                val age = jsonObject.getAsJsonPrimitive("age")?.asLong
                return Person(
                    firstName,
                    lastName,
                    age
                )
            } catch(e:IllegalStateException) {
                throw JsonParseException(e.message)
            } catch(e:NumberFormatException) {
                throw JsonParseException(e.message)
            }
        }
    }
}
