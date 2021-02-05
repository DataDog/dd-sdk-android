package com.example.model

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import java.lang.IllegalStateException
import java.lang.NumberFormatException
import kotlin.Long
import kotlin.String
import kotlin.collections.ArrayList
import kotlin.collections.List
import kotlin.jvm.JvmStatic
import kotlin.jvm.Throws

/**
 * A musical opus.
 * @param title The opus's title.
 * @param composer The opus's composer.
 * @param artists The opus's artists.
 * @param duration The opus's duration in seconds
 */
data class Opus(
    val title: String? = null,
    val composer: String? = null,
    val artists: List<Artist>? = null,
    val duration: Long? = null
) {
    fun toJson(): JsonElement {
        val json = JsonObject()
        title?.let { json.addProperty("title", it) }
        composer?.let { json.addProperty("composer", it) }
        artists?.let { temp ->
            val artistsArray = JsonArray(temp.size)
            temp.forEach { artistsArray.add(it.toJson()) }
            json.add("artists", artistsArray)
        }
        duration?.let { json.addProperty("duration", it) }
        return json
    }

    companion object {
        @JvmStatic
        @Throws(JsonParseException::class)
        fun fromJson(serializedObject: String): Opus {
            try {
                val jsonObject = JsonParser.parseString(serializedObject).asJsonObject
                val title = jsonObject.get("title")?.asString
                val composer = jsonObject.get("composer")?.asString
                val artists = jsonObject.get("artists")?.asJsonArray?.let { jsonArray ->
                    val collection = ArrayList<Artist>(jsonArray.size())
                    jsonArray.forEach {
                        collection.add(Artist.fromJson(it.toString()))
                    }
                    collection
                }
                val duration = jsonObject.get("duration")?.asLong
                return Opus(title, composer, artists, duration)
            } catch (e: IllegalStateException) {
                throw JsonParseException(e.message)
            } catch (e: NumberFormatException) {
                throw JsonParseException(e.message)
            }
        }
    }

    /**
     * An artist and their role in an opus.
     * @param name The artist's name.
     * @param role The artist's role.
     */
    data class Artist(
        val name: String? = null,
        val role: Role? = null
    ) {
        fun toJson(): JsonElement {
            val json = JsonObject()
            name?.let { json.addProperty("name", it) }
            role?.let { json.add("role", it.toJson()) }
            return json
        }

        companion object {
            @JvmStatic
            @Throws(JsonParseException::class)
            fun fromJson(serializedObject: String): Artist {
                try {
                    val jsonObject = JsonParser.parseString(serializedObject).asJsonObject
                    val name = jsonObject.get("name")?.asString
                    val role = jsonObject.get("role")?.asString?.let {
                        Role.fromJson(it)
                    }
                    return Artist(name, role)
                } catch (e: IllegalStateException) {
                    throw JsonParseException(e.message)
                } catch (e: NumberFormatException) {
                    throw JsonParseException(e.message)
                }
            }
        }
    }

    /**
     * The artist's role.
     */
    enum class Role(
        private val jsonValue: String
    ) {
        SINGER("singer"),

        GUITARIST("guitarist"),

        PIANIST("pianist"),

        DRUMMER("drummer"),

        BASSIST("bassist"),

        VIOLINIST("violinist"),

        DJ("dj"),

        VOCALS("vocals"),

        OTHER("other");

        fun toJson(): JsonElement = JsonPrimitive(jsonValue)

        companion object {
            @JvmStatic
            fun fromJson(serializedObject: String): Role = values().first { it.jsonValue ==
                    serializedObject }
        }
    }
}
