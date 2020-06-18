package com.example.model

import com.google.gson.annotations.SerializedName
import kotlin.Long
import kotlin.String

data class Person(
    @SerializedName("firstName")
    val firstName: String? = null,
    @SerializedName("lastName")
    val lastName: String? = null,
    @SerializedName("age")
    val age: Long? = null
)
