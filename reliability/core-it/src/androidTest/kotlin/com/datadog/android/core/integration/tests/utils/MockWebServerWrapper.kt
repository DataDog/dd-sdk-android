/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.integration.tests.utils

import com.google.gson.JsonParser
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy
import java.net.HttpURLConnection

class MockWebServerWrapper {

    private val mockWebServer = MockWebServer()
    private val requests = mutableListOf<HandledRequest>()

    fun start() {
        mockWebServer.start()
        mockWebServer.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    handleRequest(request)
                    return if (request.path == CONNECTION_ISSUE_PATH) {
                        MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE)
                    } else {
                        mockResponse(HttpURLConnection.HTTP_ACCEPTED)
                    }
                }
            }
    }

    fun shutdown() {
        mockWebServer.shutdown()
        clearData()
    }

    fun getServerUrl(path: String = "/"): String {
        return mockWebServer.url(path).toString()
    }

    fun getHandledRequests(): List<HandledRequest> {
        synchronized(requests) {
            return requests.toList()
        }
    }

    fun clearData() {
        clearRequests()
    }

    private fun clearRequests() {
        synchronized(requests) {
            requests.clear()
        }
    }

    private fun handleRequest(request: RecordedRequest) {
        val content = request.body.copy().readUtf8()
        val jsonBody = JsonParser.parseString(content)
        synchronized(requests) {
            requests.add(
                HandledRequest(
                    url = request.requestUrl.toString(),
                    headers = request.headers,
                    method = request.method,
                    jsonBody = jsonBody,
                    textBody = content,
                    requestBuffer = request.body.clone()
                )
            )
        }
    }

    private fun mockResponse(code: Int): MockResponse {
        return MockResponse()
            .setResponseCode(code)
            .setBody("{}")
    }

    companion object {
        const val CONNECTION_ISSUE_PATH = "/connection-issue"
    }
}
