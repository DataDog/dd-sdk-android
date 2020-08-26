/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sample.data

import com.datadog.android.sample.BuildConfig
import com.datadog.android.sample.data.model.LogsCollection
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Query

interface RemoteDataSource {

    @Headers(
        "Content-Type: application/json",
        "DD-API-KEY:${BuildConfig.DD_API_KEY}",
        "DD-APPLICATION-KEY:${BuildConfig.DD_APPLICATION_KEY}"
    )
    @GET("logs/events")
    fun getLogs(@Query("filter[query]") query: String): Call<LogsCollection>
}
