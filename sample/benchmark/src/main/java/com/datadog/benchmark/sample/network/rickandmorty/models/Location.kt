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
internal data class Location(
    @SerialName("id")
    val id: Int,

    @SerialName("name")
    val name: String,

    @SerialName("type")
    val type: String,

    @SerialName("dimension")
    val dimension: String,

    @SerialName("residents")
    val residents: List<String>,

    @SerialName("url")
    val url: String,

    @SerialName("created")
    val created: String
): Parcelable

@Serializable
internal data class LocationResponse(
    @SerialName("info")
    val info: PageInfo,

    @SerialName("results")
    val results: List<Location>
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
