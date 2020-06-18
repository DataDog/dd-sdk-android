package com.example.model

import com.google.gson.annotations.SerializedName
import kotlin.String
import kotlin.collections.Set

data class Video(
    @SerializedName("title")
    val title: String,
    @SerializedName("tags")
    val tags: Set<String>? = null,
    @SerializedName("links")
    val links: Set<String>? = null
)
