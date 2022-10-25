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

public data class Paper(
    public val title: String,
    public val author: List<String>,
) {
    public fun toJson(): JsonElement {
        val json = JsonObject()
        json.addProperty("title", title)
        val authorArray = JsonArray(author.size)
        author.forEach { authorArray.add(it) }
        json.add("author", authorArray)
        return json
    }

    public companion object {
        @JvmStatic
        @Throws(JsonParseException::class)
        public fun fromJson(jsonString: String): Paper {
            try {
                val jsonObject = JsonParser.parseString(jsonString).asJsonObject
                val title = jsonObject.get("title").asString
                val author = jsonObject.get("author").asJsonArray.let { jsonArray ->
                    val collection = ArrayList<String>(jsonArray.size())
                    jsonArray.forEach {
                        collection.add(it.asString)
                    }
                    collection
                }
                return Paper(title, author)
            } catch (e: IllegalStateException) {
                throw JsonParseException(
                    "Unable to parse json into type Paper",
                    e
                )
            } catch (e: NumberFormatException) {
                throw JsonParseException(
                    "Unable to parse json into type Paper",
                    e
                )
            } catch (e: NullPointerException) {
                throw JsonParseException(
                    "Unable to parse json into type Paper",
                    e
                )
            }
        }
    }
}
