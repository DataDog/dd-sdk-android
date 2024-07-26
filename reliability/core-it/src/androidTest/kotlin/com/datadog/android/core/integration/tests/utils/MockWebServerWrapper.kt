/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.integration.tests.utils

import okhttp3.mockwebserver.MockWebServer

class MockWebServerWrapper {
    private val mockWebServer = MockWebServer()

    fun start() {
        mockWebServer.start()
    }

    fun shutdown() {
        mockWebServer.shutdown()
    }

    fun getMockWebServer(): MockWebServer {
        return mockWebServer
    }

    fun getServerUrl(path: String = "/"): String {
        return mockWebServer.url(path).toString()
    }
}
