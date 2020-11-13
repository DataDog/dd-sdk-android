/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sdk.integration.log

import android.os.Build
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.datadog.android.privacy.TrackingConsent
import com.datadog.android.sdk.assertj.HeadersAssert
import com.datadog.android.sdk.assertj.HeadersAssert.Companion.assertThat
import com.datadog.android.sdk.integration.BuildConfig
import com.datadog.android.sdk.integration.RuntimeConfig
import com.datadog.android.sdk.rules.MockServerActivityTestRule
import com.datadog.android.sdk.utils.isLogsUrl
import com.datadog.tools.unit.assertj.JsonObjectAssert.Companion.assertThat
import com.google.gson.JsonObject
import java.util.Date
import java.util.concurrent.TimeUnit
import org.assertj.core.api.Assertions.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
internal class ConsentGrantedLogsTest {

    @get:Rule
    val mockServerRule = MockServerActivityTestRule(
        ActivityLifecycleLogs::class.java,
        trackingConsent = TrackingConsent.GRANTED,
        keepRequests = true
    )

    @Test
    fun verifyExpectedActivityLogs() {

        // Wait to make sure all batches are consumed
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        Thread.sleep(INITIAL_WAIT_MS)

        // Check sent requests
        val requests = mockServerRule.getRequests()
        val logObjects = mutableListOf<JsonObject>()
        requests
            .filter { it.url?.isLogsUrl() ?: false }
            .forEach { request ->
                assertThat(request.headers)
                    .isNotNull
                    .hasHeader(HeadersAssert.HEADER_CT, RuntimeConfig.CONTENT_TYPE_JSON)
                    .hasHeader(HeadersAssert.HEADER_UA, expectedUserAgent())

                request.jsonBody!!.asJsonArray.forEach {
                    Log.i("EndToEndLogTest", "adding log $it")
                    logObjects.add(it.asJsonObject)
                }
            }

        // Check log contents
        val messagesSent = mockServerRule.activity.getSentMessages()
        val globalTags = RuntimeConfig.allTags
        val globalAttributes = RuntimeConfig.allAttributes
        val localAttributes = mockServerRule.activity.localAttributes()

        messagesSent.forEachIndexed { i, m ->
            val log = logObjects[i]
            assertThat(log)
                .hasField(TAG_STATUS, levels[m.first])
                .hasField(TAG_MESSAGE, m.second)
                .hasField(TAG_SERVICE, mockServerRule.activity.packageName)
                .hasField(TAG_LOGGER_NAME, expectedLoggerName())
                .hasField(TAG_VERSION_NAME, com.datadog.android.BuildConfig.VERSION_NAME)

            val tags = log.get(TAG_DDTAGS)?.asString.orEmpty().split(',')
            assertThat(tags).containsOnlyElementsOf(globalTags)

            globalAttributes.forEach { (k, v) -> log.assertHasAttribute(k, v) }
            localAttributes.forEach { (k, v) -> log.assertHasAttribute(k, v) }
        }
    }

    // region Internal

    private fun JsonObject.assertHasAttribute(key: String, value: Any?) {
        when (value) {
            null -> assertThat(this).hasNullField(key)
            is Boolean -> assertThat(this).hasField(key, value)
            is Int -> assertThat(this).hasField(key, value)
            is Long -> assertThat(this).hasField(key, value)
            is Float -> assertThat(this).hasField(key, value)
            is Double -> assertThat(this).hasField(key, value)
            is String -> assertThat(this).hasField(key, value)
            is Date -> assertThat(this).hasField(key, value.time)
            else -> assertThat(this).hasField(key, value.toString())
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

    // endregion

    companion object {
        internal const val TAG_STATUS = "status"
        private const val TAG_SERVICE = "service"
        private const val TAG_MESSAGE = "message"
        private const val TAG_DDTAGS = "ddtags"

        internal const val TAG_LOGGER_NAME = "logger.name"
        internal const val TAG_VERSION_NAME = "logger.version"

        private val INITIAL_WAIT_MS = TimeUnit.SECONDS.toMillis(60)

        internal val levels = arrayOf(
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
