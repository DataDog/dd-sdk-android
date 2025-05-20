/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.benchmark.sample.network.rickandmorty.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@Parcelize
internal data class Character(
    @SerialName("id")
    val id: Int,

    @SerialName("name")
    val name: String,

    @SerialName("status")
    val status: Status,

    @SerialName("species")
    val species: String,

    @SerialName("type")
    val type: String,

    @SerialName("gender")
    val gender: Gender,

    @SerialName("origin")
    val origin: LocationInfo,

    @SerialName("location")
    val location: LocationInfo,

    @SerialName("image")
    val image: String,

    @SerialName("episode")
    val episode: List<String>,

    @SerialName("url")
    val url: String,

    @SerialName("created")
    val created: String
): Parcelable {
    @Serializable
    @Parcelize
    data class LocationInfo(
        @SerialName("name")
        val name: String,

        @SerialName("url")
        val url: String
    ): Parcelable

    @Serializable
    @Parcelize
    enum class Status: Parcelable {
        @SerialName("Alive")
        ALIVE,
        @SerialName("Dead")
        DEAD,
        @SerialName("unknown")
        UNKNOWN
    }

    @Serializable
    @Parcelize
    enum class Gender: Parcelable {
        @SerialName("Female")
        FEMALE,
        @SerialName("Male")
        MALE,
        @SerialName("Genderless")
        GENDERLESS,
        @SerialName("unknown")
        UNKNOWN
    }
}

@Serializable
internal data class CharacterResponse(
    @SerialName("info")
    val info: PageInfo,

    @SerialName("results")
    val results: List<Character>
) {
    @Serializable
    data class PageInfo(
        @SerialName("count")
        val count: Int,

        @SerialName("pages")
        val pages: Int,

        @SerialName("next")
        val next: String?,

        @SerialName("prev")
        val prev: String?
    )
}
