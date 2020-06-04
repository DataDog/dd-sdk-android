package com.example.model

import com.google.gson.annotations.SerializedName
import kotlin.Int
import kotlin.String

data class Foo(
    @SerializedName("bar")
    val bar: String?,
    @SerializedName("baz")
    val baz: Int?
)
