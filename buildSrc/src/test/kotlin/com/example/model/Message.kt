package com.example.model

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import kotlin.Boolean
import kotlin.String
import kotlin.collections.List

data class Message(
    val destination: List<String>,
    val origin: String,
    val subject: String? = null,
    val message: String? = null,
    var labels: List<String>? = null,
    var read: Boolean? = null,
    var important: Boolean? = null
) {
    internal fun toJson(): JsonElement {
        val json = JsonObject()
        val destinationArray = JsonArray(destination.size)
        destination.forEach { destinationArray.add(it) }
        json.add("destination", destinationArray)
        json.addProperty("origin", origin)
        subject?.let { json.addProperty("subject", it) }
        message?.let { json.addProperty("message", it) }
        labels?.let { temp ->
            val labelsArray = JsonArray(temp.size)
            temp.forEach { labelsArray.add(it) }
            json.add("labels", labelsArray)
        }
        read?.let { json.addProperty("read", it) }
        important?.let { json.addProperty("important", it) }
        return json
    }
}
