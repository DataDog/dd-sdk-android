package com.datadog.android.sdk.integrationtests.utils

import android.app.Activity
import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.ActivityTestRule
import com.datadog.android.BuildConfig
import com.datadog.android.Datadog
import com.datadog.android.log.EndpointUpdateStrategy
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.nio.charset.StandardCharsets
import java.util.*
import okhttp3.Headers
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest

class DDTestRule<T : Activity>(activityClass: Class<T>) : ActivityTestRule<T>(activityClass) {
    private val mockWebServer: MockWebServer = MockWebServer()
    val requestObjects = LinkedList<JsonObject>()
    var requestHeaders: Headers? = null

    override fun beforeActivityLaunched() {
        mockWebServer.apply {
            start()
            dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val jsonArray =
                        JsonParser.parseString(request.body.readString(StandardCharsets.UTF_8))
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

    val userAgent: String = System.getProperty("http.agent").let {
        if (it.isNullOrBlank()) {
            "Datadog/${BuildConfig.VERSION_NAME} " +
                    "(Linux; U; Android ${Build.VERSION.RELEASE}; " +
                    "${Build.MODEL} Build/${Build.ID})"
        } else {
            it
        }
    }
}
