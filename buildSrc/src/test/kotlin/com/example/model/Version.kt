package com.example.model

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import kotlin.Double
import kotlin.Long

internal class Version {
    val version: Long = 42L

    val delta: Double = 3.1415

    fun toJson(): JsonElement {
        val json = JsonObject()
        json.addProperty("version", version)
        json.addProperty("delta", delta)
        return json
    }
}
