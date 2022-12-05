package com.example.model

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import java.lang.IllegalStateException
import java.lang.NullPointerException
import java.lang.NumberFormatException
import kotlin.String
import kotlin.Suppress
import kotlin.collections.HashSet
import kotlin.collections.Set
import kotlin.jvm.JvmStatic
import kotlin.jvm.Throws

@Suppress("StringLiteralDuplication", "EmptyDefaultConstructor", "MagicNumber")
public data class Video(
    public val title: String,
    public val tags: Set<String>? = null,
    public val links: Set<String>? = null,
) {
    @Suppress("CyclomaticComplexMethod", "LongMethod", "ThrowsCount", "TooGenericExceptionCaught")
    public fun toJson(): JsonElement {
        val json = JsonObject()
        json.addProperty("title", title)
        tags?.let { tagsNonNull ->
            val tagsArray = JsonArray(tagsNonNull.size)
            tagsNonNull.forEach { tagsArray.add(it) }
            json.add("tags", tagsArray)
        }
        links?.let { linksNonNull ->
            val linksArray = JsonArray(linksNonNull.size)
            linksNonNull.forEach { linksArray.add(it) }
            json.add("links", linksArray)
        }
        return json
    }

    public companion object {
        @JvmStatic
        @Throws(JsonParseException::class)
        public fun fromJson(jsonString: String): Video {
            try {
                val jsonObject = JsonParser.parseString(jsonString).asJsonObject
                return fromJsonObject(jsonObject)
            } catch (e: IllegalStateException) {
                throw JsonParseException(
                    "Unable to parse json into type Video",
                    e
                )
            }
        }

        @JvmStatic
        @Suppress("CyclomaticComplexMethod", "LongMethod", "ThrowsCount",
                "TooGenericExceptionCaught")
        @Throws(JsonParseException::class)
        public fun fromJsonObject(jsonObject: JsonObject): Video {
            try {
                val title = jsonObject.get("title").asString
                val tags = jsonObject.get("tags")?.asJsonArray?.let { jsonArray ->
                    val collection = HashSet<String>(jsonArray.size())
                    jsonArray.forEach {
                        collection.add(it.asString)
                    }
                    collection
                }
                val links = jsonObject.get("links")?.asJsonArray?.let { jsonArray ->
                    val collection = HashSet<String>(jsonArray.size())
                    jsonArray.forEach {
                        collection.add(it.asString)
                    }
                    collection
                }
                return Video(title, tags, links)
            } catch (e: IllegalStateException) {
                throw JsonParseException(
                    "Unable to parse json into type Video",
                    e
                )
            } catch (e: NumberFormatException) {
                throw JsonParseException(
                    "Unable to parse json into type Video",
                    e
                )
            } catch (e: NullPointerException) {
                throw JsonParseException(
                    "Unable to parse json into type Video",
                    e
                )
            }
        }
    }
}
