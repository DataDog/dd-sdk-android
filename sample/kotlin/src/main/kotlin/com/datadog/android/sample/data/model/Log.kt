/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sample.data.model

import com.google.gson.annotations.SerializedName

data class Log(
    @SerializedName("id") val id: String,
    @SerializedName("attributes") val attributes: LogAttributes
)
