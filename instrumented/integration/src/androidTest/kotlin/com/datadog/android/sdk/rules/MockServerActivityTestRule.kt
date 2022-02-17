/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

@file:Suppress("DEPRECATION")

package com.datadog.android.sdk.rules

import android.app.Activity
import android.content.Intent
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.ActivityTestRule
import com.datadog.android.Datadog
import com.datadog.android.privacy.TrackingConsent
import com.datadog.android.rum.GlobalRum
import com.datadog.android.sdk.integration.RuntimeConfig
import com.datadog.android.sdk.utils.addTrackingConsent
import com.datadog.tools.unit.getFieldValue
import com.google.gson.JsonParser
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.GZIPInputStream
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy

internal open class MockServerActivityTestRule<T : Activity>(
    val activityClass: Class<T>,
    val keepRequests: Boolean = false,
    val trackingConsent: TrackingConsent = TrackingConsent.PENDING
) : ActivityTestRule<T>(activityClass) {

    private val mockWebServer: MockWebServer = MockWebServer()

    private val requests = mutableListOf<HandledRequest>()

    // region ActivityTestRule

    override fun getActivityIntent(): Intent {
        return Intent(
            InstrumentationRegistry.getInstrumentation().targetContext,
            activityClass
        ).apply {
            addTrackingConsent(trackingConsent)
        }
    }

    override fun beforeActivityLaunched() {
        InstrumentationRegistry
            .getInstrumentation()
            .targetContext
            .cacheDir.deleteRecursively {
                Log.i("MockServerActivityTestRule", "Before activity launched, deleting file $it")
            }
        requests.clear()
        mockWebServer.start()
        mockWebServer.setDispatcher(
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    if (keepRequests) {
                        handleRequest(request)
                    } else {
                        Log.w(TAG, "Dropping @request:$request")
                    }

                    return if (request.path == CONNECTION_ISSUE_PATH) {
                        MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE)
                    } else {
                        mockResponse(200)
                    }
                }
            }
        )

        getConnectionUrl().let {
            RuntimeConfig.logsEndpointUrl = "$it/$LOGS_URL_SUFFIX"
            RuntimeConfig.tracesEndpointUrl = "$it/$TRACES_URL_SUFFIX"
            RuntimeConfig.rumEndpointUrl = "$it/$RUM_URL_SUFFIX"
        }

        super.beforeActivityLaunched()
    }

    override fun afterActivityFinished() {
        mockWebServer.shutdown()

        val instance = Datadog.javaClass.getDeclaredField("INSTANCE")
        instance.isAccessible = true

        val method = Datadog.javaClass.declaredMethods.first { it.name.contains("stop") }
        method.isAccessible = true
        method.invoke(instance.get(null))

        InstrumentationRegistry
            .getInstrumentation()
            .targetContext
            .cacheDir.deleteRecursively {
                Log.i("MockServerActivityTestRule", "After activity finished, deleting file $it")
            }
        GlobalRum.getFieldValue<AtomicBoolean, GlobalRum>("isRegistered").set(false)
        super.afterActivityFinished()
    }

    // endregion

    // region MockServerRule

    fun getRequests(): List<HandledRequest> {
        Log.i(TAG, "Caught ${requests.size} requests")
        return requests.toList()
    }

    fun getConnectionUrl(): String = mockWebServer.url("/").toString().removeSuffix("/")

    // endregion

    // region Internal

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
            String(request.body.readByteArray(), Charsets.UTF_8)
        }

        val jsonBody = if (contentType == "application/json") {
            JsonParser.parseString(content)
        } else {
            null
        }

        requests.add(
            HandledRequest(
                url = request.requestUrl.toString(),
                headers = request.headers,
                method = request.method,
                jsonBody = jsonBody,
                textBody = content
            )
        )
    }

    private fun RecordedRequest.unzip(): String {
        if (body.size() <= 0) {
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

    private fun File.deleteRecursively(onFileDeleted: (fileName: String) -> Unit) {
        walkBottomUp()
            .forEach {
                it.delete()
                onFileDeleted(it.canonicalPath)
            }
    }

    // endregion

    companion object {
        private const val TAG = "MockServerActivityTestRule"

        const val HEADER_CONTENT_TYPE = "Content-Type"
        const val HEADER_CONTENT_ENCODING = "Content-Encoding"
        const val LOGS_URL_SUFFIX = "logs"
        const val TRACES_URL_SUFFIX = "traces"
        const val RUM_URL_SUFFIX = "rum"
        const val CONNECTION_ISSUE_PATH = "/connection-issue"
    }
}
