package com.datadog.android.sdk.integrationtests.utils

import android.app.Activity
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.ActivityTestRule
import com.datadog.android.Datadog
import com.datadog.android.log.EndpointUpdateStrategy
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.nio.charset.StandardCharsets
import java.util.LinkedList
import okhttp3.Headers
import okhttp3.RequestBody
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import java.io.BufferedInputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.zip.GZIPInputStream

internal class MockServerRule<T : Activity>(activityClass: Class<T>) :
    ActivityTestRule<T>(activityClass) {

    private val mockWebServer: MockWebServer = MockWebServer()
    val requestObjects = LinkedList<JsonObject>()
    var requestHeaders: Headers? = null

    override fun beforeActivityLaunched() {
        mockWebServer.apply {
            start()
            dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {

                    val content =
                        if (request.headers.contains("Content-Encoding" to "gzip")) {
                            request.unzip()
                        } else {
                            String(request.body.readByteArray(), Charsets.UTF_8)
                        }
                    val jsonArray =
                        JsonParser.parseString(content)
                            .asJsonArray
                    jsonArray.forEach {
                        requestObjects.add(it.asJsonObject)
                    }
                    requestHeaders = request.headers
                    return mockResponse(200)
                }
            }
        }
        val fakeEndpoint = mockWebServer.url("/").toString().removeSuffix("/")
        Datadog.setEndpointUrl(fakeEndpoint, EndpointUpdateStrategy.DISCARD_OLD_LOGS)
        super.beforeActivityLaunched()
    }

    override fun afterActivityFinished() {
        mockWebServer.shutdown()
        requestHeaders = null
        // clean all logs
        requestObjects.clear()
        InstrumentationRegistry.getInstrumentation().context.filesDir.deleteRecursively()
        super.afterActivityFinished()
    }

    private fun mockResponse(code: Int): MockResponse {
        return MockResponse()
            .setResponseCode(code)
            .setBody("{}")
    }

    private fun RecordedRequest.unzip(): String {
        if (body.size <= 0) {
            return ""
        }
        val gzipInputStream =
            GZIPInputStream(ByteArrayInputStream(body.readByteArray()))
        val byteOutputStream = ByteArrayOutputStream()
        val buffer = ByteArray(1024)
        var readBytes = gzipInputStream.read(buffer, 0, buffer.size)
        while (readBytes > 0) {
            byteOutputStream.write(buffer, 0, readBytes)
            readBytes = gzipInputStream.read(buffer, 0, buffer.size)
        }

        return String(byteOutputStream.toByteArray(), Charsets.UTF_8)
    }
}
