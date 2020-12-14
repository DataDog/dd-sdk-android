package com.example.model

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import kotlin.String

class Location {
    val planet: String = "earth"

    fun toJson(): JsonElement {
        val json = JsonObject()
        json.addProperty("planet", planet)
        return json
    }
}
