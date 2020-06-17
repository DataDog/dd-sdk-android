package com.example.model

import com.google.gson.annotations.SerializedName
import kotlin.Long
import kotlin.String

data class Foo(
    @SerializedName("bar")
    val bar: String?,
    @SerializedName("baz")
    val baz: Long?
)
