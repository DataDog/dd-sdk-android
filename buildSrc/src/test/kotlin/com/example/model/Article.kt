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
import kotlin.collections.ArrayList
import kotlin.collections.List
import kotlin.jvm.JvmStatic
import kotlin.jvm.Throws

public data class Article(
    public val title: String,
    public val tags: List<String>? = null,
    public val authors: List<String>
) {
    public fun toJson(): JsonElement {
        val json = JsonObject()
        json.addProperty("title", title)
        tags?.let { tagsNonNull ->
            val tagsArray = JsonArray(tagsNonNull.size)
            tagsNonNull.forEach { tagsArray.add(it) }
            json.add("tags", tagsArray)
        }
        val authorsArray = JsonArray(authors.size)
        authors.forEach { authorsArray.add(it) }
        json.add("authors", authorsArray)
        return json
    }

    public companion object {
        @JvmStatic
        @Throws(JsonParseException::class)
        public fun fromJson(jsonString: String): Article {
            try {
                val jsonObject = JsonParser.parseString(jsonString).asJsonObject
                val title = jsonObject.get("title").asString
                val tags = jsonObject.get("tags")?.asJsonArray?.let { jsonArray ->
                    val collection = ArrayList<String>(jsonArray.size())
                    jsonArray.forEach {
                        collection.add(it.asString)
                    }
                    collection
                }
                val authors = jsonObject.get("authors").asJsonArray.let { jsonArray ->
                    val collection = ArrayList<String>(jsonArray.size())
                    jsonArray.forEach {
                        collection.add(it.asString)
                    }
                    collection
                }
                return Article(title, tags, authors)
            } catch (e: IllegalStateException) {
                throw JsonParseException(
                    "Unable to parse json into type Article",
                    e
                )
            } catch (e: NumberFormatException) {
                throw JsonParseException(
                    "Unable to parse json into type Article",
                    e
                )
            } catch (e: NullPointerException) {
                throw JsonParseException(
                    "Unable to parse json into type Article",
                    e
                )
            }
        }
    }
}
