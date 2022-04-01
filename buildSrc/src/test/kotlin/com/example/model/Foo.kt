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

public data class Foo(
    public val bar: String? = null,
    public val baz: Long? = null,
) {
    public fun toJson(): JsonElement {
        val json = JsonObject()
        bar?.let { json.addProperty("bar", it) }
        baz?.let { json.addProperty("baz", it) }
        return json
    }

    public companion object {
        @JvmStatic
        @Throws(JsonParseException::class)
        public fun fromJson(serializedObject: String): Foo {
            try {
                val jsonObject = JsonParser.parseString(serializedObject).asJsonObject
                val bar = jsonObject.get("bar")?.asString
                val baz = jsonObject.get("baz")?.asLong
                return Foo(bar, baz)
            } catch (e: IllegalStateException) {
                throw JsonParseException(e.message)
            } catch (e: NumberFormatException) {
                throw JsonParseException(e.message)
            }
        }
    }
}
