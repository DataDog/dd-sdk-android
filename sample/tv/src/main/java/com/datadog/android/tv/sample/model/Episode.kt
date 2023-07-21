/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.tv.sample.model

import com.google.gson.annotations.SerializedName

internal data class Episode(
    @SerializedName("title") val title: String,
    @SerializedName("speakers") val speakers: List<String>,
    @SerializedName("description") val description: List<String>,
    @SerializedName("categories") val categories: List<String>,
    @SerializedName("video") val video: String?,
    @SerializedName("ep_date") val recordDate: String
)
