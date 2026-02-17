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
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.URL
import java.security.SecureRandom
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

@ExtendWith(ForgeExtension::class)
internal class SecureTestServerTest {

    private lateinit var testServer: SecureTestServer
    private lateinit var client: OkHttpClient
    private lateinit var sslContext: SSLContext
    private lateinit var trustManager: X509TrustManager
    private val gson = Gson()

    @IntForgery(min = 10000, max = 60000)
    private var serverPort: Int = 0

    @BeforeEach
    fun `set up`() {
        testServer = SecureTestServer(httpsPort = serverPort)
        testServer.start()

        // Create SSL context that trusts the server's self-signed certificate
        val trustManagers = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
            .apply { init(testServer.keyStore) }
            .trustManagers

        trustManager = trustManagers.first() as X509TrustManager
        sslContext = SSLContext.getInstance("TLS").apply {
            init(null, trustManagers, SecureRandom())
        }

        client = OkHttpClient.Builder()
            .sslSocketFactory(sslContext.socketFactory, trustManager)
            .hostnameVerifier { _, _ -> true }
            .build()
    }

    @AfterEach
    fun `tear down`() {
        testServer.stop()
    }

    @Test
    fun `M return GET response over HTTPS W request to get endpoint`() {
        // Given
        val request = Request.Builder()
            .url("${testServer.httpsUrl()}/get")
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
    fun `M return POST response with body over HTTPS W request to post endpoint`(
        @StringForgery fakeBody: String
    ) {
        // Given
        val request = Request.Builder()
            .url("${testServer.httpsUrl()}/post")
            .post(fakeBody.toRequestBody("text/plain".toMediaType()))
            .build()

        // When
        val response = client.newCall(request).execute()
        val body = response.body?.string()
        val json = gson.fromJson(body, JsonObject::class.java)

        // Then
        assertThat(response.code).isEqualTo(200)
        assertThat(json.get("method").asString).isEqualTo("POST")
        assertThat(json.get("body").asString).isEqualTo(fakeBody)
    }

    @Test
    fun `M return PUT response with body over HTTPS W request to put endpoint`(
        @StringForgery fakeBody: String
    ) {
        // Given
        val request = Request.Builder()
            .url("${testServer.httpsUrl()}/put")
            .put(fakeBody.toRequestBody("text/plain".toMediaType()))
            .build()

        // When
        val response = client.newCall(request).execute()
        val body = response.body?.string()
        val json = gson.fromJson(body, JsonObject::class.java)

        // Then
        assertThat(response.code).isEqualTo(200)
        assertThat(json.get("method").asString).isEqualTo("PUT")
        assertThat(json.get("body").asString).isEqualTo(fakeBody)
    }

    @Test
    fun `M return DELETE response over HTTPS W request to delete endpoint`() {
        // Given
        val request = Request.Builder()
            .url("${testServer.httpsUrl()}/delete")
            .delete()
            .build()

        // When
        val response = client.newCall(request).execute()
        val body = response.body?.string()
        val json = gson.fromJson(body, JsonObject::class.java)

        // Then
        assertThat(response.code).isEqualTo(200)
        assertThat(json.get("method").asString).isEqualTo("DELETE")
    }

    @Test
    fun `M redirect to get endpoint over HTTPS W request to redirect get endpoint`() {
        // Given
        val clientNoRedirect = OkHttpClient.Builder()
            .sslSocketFactory(sslContext.socketFactory, trustManager)
            .hostnameVerifier { _, _ -> true }
            .followRedirects(false)
            .build()

        val request = Request.Builder()
            .url("${testServer.httpsUrl()}/redirect/get")
            .get()
            .build()

        // When
        val response = clientNoRedirect.newCall(request).execute()

        // Then
        assertThat(response.code).isEqualTo(302)
        assertThat(response.header("Location")).isEqualTo("/get")
    }

    @RepeatedTest(4)
    fun `M return error with specified status code over HTTPS W request to error get endpoint`(
        @IntForgery(min = 400, max = 599) errorCode: Int
    ) {
        // Given
        val request = Request.Builder()
            .url("${testServer.httpsUrl()}/error/$errorCode/get")
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
    }

    @Test
    fun `M return correct https url W httpsUrl()`() {
        // When
        val url = testServer.httpsUrl()

        // Then
        assertThat(url).isEqualTo("https://localhost:$serverPort")
    }

    @Test
    fun `M provide keyStore W start()`() {
        // When / Then
        assertThat(testServer.keyStore).isNotNull()
    }

    @Test
    fun `M clear keyStore W stop()`() {
        // When
        testServer.stop()

        // Then
        assertThat(testServer.keyStore).isNull()
    }

    @Test
    fun `M return GET response W HttpsURLConnection with custom certificate`() {
        // Given
        val url = URL("${testServer.httpsUrl()}/get")
        val connection = (url.openConnection() as HttpsURLConnection).apply {
            sslSocketFactory = sslContext.socketFactory
        }

        // When
        val responseCode = connection.responseCode
        val body = BufferedReader(InputStreamReader(connection.inputStream)).use { it.readText() }
        val json = gson.fromJson(body, JsonObject::class.java)

        // Then
        assertThat(responseCode).isEqualTo(200)
        assertThat(json.get("method").asString).isEqualTo("GET")
        assertThat(json.get("path").asString).isEqualTo("/get")
    }

    @Test
    fun `M return POST response W HttpsURLConnection with custom certificate`(
        @StringForgery fakeBody: String
    ) {
        // Given
        val url = URL("${testServer.httpsUrl()}/post")
        val connection = (url.openConnection() as HttpsURLConnection).apply {
            setRequestProperty("Content-Type", "text/plain")
            sslSocketFactory = sslContext.socketFactory
            requestMethod = "POST"
            doOutput = true
        }

        OutputStreamWriter(connection.outputStream).use { it.write(fakeBody) }

        // When
        val responseCode = connection.responseCode
        val body = BufferedReader(InputStreamReader(connection.inputStream)).use { it.readText() }
        val json = gson.fromJson(body, JsonObject::class.java)

        // Then
        assertThat(responseCode).isEqualTo(200)
        assertThat(json.get("method").asString).isEqualTo("POST")
        assertThat(json.get("body").asString).isEqualTo(fakeBody)
    }

    @Test
    fun `M fail SSL handshake W HttpsURLConnection without custom certificate`() {
        // Given
        val url = URL("${testServer.httpsUrl()}/get")
        val connection = url.openConnection() as HttpsURLConnection

        // When / Then
        assertThrows<SSLHandshakeException> {
            connection.responseCode
        }
    }

    @Test
    fun `M return combined header values W request with multiple values for same header`(
        @StringForgery fakeHeaderValue1: String,
        @StringForgery fakeHeaderValue2: String
    ) {
        // Given
        val request = Request.Builder()
            .url("${testServer.httpsUrl()}/get")
            .addHeader("X-Custom-Header", fakeHeaderValue1)
            .addHeader("X-Custom-Header", fakeHeaderValue2)
            .get()
            .build()

        // When
        val response = client.newCall(request).execute()
        val body = response.body?.string()
        val json = gson.fromJson(body, JsonObject::class.java)
        val headers = json.getAsJsonObject("headers")

        // Then
        assertThat(response.code).isEqualTo(200)
        assertThat(headers.get("x-custom-header").asString)
            .isEqualTo("$fakeHeaderValue1, $fakeHeaderValue2")
    }

    @Test
    fun `M return combined header values W request with comma-separated and multiple headers`(
        @StringForgery fakeHeaderValue1: String,
        @StringForgery fakeHeaderValue2: String,
        @StringForgery fakeHeaderValue3: String
    ) {
        // Given
        val request = Request.Builder()
            .url("${testServer.httpsUrl()}/get")
            .addHeader("X-Custom-Header", "$fakeHeaderValue1,$fakeHeaderValue2")
            .addHeader("X-Custom-Header", fakeHeaderValue3)
            .get()
            .build()

        // When
        val response = client.newCall(request).execute()
        val body = response.body?.string()
        val json = gson.fromJson(body, JsonObject::class.java)
        val headers = json.getAsJsonObject("headers")

        // Then
        assertThat(response.code).isEqualTo(200)
        assertThat(headers.get("x-custom-header").asString)
            .isEqualTo("$fakeHeaderValue1,$fakeHeaderValue2, $fakeHeaderValue3")
    }
}
