package com.example.model

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import kotlin.String
import kotlin.collections.Set

internal data class Video(
    val title: String,
    val tags: Set<String>? = null,
    val links: Set<String>? = null
) {
    fun toJson(): JsonElement {
        val json = JsonObject()
        json.addProperty("title", title)
        if (tags != null) {
            val tagsArray = JsonArray(tags.size)
            tags.forEach { tagsArray.add(it) }
            json.add("tags", tagsArray)
        }
        if (links != null) {
            val linksArray = JsonArray(links.size)
            links.forEach { linksArray.add(it) }
            json.add("links", linksArray)
        }
        return json
    }
}
