package com.example.model

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import java.lang.IllegalStateException
import java.lang.NullPointerException
import java.lang.NumberFormatException
import kotlin.Boolean
import kotlin.String
import kotlin.Suppress
import kotlin.collections.ArrayList
import kotlin.collections.List
import kotlin.jvm.JvmStatic
import kotlin.jvm.Throws

@Suppress("StringLiteralDuplication", "EmptyDefaultConstructor", "MagicNumber")
public data class Message(
    public val destination: List<String>,
    public val origin: String,
    public val subject: String? = null,
    public val message: String? = null,
    public var labels: List<String>? = null,
    public var read: Boolean? = null,
    public var important: Boolean? = null,
) {
    @Suppress("CyclomaticComplexMethod", "LongMethod", "ThrowsCount", "TooGenericExceptionCaught")
    public fun toJson(): JsonElement {
        val json = JsonObject()
        val destinationArray = JsonArray(destination.size)
        destination.forEach { destinationArray.add(it) }
        json.add("destination", destinationArray)
        json.addProperty("origin", origin)
        subject?.let { subjectNonNull ->
            json.addProperty("subject", subjectNonNull)
        }
        message?.let { messageNonNull ->
            json.addProperty("message", messageNonNull)
        }
        labels?.let { labelsNonNull ->
            val labelsArray = JsonArray(labelsNonNull.size)
            labelsNonNull.forEach { labelsArray.add(it) }
            json.add("labels", labelsArray)
        }
        read?.let { readNonNull ->
            json.addProperty("read", readNonNull)
        }
        important?.let { importantNonNull ->
            json.addProperty("important", importantNonNull)
        }
        return json
    }

    public companion object {
        @JvmStatic
        @Throws(JsonParseException::class)
        public fun fromJson(jsonString: String): Message {
            try {
                val jsonObject = JsonParser.parseString(jsonString).asJsonObject
                return fromJsonObject(jsonObject)
            } catch (e: IllegalStateException) {
                throw JsonParseException(
                    "Unable to parse json into type Message",
                    e
                )
            }
        }

        @JvmStatic
        @Suppress("CyclomaticComplexMethod", "LongMethod", "ThrowsCount",
                "TooGenericExceptionCaught")
        @Throws(JsonParseException::class)
        public fun fromJsonObject(jsonObject: JsonObject): Message {
            try {
                val destination = jsonObject.get("destination").asJsonArray.let { jsonArray ->
                    val collection = ArrayList<String>(jsonArray.size())
                    jsonArray.forEach {
                        collection.add(it.asString)
                    }
                    collection
                }
                val origin = jsonObject.get("origin").asString
                val subject = jsonObject.get("subject")?.asString
                val message = jsonObject.get("message")?.asString
                val labels = jsonObject.get("labels")?.asJsonArray?.let { jsonArray ->
                    val collection = ArrayList<String>(jsonArray.size())
                    jsonArray.forEach {
                        collection.add(it.asString)
                    }
                    collection
                }
                val read = jsonObject.get("read")?.asBoolean
                val important = jsonObject.get("important")?.asBoolean
                return Message(destination, origin, subject, message, labels, read, important)
            } catch (e: IllegalStateException) {
                throw JsonParseException(
                    "Unable to parse json into type Message",
                    e
                )
            } catch (e: NumberFormatException) {
                throw JsonParseException(
                    "Unable to parse json into type Message",
                    e
                )
            } catch (e: NullPointerException) {
                throw JsonParseException(
                    "Unable to parse json into type Message",
                    e
                )
            }
        }
    }
}
