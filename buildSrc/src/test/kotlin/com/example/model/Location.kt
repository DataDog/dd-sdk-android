package com.example.model

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import kotlin.String

public class Location() {
    public val planet: String = "earth"

    public fun toJson(): JsonElement {
        val json = JsonObject()
        json.addProperty("planet", planet)
        return json
    }
}
