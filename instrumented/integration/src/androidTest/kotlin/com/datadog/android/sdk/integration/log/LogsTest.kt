/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sdk.integration.log

import android.os.Build
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import com.datadog.android.sdk.assertj.HeadersAssert
import com.datadog.android.sdk.integration.BuildConfig
import com.datadog.android.sdk.integration.RuntimeConfig
import com.datadog.android.sdk.rules.HandledRequest
import com.datadog.android.sdk.utils.isLogsUrl
import com.datadog.tools.unit.assertj.JsonObjectAssert
import com.google.gson.JsonObject
import org.assertj.core.api.Assertions.assertThat
import java.util.Date
import java.util.concurrent.TimeUnit

internal abstract class LogsTest {

    protected fun verifyExpectedLogs(
        activity: ActivityLifecycleLogs,
        handledRequests: List<HandledRequest>
    ) {
        // Check sent requests
        val logObjects = mutableListOf<JsonObject>()
        handledRequests
            .filter { it.url?.isLogsUrl() ?: false }
            .forEach { request ->
                HeadersAssert.assertThat(request.headers)
                    .isNotNull
                    .hasHeader(HeadersAssert.HEADER_CT, RuntimeConfig.CONTENT_TYPE_JSON)
                    .hasHeader(HeadersAssert.HEADER_UA, expectedUserAgent())

                request.jsonBody!!.asJsonArray.forEach {
                    Log.i("EndToEndLogTest", "adding log $it")
                    logObjects.add(it.asJsonObject)
                }
            }

        // Check log contents
        val messagesSent = activity.getSentMessages()
        val globalTags = RuntimeConfig.allTags
        val globalAttributes = RuntimeConfig.allAttributes
        val localAttributes = activity.localAttributes()

        assertThat(messagesSent).hasSameSizeAs(logObjects)

        messagesSent.forEachIndexed { i, m ->
            val log = logObjects[i]
            JsonObjectAssert.assertThat(log)
                .hasField(TAG_STATUS, levels[m.first])
                .hasField(TAG_MESSAGE, m.second)
                .hasField(TAG_SERVICE, activity.packageName)
                .hasField(TAG_LOGGER) {
                    hasField(TAG_LOGGER_NAME, expectedLoggerName())
                    hasField(TAG_VERSION_NAME, com.datadog.android.BuildConfig.SDK_VERSION_NAME)
                }

            val tags = log.get(TAG_DDTAGS)?.asString.orEmpty().split(',')
            assertThat(tags).isSubsetOf(globalTags)

            globalAttributes.forEach { (k, v) -> log.assertHasAttribute(k, v) }
            localAttributes.forEach { (k, v) -> log.assertHasAttribute(k, v) }
        }
    }

    private fun JsonObject.assertHasAttribute(key: String, value: Any?) {
        when (value) {
            null -> JsonObjectAssert.assertThat(this).hasNullField(key)
            is Boolean -> JsonObjectAssert.assertThat(this).hasField(key, value)
            is Int -> JsonObjectAssert.assertThat(this).hasField(key, value)
            is Long -> JsonObjectAssert.assertThat(this).hasField(key, value)
            is Float -> JsonObjectAssert.assertThat(this).hasField(key, value)
            is Double -> JsonObjectAssert.assertThat(this).hasField(key, value)
            is String -> JsonObjectAssert.assertThat(this).hasField(key, value)
            is Date -> JsonObjectAssert.assertThat(this).hasField(key, value.time)
            else -> JsonObjectAssert.assertThat(this).hasField(key, value.toString())
        }
    }

    private fun expectedLoggerName(): String {
        return InstrumentationRegistry
            .getInstrumentation()
            .targetContext.packageName
    }

    private fun expectedUserAgent(): String {
        return System.getProperty("http.agent").let {
            if (it.isNullOrBlank()) {
                "Datadog/${BuildConfig.VERSION_NAME} " +
                    "(Linux; U; Android ${Build.VERSION.RELEASE}; " +
                    "${Build.MODEL} Build/${Build.ID})"
            } else {
                it
            }
        }
    }

    companion object {
        private const val TAG_STATUS = "status"
        private const val TAG_SERVICE = "service"
        private const val TAG_MESSAGE = "message"
        private const val TAG_DDTAGS = "ddtags"

        private const val TAG_LOGGER = "logger"
        private const val TAG_LOGGER_NAME = "name"
        private const val TAG_VERSION_NAME = "version"

        internal val INITIAL_WAIT_MS = TimeUnit.SECONDS.toMillis(60)

        private val levels = arrayOf(
            "debug",
            "debug",
            "trace",
            "debug",
            "info",
            "warn",
            "error",
            "critical"
        )
    }
}
