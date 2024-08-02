package com.example.model

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import java.lang.IllegalStateException
import java.lang.NullPointerException
import java.lang.NumberFormatException
import kotlin.String
import kotlin.jvm.JvmStatic
import kotlin.jvm.Throws

public class Location {
    public val planet: String = "earth"

    public val solarSystem: String = "sol"

    public fun toJson(): JsonElement {
        val json = JsonObject()
        json.addProperty("planet", planet)
        json.addProperty("solar_system", solarSystem)
        return json
    }

    public companion object {
        @JvmStatic
        @Throws(JsonParseException::class)
        public fun fromJson(jsonString: String): Location {
            try {
                val jsonObject = JsonParser.parseString(jsonString).asJsonObject
                return fromJsonObject(jsonObject)
            } catch (e: IllegalStateException) {
                throw JsonParseException(
                    "Unable to parse json into type Location",
                    e
                )
            }
        }

        @JvmStatic
        @Throws(JsonParseException::class)
        public fun fromJsonObject(jsonObject: JsonObject): Location {
            try {
                val planet = jsonObject.get("planet").asString
                val solarSystem = jsonObject.get("solar_system").asString
                check(planet == "earth")
                check(solarSystem == "sol")
                return Location()
            } catch (e: IllegalStateException) {
                throw JsonParseException(
                    "Unable to parse json into type Location",
                    e
                )
            } catch (e: NumberFormatException) {
                throw JsonParseException(
                    "Unable to parse json into type Location",
                    e
                )
            } catch (e: NullPointerException) {
                throw JsonParseException(
                    "Unable to parse json into type Location",
                    e
                )
            }
        }
    }
}
