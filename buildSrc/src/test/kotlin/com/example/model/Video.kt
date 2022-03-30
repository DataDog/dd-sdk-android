package com.example.model

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import java.lang.IllegalStateException
import java.lang.NumberFormatException
import kotlin.String
import kotlin.collections.HashSet
import kotlin.collections.Set
import kotlin.jvm.JvmStatic
import kotlin.jvm.Throws

public data class Video(
    public val title: String,
    public val tags: Set<String>? = null,
    public val links: Set<String>? = null,
) {
    public fun toJson(): JsonElement {
        val json = JsonObject()
        json.addProperty("title", title)
        tags?.let { temp ->
            val tagsArray = JsonArray(temp.size)
            temp.forEach { tagsArray.add(it) }
            json.add("tags", tagsArray)
        }
        links?.let { temp ->
            val linksArray = JsonArray(temp.size)
            temp.forEach { linksArray.add(it) }
            json.add("links", linksArray)
        }
        return json
    }

    public companion object {
        @JvmStatic
        @Throws(JsonParseException::class)
        public fun fromJson(serializedObject: String): Video {
            try {
                val jsonObject = JsonParser.parseString(serializedObject).asJsonObject
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
                throw JsonParseException(e.message)
            } catch (e: NumberFormatException) {
                throw JsonParseException(e.message)
            }
        }
    }
}
