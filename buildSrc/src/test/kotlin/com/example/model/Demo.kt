package com.example.model

import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import java.lang.IllegalStateException
import java.lang.NullPointerException
import java.lang.NumberFormatException
import kotlin.Boolean
import kotlin.Long
import kotlin.Nothing
import kotlin.Number
import kotlin.String
import kotlin.jvm.JvmStatic
import kotlin.jvm.Throws

public data class Demo(
    public val s: String,
    public val i: Long,
    public val n: Number,
    public val b: Boolean,
    public val l: Nothing? = null,
    public val ns: String? = null,
    public val ni: Long? = null,
    public val nn: Number? = null,
    public val nb: Boolean? = null,
    public val nl: Nothing? = null,
) {
    public fun toJson(): JsonElement {
        val json = JsonObject()
        json.addProperty("s", s)
        json.addProperty("i", i)
        json.addProperty("n", n)
        json.addProperty("b", b)
        json.add("l", JsonNull.INSTANCE)
        ns?.let { nsNonNull ->
            json.addProperty("ns", nsNonNull)
        }
        ni?.let { niNonNull ->
            json.addProperty("ni", niNonNull)
        }
        nn?.let { nnNonNull ->
            json.addProperty("nn", nnNonNull)
        }
        nb?.let { nbNonNull ->
            json.addProperty("nb", nbNonNull)
        }
        json.add("nl", JsonNull.INSTANCE)
        return json
    }

    public companion object {
        @JvmStatic
        @Throws(JsonParseException::class)
        public fun fromJson(jsonString: String): Demo {
            val jsonObject = JsonParser.parseString(jsonString).asJsonObject
            return fromJsonElement(jsonObject)
        }

        @JvmStatic
        @Throws(JsonParseException::class)
        public fun fromJsonElement(jsonObject: JsonObject): Demo {
            try {
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
                throw JsonParseException(
                    "Unable to parse json into type Demo",
                    e
                )
            } catch (e: NumberFormatException) {
                throw JsonParseException(
                    "Unable to parse json into type Demo",
                    e
                )
            } catch (e: NullPointerException) {
                throw JsonParseException(
                    "Unable to parse json into type Demo",
                    e
                )
            }
        }
    }
}
