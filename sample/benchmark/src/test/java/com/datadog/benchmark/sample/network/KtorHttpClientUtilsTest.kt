/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.benchmark.sample.network

import fr.xgouchet.elmyr.junit5.ForgeExtension
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
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
class KtorHttpClientUtilsTest {
    @Test
    fun `M return deserialized body W safeGet() { server returns 200 and proper json }`() {
        val mockEngine = MockEngine { request ->
            respond(
                content = Json.Default.encodeToString(ResponseData(1)),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val client = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }

        val url = URLBuilder("").build()

        runBlocking {
            val response = client.safeGet<ResponseData>(url)
            assertEquals(ResponseData(1), response.optionalResult)
        }
    }

    @Test
    fun `M return ServerError W safeGet() { server returns 500 }`() {
        val mockEngine = MockEngine { request ->
            respondError(status = HttpStatusCode.InternalServerError)
        }

        val client = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }

        val url = URLBuilder("").build()

        runBlocking {
            val response = client.safeGet<ResponseData>(url)
            assertEquals(KtorHttpResponse.ServerError(HttpStatusCode.InternalServerError), response)
        }
    }

    @Test
    fun `M return ClientError W safeGet() { server returns 404 }`() {
        val mockEngine = MockEngine { request ->
            respondError(status = HttpStatusCode.NotFound)
        }

        val client = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }

        val url = URLBuilder("").build()

        runBlocking {
            val response = client.safeGet<ResponseData>(url)
            assertEquals(KtorHttpResponse.ClientError(HttpStatusCode.NotFound), response)
        }
    }

    @Test
    fun `M return UnknownException W safeGet() { server returns 200 and invalid json }`() {
        val mockEngine = MockEngine { request ->
            respond(
                content = "some_invalid_json",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val client = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }

        val url = URLBuilder("").build()

        runBlocking {
            val response = client.safeGet<ResponseData>(url)
            assertInstanceOf(KtorHttpResponse.UnknownException::class.java, response)
        }
    }
}

@Serializable
private data class ResponseData(
    val x: Int
)
