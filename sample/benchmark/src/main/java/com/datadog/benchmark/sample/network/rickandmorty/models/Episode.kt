/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.benchmark.sample.network.rickandmorty.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Episode(
    @SerialName("id")
    val id: Int,

    @SerialName("name")
    val name: String,

    @SerialName("air_date")
    val airDate: String,

    @SerialName("episode")
    val episodeCode: String,

    @SerialName("characters")
    val characters: List<String>,

    @SerialName("url")
    val url: String,

    @SerialName("created")
    val created: String
)
