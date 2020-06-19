package com.example.model

import com.google.gson.annotations.SerializedName
import kotlin.Long
import kotlin.String

internal data class Foo(
    @SerializedName("bar")
    val bar: String? = null,
    @SerializedName("baz")
    val baz: Long? = null
)
