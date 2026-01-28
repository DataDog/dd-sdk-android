/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.tools.testserver

import com.google.gson.Gson
import com.google.gson.JsonObject
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeExtension
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(ForgeExtension::class)
internal class TestServerTest {

    private val gson: Gson = Gson()
    private val client: OkHttpClient = OkHttpClient.Builder().build()

    @IntForgery(min = 30000, max = 60000)
    private var fakeServerPort: Int = 0

    private lateinit var testServer: TestServer

    @BeforeEach
    fun `set up`() {
        testServer = TestServer(httpPort = fakeServerPort).apply { start() }
    }

    @AfterEach
    fun `tear down`() {
        testServer.stop()
    }

    @Test
    fun `M return GET response W request to get endpoint`() {
        // Given
        val request = Request.Builder()
            .url("${testServer.httpUrl()}/get")
            .get()
            .build()

        // When
        val response = client.newCall(request).execute()
        val body = response.body?.string()
        val json = gson.fromJson(body, JsonObject::class.java)

        // Then
        assertThat(response.code).isEqualTo(200)
        assertThat(json.get("method").asString).isEqualTo("GET")
        assertThat(json.get("path").asString).isEqualTo("/get")
    }

    @Test
    fun `M return POST response with body W request to post endpoint`(
        @StringForgery fakeBody: String
    ) {
        // Given
        val request = Request.Builder()
            .url("${testServer.httpUrl()}/post")
            .post(fakeBody.toRequestBody("text/plain".toMediaType()))
            .build()

        // When
        val response = client.newCall(request).execute()
        val body = response.body?.string()
        val json = gson.fromJson(body, JsonObject::class.java)

        // Then
        assertThat(response.code).isEqualTo(200)
        assertThat(json.get("method").asString).isEqualTo("POST")
        assertThat(json.get("path").asString).isEqualTo("/post")
        assertThat(json.get("body").asString).isEqualTo(fakeBody)
    }

    @Test
    fun `M return PUT response with body W request to put endpoint`(
        @StringForgery fakeBody: String
    ) {
        // Given
        val request = Request.Builder()
            .url("${testServer.httpUrl()}/put")
            .put(fakeBody.toRequestBody("text/plain".toMediaType()))
            .build()

        // When
        val response = client.newCall(request).execute()
        val body = response.body?.string()
        val json = gson.fromJson(body, JsonObject::class.java)

        // Then
        assertThat(response.code).isEqualTo(200)
        assertThat(json.get("method").asString).isEqualTo("PUT")
        assertThat(json.get("path").asString).isEqualTo("/put")
        assertThat(json.get("body").asString).isEqualTo(fakeBody)
    }

    @Test
    fun `M return DELETE response W request to delete endpoint`() {
        // Given
        val request = Request.Builder()
            .url("${testServer.httpUrl()}/delete")
            .delete()
            .build()

        // When
        val response = client.newCall(request).execute()
        val body = response.body?.string()
        val json = gson.fromJson(body, JsonObject::class.java)

        // Then
        assertThat(response.code).isEqualTo(200)
        assertThat(json.get("method").asString).isEqualTo("DELETE")
        assertThat(json.get("path").asString).isEqualTo("/delete")
    }

    @Test
    fun `M return PATCH response with body W request to patch endpoint`(
        @StringForgery fakeBody: String
    ) {
        // Given
        val request = Request.Builder()
            .url("${testServer.httpUrl()}/patch")
            .patch(fakeBody.toRequestBody("text/plain".toMediaType()))
            .build()

        // When
        val response = client.newCall(request).execute()
        val body = response.body?.string()
        val json = gson.fromJson(body, JsonObject::class.java)

        // Then
        assertThat(response.code).isEqualTo(200)
        assertThat(json.get("method").asString).isEqualTo("PATCH")
        assertThat(json.get("path").asString).isEqualTo("/patch")
        assertThat(json.get("body").asString).isEqualTo(fakeBody)
    }

