/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.integration.tests.utils

import io.ktor.client.HttpClient
import io.ktor.http.Url
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal interface DatadogRestApiClient {
    suspend fun getRumViewEvent(name: String): KtorHttpResponse<JsonObject>
}

internal class DatadogRestApiClientImpl(
    private val httpClient: HttpClient,
    private val baseUrl: String
) : DatadogRestApiClient {

    override suspend fun getRumViewEvent(name: String): KtorHttpResponse<JsonObject> {
        val requestBody = buildJsonObject {
            put("filter", buildJsonObject {
                put("query", """@type:view @view.name:"$name"""")
                put("from", "now-15m")
                put("to", "now")
            })
        }
        return httpClient.safePost(Url("$baseUrl/api/v2/rum/events/search"), requestBody.toString())
    }
}
