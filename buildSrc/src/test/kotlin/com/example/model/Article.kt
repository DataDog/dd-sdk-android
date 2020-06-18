package com.example.model

import com.google.gson.annotations.SerializedName
import kotlin.String
import kotlin.collections.List

data class Article(
    @SerializedName("title")
    val title: String,
    @SerializedName("tags")
    val tags: List<String>? = null,
    @SerializedName("authors")
    val authors: List<String>
)
