package com.example.model

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import kotlin.Long
import kotlin.String
import kotlin.collections.List

/**
 * A musical opus.
 * @param title The opus's title.
 * @param composer The opus's composer.
 * @param artists The opus's artists.
 * @param duration The opus's duration in seconds
 */
internal data class Opus(
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
    }

    /**
     * The artist's role.
     */
    enum class Role {
        SINGER,

        GUITARIST,

        PIANIST,

        DRUMMER,

        BASSIST,

        VIOLINIST,

        DJ,

        VOCALS,

        OTHER;

        fun toJson(): JsonElement = when (this) {
            SINGER -> JsonPrimitive("singer")
            GUITARIST -> JsonPrimitive("guitarist")
            PIANIST -> JsonPrimitive("pianist")
            DRUMMER -> JsonPrimitive("drummer")
            BASSIST -> JsonPrimitive("bassist")
            VIOLINIST -> JsonPrimitive("violinist")
            DJ -> JsonPrimitive("dj")
            VOCALS -> JsonPrimitive("vocals")
            OTHER -> JsonPrimitive("other")
        }
    }
}
