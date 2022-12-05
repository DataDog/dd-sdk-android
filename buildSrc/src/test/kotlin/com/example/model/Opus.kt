package com.example.model

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import java.lang.IllegalStateException
import java.lang.NullPointerException
import java.lang.NumberFormatException
import kotlin.Long
import kotlin.String
import kotlin.Suppress
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
@Suppress("StringLiteralDuplication", "EmptyDefaultConstructor", "MagicNumber")
public data class Opus(
    public val title: String? = null,
    public val composer: String? = null,
    public val artists: List<Artist>? = null,
    public val duration: Long? = null,
) {
    @Suppress("CyclomaticComplexMethod", "LongMethod", "ThrowsCount", "TooGenericExceptionCaught")
    public fun toJson(): JsonElement {
        val json = JsonObject()
        title?.let { titleNonNull ->
            json.addProperty("title", titleNonNull)
        }
        composer?.let { composerNonNull ->
            json.addProperty("composer", composerNonNull)
        }
        artists?.let { artistsNonNull ->
            val artistsArray = JsonArray(artistsNonNull.size)
            artistsNonNull.forEach { artistsArray.add(it.toJson()) }
            json.add("artists", artistsArray)
        }
        duration?.let { durationNonNull ->
            json.addProperty("duration", durationNonNull)
        }
        return json
    }

    public companion object {
        @JvmStatic
        @Throws(JsonParseException::class)
        public fun fromJson(jsonString: String): Opus {
            try {
                val jsonObject = JsonParser.parseString(jsonString).asJsonObject
                return fromJsonObject(jsonObject)
            } catch (e: IllegalStateException) {
                throw JsonParseException(
                    "Unable to parse json into type Opus",
                    e
                )
            }
        }

        @JvmStatic
        @Suppress("CyclomaticComplexMethod", "LongMethod", "ThrowsCount",
                "TooGenericExceptionCaught")
        @Throws(JsonParseException::class)
        public fun fromJsonObject(jsonObject: JsonObject): Opus {
            try {
                val title = jsonObject.get("title")?.asString
                val composer = jsonObject.get("composer")?.asString
                val artists = jsonObject.get("artists")?.asJsonArray?.let { jsonArray ->
                    val collection = ArrayList<Artist>(jsonArray.size())
                    jsonArray.forEach {
                        collection.add(Artist.fromJsonObject(it.asJsonObject))
                    }
                    collection
                }
                val duration = jsonObject.get("duration")?.asLong
                return Opus(title, composer, artists, duration)
            } catch (e: IllegalStateException) {
                throw JsonParseException(
                    "Unable to parse json into type Opus",
                    e
                )
            } catch (e: NumberFormatException) {
                throw JsonParseException(
                    "Unable to parse json into type Opus",
                    e
                )
            } catch (e: NullPointerException) {
                throw JsonParseException(
                    "Unable to parse json into type Opus",
                    e
                )
            }
        }
    }

    /**
     * An artist and their role in an opus.
     * @param name The artist's name.
     * @param role The artist's role.
     */
    public data class Artist(
        public val name: String? = null,
        public val role: Role? = null,
    ) {
        @Suppress("CyclomaticComplexMethod", "LongMethod", "ThrowsCount",
                "TooGenericExceptionCaught")
        public fun toJson(): JsonElement {
            val json = JsonObject()
            name?.let { nameNonNull ->
                json.addProperty("name", nameNonNull)
            }
            role?.let { roleNonNull ->
                json.add("role", roleNonNull.toJson())
            }
            return json
        }

        public companion object {
            @JvmStatic
            @Throws(JsonParseException::class)
            public fun fromJson(jsonString: String): Artist {
                try {
                    val jsonObject = JsonParser.parseString(jsonString).asJsonObject
                    return fromJsonObject(jsonObject)
                } catch (e: IllegalStateException) {
                    throw JsonParseException(
                        "Unable to parse json into type Artist",
                        e
                    )
                }
            }

            @JvmStatic
            @Suppress("CyclomaticComplexMethod", "LongMethod", "ThrowsCount",
                    "TooGenericExceptionCaught")
            @Throws(JsonParseException::class)
            public fun fromJsonObject(jsonObject: JsonObject): Artist {
                try {
                    val name = jsonObject.get("name")?.asString
                    val role = jsonObject.get("role")?.asString?.let {
                        Role.fromJson(it)
                    }
                    return Artist(name, role)
                } catch (e: IllegalStateException) {
                    throw JsonParseException(
                        "Unable to parse json into type Artist",
                        e
                    )
                } catch (e: NumberFormatException) {
                    throw JsonParseException(
                        "Unable to parse json into type Artist",
                        e
                    )
                } catch (e: NullPointerException) {
                    throw JsonParseException(
                        "Unable to parse json into type Artist",
                        e
                    )
                }
            }
        }
    }

    /**
     * The artist's role.
     */
    public enum class Role(
        private val jsonValue: String,
    ) {
        SINGER("singer"),
        GUITARIST("guitarist"),
        PIANIST("pianist"),
        DRUMMER("drummer"),
        BASSIST("bassist"),
        VIOLINIST("violinist"),
        DJ("dj"),
        VOCALS("vocals"),
        OTHER("other"),
        ;

        public fun toJson(): JsonElement = JsonPrimitive(jsonValue)

        public companion object {
            @JvmStatic
            public fun fromJson(jsonString: String): Role = values().first {
                it.jsonValue == jsonString
            }
        }
    }
}
