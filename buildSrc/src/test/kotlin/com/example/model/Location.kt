package com.example.model

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import kotlin.String
import kotlin.Suppress

@Suppress("StringLiteralDuplication", "EmptyDefaultConstructor", "MagicNumber")
public class Location() {
    public val planet: String = "earth"

    public val solarSystem: String = "sol"

    @Suppress("CyclomaticComplexMethod", "LongMethod", "ThrowsCount", "TooGenericExceptionCaught")
    public fun toJson(): JsonElement {
        val json = JsonObject()
        json.addProperty("planet", planet)
        json.addProperty("solar_system", solarSystem)
        return json
    }
}