    @Test
    fun `M return HEAD response with headers W request to head endpoint`() {
        // Given
        val request = Request.Builder()
            .url("${testServer.httpUrl()}/head")
            .head()
            .build()

        // When
        val response = client.newCall(request).execute()

        // Then
        assertThat(response.code).isEqualTo(200)
        assertThat(response.header("X-Custom-Header")).isEqualTo("head-response")
    }

    @Test
    fun `M return OPTIONS response with Allow header W request to options endpoint`() {
        // Given
        val request = Request.Builder()
            .url("${testServer.httpUrl()}/options")
            .method("OPTIONS", null)
            .build()

        // When
        val response = client.newCall(request).execute()

        // Then
        assertThat(response.code).isEqualTo(200)
        assertThat(response.header("Allow")).contains("GET", "POST", "PUT", "DELETE", "PATCH", "HEAD", "OPTIONS")
    }

    @Test
    fun `M redirect to get endpoint W request to redirect get endpoint`() {
        // Given
        val clientNoRedirect = OkHttpClient.Builder()
            .followRedirects(false)
            .build()
        val request = Request.Builder()
            .url("${testServer.httpUrl()}/redirect/get")
            .get()
            .build()

        // When
        val response = clientNoRedirect.newCall(request).execute()

        // Then
        assertThat(response.code).isEqualTo(302)
        assertThat(response.header("Location")).isEqualTo("/get")
    }

    @Test
    fun `M follow redirect and return GET response W request to redirect get endpoint`() {
        // Given
        val request = Request.Builder()
            .url("${testServer.httpUrl()}/redirect/get")
            .get()
            .build()

        // When
        val response = client.newCall(request).execute()
        val body = response.body?.string()
        val json = gson.fromJson(body, JsonObject::class.java)

        // Then
        assertThat(response.code).isEqualTo(200)
        assertThat(json.get("method").asString).isEqualTo("GET")
        assertThat(json.get("path").asString).isEqualTo("/get")
    }

    @RepeatedTest(10)
    fun `M return error with specified status code W request to error get endpoint`(
        @IntForgery(min = 400, max = 599) errorCode: Int
    ) {
        // Given
        val request = Request.Builder()
            .url("${testServer.httpUrl()}/error/$errorCode/get")
            .get()
            .build()

        // When
        val response = client.newCall(request).execute()
        val body = response.body?.string()
        val json = gson.fromJson(body, JsonObject::class.java)

        // Then
        assertThat(response.code).isEqualTo(errorCode)
        assertThat(json.get("error").asBoolean).isTrue()
        assertThat(json.get("statusCode").asInt).isEqualTo(errorCode)
        assertThat(json.get("method").asString).isEqualTo("GET")
    }

    @Test
    fun `M return error 500 W request to error post endpoint`(
        @StringForgery fakeBody: String
    ) {
        // Given
        val request = Request.Builder()
            .url("${testServer.httpUrl()}/error/500/post")
            .post(fakeBody.toRequestBody("text/plain".toMediaType()))
            .build()

        // When
        val response = client.newCall(request).execute()
        val body = response.body?.string()
        val json = gson.fromJson(body, JsonObject::class.java)

        // Then
        assertThat(response.code).isEqualTo(500)
        assertThat(json.get("error").asBoolean).isTrue()
        assertThat(json.get("statusCode").asInt).isEqualTo(500)
        assertThat(json.get("method").asString).isEqualTo("POST")
    }

    @Test
    fun `M return error 404 W request to error delete endpoint`() {
        // Given
        val request = Request.Builder()
            .url("${testServer.httpUrl()}/error/404/delete")
            .delete()
            .build()

        // When
        val response = client.newCall(request).execute()
        val body = response.body?.string()
        val json = gson.fromJson(body, JsonObject::class.java)

        // Then
        assertThat(response.code).isEqualTo(404)
        assertThat(json.get("error").asBoolean).isTrue()
        assertThat(json.get("statusCode").asInt).isEqualTo(404)
        assertThat(json.get("method").asString).isEqualTo("DELETE")
    }

    @Test
    fun `M return correct http url W httpUrl()`() {
        // When
        val url = testServer.httpUrl()

        // Then
        assertThat(url).isEqualTo("http://localhost:$fakeServerPort")
    }
}
