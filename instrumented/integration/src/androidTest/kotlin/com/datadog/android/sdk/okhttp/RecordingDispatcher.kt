/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sdk.okhttp

import android.util.Log
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.util.zip.GZIPInputStream

internal class RecordingDispatcher(
    private val keepRequests: Boolean,
    private val onRecorded: (request: RecordedRequest, jsonBody: JsonElement?, content: String) -> Unit = { _, _, _ -> }
) : Dispatcher() {
    override fun dispatch(request: RecordedRequest): MockResponse {
        if (keepRequests) {
            // for now the SR requests will be dropped as we do not support them
            // in our integration tests
            handleRequest(request)
        } else {
            Log.w(TAG, "Dropping @request:$request")
        }

        return if (request.path == CONNECTION_ISSUE_PATH) {
            MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE)
        } else if (request.path?.endsWith("nyan-cat.gif") == true) {
            mockResponse(200)
        } else {
            mockResponse(HttpURLConnection.HTTP_ACCEPTED)
        }
    }

    private fun mockResponse(code: Int): MockResponse {
        return MockResponse()
            .setResponseCode(code)
            .setBody("{}")
    }

    private fun handleRequest(request: RecordedRequest) {
        Log.i(TAG, "Handling @request:$request")
        val encoding = request.headers.get(HEADER_CONTENT_ENCODING)
        val contentType = request.headers.get(HEADER_CONTENT_TYPE)
        val content = if (encoding == "gzip") {
            request.unzip()
        } else {
            String(request.body.clone().readByteArray(), Charsets.UTF_8)
        }

        val jsonBody = if (contentType == "application/json") {
            JsonParser.parseString(content)
        } else {
            null
        }

        onRecorded(request, jsonBody, content)
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

    companion object {
        const val TAG = "RecordingDispatcher"
        const val HEADER_CONTENT_TYPE = "Content-Type"
        const val HEADER_CONTENT_ENCODING = "Content-Encoding"
        const val CONNECTION_ISSUE_PATH = "/connection-issue"
    }
}
