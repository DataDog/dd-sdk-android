package com.example.model

import com.google.gson.annotations.SerializedName
import kotlin.Int
import kotlin.String

data class Person(
    @SerializedName("firstName")
    val firstName: String?,
    @SerializedName("lastName")
    val lastName: String?,
    @SerializedName("age")
    val age: Int?
)
