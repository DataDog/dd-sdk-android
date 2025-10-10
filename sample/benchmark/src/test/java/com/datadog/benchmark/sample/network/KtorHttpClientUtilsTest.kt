/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.benchmark.sample.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLBuilder
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test

class KtorHttpClientUtilsTest {
    @Test
    fun `M return deserialized body W safeGet() { server returns 200 and proper json }`() {
        // Given
        val mockEngine = MockEngine {
            respond(
                content = Json.Default.encodeToString(ResponseData(1)),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val client = createClient(mockEngine)

        val url = URLBuilder("").build()

        // When
        val response = runBlocking { client.safeGet<ResponseData>(url) }

        // Then
        assertEquals(ResponseData(1), response.optionalResult)
    }

    @Test
    fun `M return ServerError W safeGet() { server returns 500 }`() {
        // Given
        val mockEngine = MockEngine {
            respondError(status = HttpStatusCode.InternalServerError)
        }

        val client = createClient(mockEngine)

        val url = URLBuilder("").build()

        // When
        val response = runBlocking { client.safeGet<ResponseData>(url) }

        // Then
        assertEquals(KtorHttpResponse.ServerError(HttpStatusCode.InternalServerError), response)
    }

    @Test
    fun `M return ClientError W safeGet() { server returns 404 }`() {
        // Given
        val mockEngine = MockEngine {
            respondError(status = HttpStatusCode.NotFound)
        }

        val client = createClient(mockEngine)

        val url = URLBuilder("").build()

        // When
        val response = runBlocking { client.safeGet<ResponseData>(url) }

        // Then
        assertEquals(KtorHttpResponse.ClientError(HttpStatusCode.NotFound), response)
    }

    @Test
    fun `M return UnknownException W safeGet() { server returns 200 and invalid json }`() {
        // Given
        val mockEngine = MockEngine {
            respond(
                content = "some_invalid_json",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val client = createClient(mockEngine)

        val url = URLBuilder("").build()

        // When
        val response = runBlocking { client.safeGet<ResponseData>(url) }

        // Then
        assertInstanceOf(KtorHttpResponse.UnknownException::class.java, response)
    }

    private fun createClient(engine: MockEngine): HttpClient {
        return HttpClient(engine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }
    }
}

@Serializable
private data class ResponseData(
    val x: Int
)
