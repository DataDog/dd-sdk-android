package com.example.model

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import java.lang.IllegalStateException
import java.lang.NumberFormatException
import kotlin.Boolean
import kotlin.Long
import kotlin.Nothing
import kotlin.Number
import kotlin.String
import kotlin.jvm.JvmStatic
import kotlin.jvm.Throws

data class Demo(
    val s: String,
    val i: Long,
    val n: Number,
    val b: Boolean,
    val l: Nothing? = null,
    val ns: String? = null,
    val ni: Long? = null,
    val nn: Number? = null,
    val nb: Boolean? = null,
    val nl: Nothing? = null
) {
    fun toJson(): JsonElement {
        val json = JsonObject()
        json.addProperty("s", s)
        json.addProperty("i", i)
        json.addProperty("n", n)
        json.addProperty("b", b)
        json.add("l", null)
        ns?.let { json.addProperty("ns", it) }
        ni?.let { json.addProperty("ni", it) }
        nn?.let { json.addProperty("nn", it) }
        nb?.let { json.addProperty("nb", it) }
        return json
    }

    companion object {
        @JvmStatic
        @Throws(JsonParseException::class)
        fun fromJson(serializedObject: String): Demo {
            try {
                val jsonObject = JsonParser.parseString(serializedObject).asJsonObject
                val s = jsonObject.get("s").asString
                val i = jsonObject.get("i").asLong
                val n = jsonObject.get("n").asNumber
                val b = jsonObject.get("b").asBoolean
                val l = null
                val ns = jsonObject.get("ns")?.asString
                val ni = jsonObject.get("ni")?.asLong
                val nn = jsonObject.get("nn")?.asNumber
                val nb = jsonObject.get("nb")?.asBoolean
                val nl = null
                return Demo(s, i, n, b, l, ns, ni, nn, nb, nl)
            } catch (e: IllegalStateException) {
                throw JsonParseException(e.message)
            } catch (e: NumberFormatException) {
                throw JsonParseException(e.message)
            }
        }
    }
}
