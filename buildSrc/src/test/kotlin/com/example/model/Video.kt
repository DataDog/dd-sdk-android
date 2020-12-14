package com.example.model

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import kotlin.String
import kotlin.collections.Set

data class Video(
    val title: String,
    val tags: Set<String>? = null,
    val links: Set<String>? = null
) {
    fun toJson(): JsonElement {
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
}
