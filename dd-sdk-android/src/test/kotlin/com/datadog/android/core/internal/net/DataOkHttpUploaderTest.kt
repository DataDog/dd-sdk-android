/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.net

import android.os.Build
import com.datadog.android.BuildConfig
import com.datadog.tools.unit.setStaticValue
import fr.xgouchet.elmyr.Forge
import java.util.concurrent.TimeUnit
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal abstract class DataOkHttpUploaderTest<T : DataOkHttpUploader> {

    lateinit var mockWebServer: MockWebServer

    lateinit var testedUploader: T

    lateinit var fakeEndpoint: String
    lateinit var fakeToken: String
    lateinit var fakeUserAgent: String

    @BeforeEach
    open fun `set up`(forge: Forge) {

        Build.VERSION::class.java.setStaticValue("RELEASE", forge.anAlphaNumericalString())
        Build::class.java.setStaticValue("MODEL", forge.anAlphabeticalString())
        Build::class.java.setStaticValue("ID", forge.anAlphabeticalString())

        mockWebServer = MockWebServer()
        mockWebServer.start()
        fakeEndpoint = mockWebServer.url("/").toString().removeSuffix("/")
        fakeToken = forge.anHexadecimalString()
        fakeUserAgent = if (forge.aBool()) forge.anAlphaNumericalString() else ""
        println("fakeUserAgent:$fakeUserAgent")
        println("RELEASE:${Build.VERSION.RELEASE}")
        println("MODEL:${Build.MODEL}")
        println("ID:${Build.ID}")
        System.setProperty("http.agent", fakeUserAgent)
        testedUploader = uploader()
    }

    abstract fun uploader(): T

    abstract fun urlFormat(): String

    abstract fun expectedPathRegex(): String

    @AfterEach
    open fun `tear down`() {
        mockWebServer.shutdown()

        Build.VERSION::class.java.setStaticValue("RELEASE", null)
        Build::class.java.setStaticValue("MODEL", null)
        Build::class.java.setStaticValue("ID", null)
    }

    @Test
    fun `uploads data 100-Continue (timeout)`(forge: Forge) {
        val anHexadecimalString = forge.anHexadecimalString()
        val data = anHexadecimalString.toByteArray(Charsets.UTF_8)
        mockWebServer.enqueue(forgeMockResponse(100))

        val status = testedUploader.upload(data)

        assertThat(status).isEqualTo(UploadStatus.NETWORK_ERROR)
        assertRequestIsValid(mockWebServer.takeRequest(), anHexadecimalString)
    }

    @Test
    fun `uploads data 1xx-Informational`(forge: Forge) {
        val anHexadecimalString = forge.anHexadecimalString()
        val data = anHexadecimalString.toByteArray(Charsets.UTF_8)
        mockWebServer.enqueue(forgeMockResponse(forge.anInt(101, 200)))

        val status = testedUploader.upload(data)

        assertThat(status).isEqualTo(UploadStatus.UNKNOWN_ERROR)
        assertRequestIsValid(mockWebServer.takeRequest(), anHexadecimalString)
    }

    @Test
    fun `uploads data 200-OK`(forge: Forge) {
        val anHexadecimalString = forge.anHexadecimalString()
        val data = anHexadecimalString.toByteArray(Charsets.UTF_8)
        mockWebServer.enqueue(forgeMockResponse(200))

        val status = testedUploader.upload(data)

        assertThat(status).isEqualTo(UploadStatus.SUCCESS)
        assertRequestIsValid(mockWebServer.takeRequest(), anHexadecimalString)
    }

    @Test
    fun `uploads data 204-NO CONTENT`(forge: Forge) {
        val anHexadecimalString = forge.anHexadecimalString()
        val data = anHexadecimalString.toByteArray(Charsets.UTF_8)
        mockWebServer.enqueue(forgeMockResponse(204))

        val status = testedUploader.upload(data)

        assertThat(status).isEqualTo(UploadStatus.NETWORK_ERROR)
        assertRequestIsValid(mockWebServer.takeRequest(), anHexadecimalString)
    }

    @Test
    fun `uploads data 205-RESET`(forge: Forge) {
        val anHexadecimalString = forge.anHexadecimalString()
        val data = anHexadecimalString.toByteArray(Charsets.UTF_8)
        mockWebServer.enqueue(forgeMockResponse(205))

        val status = testedUploader.upload(data)

        assertThat(status).isEqualTo(UploadStatus.NETWORK_ERROR)
        assertRequestIsValid(mockWebServer.takeRequest(), anHexadecimalString)
    }

    @Test
    fun `uploads data 2xx-Success`(forge: Forge) {
        val anHexadecimalString = forge.anHexadecimalString()
        val data = anHexadecimalString.toByteArray(Charsets.UTF_8)
        mockWebServer.enqueue(forgeMockResponse(forge.anInt(206, 299)))

        val status = testedUploader.upload(data)

        assertThat(status).isEqualTo(UploadStatus.SUCCESS)
        assertRequestIsValid(mockWebServer.takeRequest(), anHexadecimalString)
    }

    @Test
    fun `uploads data 3xx-Redirection`(forge: Forge) {
        val anHexadecimalString = forge.anHexadecimalString()
        val data = anHexadecimalString.toByteArray(Charsets.UTF_8)
        mockWebServer.enqueue(forgeMockResponse(forge.anInt(300, 399)))

        val status = testedUploader.upload(data)

        assertThat(status).isEqualTo(UploadStatus.HTTP_REDIRECTION)
        assertRequestIsValid(mockWebServer.takeRequest(), anHexadecimalString)
    }

    @Test
    fun `uploads data 400-BadRequest`(forge: Forge) {
        val anHexadecimalString = forge.anHexadecimalString()
        val data = anHexadecimalString.toByteArray(Charsets.UTF_8)
        mockWebServer.enqueue(forgeMockResponse(400))

        val status = testedUploader.upload(data)

        assertThat(status).isEqualTo(UploadStatus.HTTP_CLIENT_ERROR)
        assertRequestIsValid(mockWebServer.takeRequest(), anHexadecimalString)
    }

    @Test
    fun `uploads data 401-Unauthorized`(forge: Forge) {
        val anHexadecimalString = forge.anHexadecimalString()
        val data = anHexadecimalString.toByteArray(Charsets.UTF_8)
        mockWebServer.enqueue(forgeMockResponse(401))

        val status = testedUploader.upload(data)

        assertThat(status).isEqualTo(UploadStatus.HTTP_CLIENT_ERROR)
        assertRequestIsValid(mockWebServer.takeRequest(), anHexadecimalString)
    }

    @Test
    fun `uploads data 403-Forbidden`(forge: Forge) {
        val anHexadecimalString = forge.anHexadecimalString()
        val data = anHexadecimalString.toByteArray(Charsets.UTF_8)
        mockWebServer.enqueue(forgeMockResponse(403))

        val status = testedUploader.upload(data)

        assertThat(status).isEqualTo(UploadStatus.INVALID_TOKEN_ERROR)
        assertRequestIsValid(mockWebServer.takeRequest(), anHexadecimalString)
    }

    @Test
    fun `uploads data 404-NotFound`(forge: Forge) {
        val anHexadecimalString = forge.anHexadecimalString()
        val data = anHexadecimalString.toByteArray(Charsets.UTF_8)
        mockWebServer.enqueue(forgeMockResponse(404))

        val status = testedUploader.upload(data)

        assertThat(status).isEqualTo(UploadStatus.HTTP_CLIENT_ERROR)
        assertRequestIsValid(mockWebServer.takeRequest(), anHexadecimalString)
    }

    @Test
    fun `uploads data 407-Proxy`(forge: Forge) {
        val anHexadecimalString = forge.anHexadecimalString()
        val data = anHexadecimalString.toByteArray(Charsets.UTF_8)
        mockWebServer.enqueue(forgeMockResponse(407))

        val status = testedUploader.upload(data)

        assertThat(status).isEqualTo(UploadStatus.NETWORK_ERROR)
        assertRequestIsValid(mockWebServer.takeRequest(), anHexadecimalString)
    }

    @Test
    fun `uploads data 408-Timeout`(forge: Forge) {
        val anHexadecimalString = forge.anHexadecimalString()
        val data = anHexadecimalString.toByteArray(Charsets.UTF_8)
        mockWebServer.enqueue(forgeMockResponse(408))

        val status = testedUploader.upload(data)

        assertThat(status).isEqualTo(UploadStatus.NETWORK_ERROR)
        assertRequestIsValid(mockWebServer.takeRequest(), anHexadecimalString)
    }

    @Test
    fun `uploads data 4xx-ClientError`(forge: Forge) {
        val anHexadecimalString = forge.anHexadecimalString()
        val data = anHexadecimalString.toByteArray(Charsets.UTF_8)
        mockWebServer.enqueue(forgeMockResponse(forge.anInt(409, 499)))

        val status = testedUploader.upload(data)

        assertThat(status).isEqualTo(UploadStatus.HTTP_CLIENT_ERROR)
        assertRequestIsValid(mockWebServer.takeRequest(), anHexadecimalString)
    }

    @Test
    fun `uploads data 500-InternalServerError`(forge: Forge) {
        val anHexadecimalString = forge.anHexadecimalString()
        val data = anHexadecimalString.toByteArray(Charsets.UTF_8)
        mockWebServer.enqueue(forgeMockResponse(500))

        val status = testedUploader.upload(data)

        assertThat(status).isEqualTo(UploadStatus.HTTP_SERVER_ERROR)
        assertRequestIsValid(mockWebServer.takeRequest(), anHexadecimalString)
    }

    @Test
    fun `uploads data 5xx-ServerError`(forge: Forge) {
        val anHexadecimalString = forge.anHexadecimalString()
        val data = anHexadecimalString.toByteArray(Charsets.UTF_8)
        mockWebServer.enqueue(forgeMockResponse(forge.anInt(500, 599)))

        val status = testedUploader.upload(data)

        assertThat(status).isEqualTo(UploadStatus.HTTP_SERVER_ERROR)
        assertRequestIsValid(mockWebServer.takeRequest(), anHexadecimalString)
    }

    @Test
    fun `uploads data xxx-InvalidError`(forge: Forge) {
        val anHexadecimalString = forge.anHexadecimalString()
        val data = anHexadecimalString.toByteArray(Charsets.UTF_8)
        mockWebServer.enqueue(forgeMockResponse(forge.anInt(600, 1000)))

        val status = testedUploader.upload(data)

        assertThat(status).isEqualTo(UploadStatus.UNKNOWN_ERROR)
        assertRequestIsValid(mockWebServer.takeRequest(), anHexadecimalString)
    }

    @Test
    fun `uploads with IOException (timeout)`(forge: Forge) {
        val data = forge.anHexadecimalString().toByteArray(Charsets.UTF_8)
        mockWebServer.enqueue(
            MockResponse()
                .throttleBody(
                    THROTTLE_RATE,
                    THROTTLE_PERIOD_MS,
                    TimeUnit.MILLISECONDS
                )
                .setBody(
                    "{ 'success': 'ok', 'message': 'Lorem ipsum dolor sit amet, " +
                        "consectetur adipiscing elit, " +
                        "sed do eiusmod tempor incididunt ut labore et dolore magna aliqua.' }"
                )
        )

        val status = testedUploader.upload(data)
        assertThat(status).isEqualTo(UploadStatus.NETWORK_ERROR)
    }

    @Test
    fun `uploads with IOException (protocol)`(forge: Forge) {
        val data = forge.anHexadecimalString().toByteArray(Charsets.UTF_8)
        mockWebServer.enqueue(forgeMockResponse(forge.anInt(0, 100)))

        val status = testedUploader.upload(data)
        assertThat(status).isEqualTo(UploadStatus.NETWORK_ERROR)
    }

    @Test
    fun `uploads with IOException (protocol 2)`(forge: Forge) {
        val data = forge.anHexadecimalString().toByteArray(Charsets.UTF_8)
        mockWebServer.enqueue(forgeMockResponse(forge.anInt(1000)))

        val status = testedUploader.upload(data)
        assertThat(status).isEqualTo(UploadStatus.NETWORK_ERROR)
    }

    @Test
    fun `uploads with IOException (no server)`(forge: Forge) {
        val anHexadecimalString = forge.anHexadecimalString()
        val data = anHexadecimalString.toByteArray(Charsets.UTF_8)
        mockWebServer.shutdown()

        val status = testedUploader.upload(data)
        assertThat(status).isEqualTo(UploadStatus.NETWORK_ERROR)
    }

    @Test
    fun `uploads with updated endpoint`(forge: Forge) {
        val data = forge.anHexadecimalString().toByteArray(Charsets.UTF_8)
        mockWebServer.shutdown()
        val mockWebServer2 = MockWebServer()
        mockWebServer2.start(forge.anInt(2000, 8000))
        mockWebServer2.enqueue(forgeMockResponse(200))
        fakeEndpoint = mockWebServer2.url("/").toString().removeSuffix("/")

        testedUploader.setEndpoint(fakeEndpoint)
        val status = testedUploader.upload(data)
        assertThat(status).isEqualTo(UploadStatus.SUCCESS)
    }

    // region Internal

    private fun assertRequestIsValid(
        request: RecordedRequest,
        data: String
    ) {
        assertRequestHasExpectedHeaders(request)

        assertThat(request.path).matches(expectedPathRegex())
        assertThat(request.body.readUtf8())
            .isEqualTo(data)
    }

    private fun assertRequestHasExpectedHeaders(request: RecordedRequest) {
        assertThat(request.getHeader("Content-Type"))
            .isEqualTo(testedUploader.contentType)
        val expectedUserAgent = if (fakeUserAgent.isBlank()) {
            "Datadog/${BuildConfig.VERSION_NAME} " +
                "(Linux; U; Android ${Build.VERSION.RELEASE}; " +
                "${Build.MODEL} Build/${Build.ID})"
        } else {
            fakeUserAgent
        }
        assertThat(request.getHeader("User-Agent"))
            .isEqualTo(expectedUserAgent)
    }

    private fun forgeMockResponse(code: Int): MockResponse {
        return MockResponse()
            .setResponseCode(code)
            .setBody("{}")
    }

    // endregion

    companion object {
        const val TIMEOUT_TEST_MS = 250L
        const val THROTTLE_RATE = 8L
        const val THROTTLE_PERIOD_MS = TIMEOUT_TEST_MS * 2
    }
}
