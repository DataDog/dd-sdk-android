/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2019 Datadog, Inc.
 */

package com.datadog.android.sdk.integrationtests

import android.app.Activity
import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import com.datadog.android.BuildConfig
import com.datadog.android.sdk.integrationtests.utils.MockDatadogServerRule
import com.datadog.android.sdk.integrationtests.utils.assertj.HeadersAssert
import com.datadog.android.sdk.integrationtests.utils.assertj.LogsListAssert
import java.util.concurrent.TimeUnit
import org.junit.Rule
import org.junit.Test

internal abstract class ActivityLoggerTest<A : Activity>(
    activityClass: Class<A>
) {

    @get:Rule
    var mockDatadogServerRule = MockDatadogServerRule(activityClass)

    @Test
    fun verifyExpectedActivityLogs() {
        // Wait to make sure all batches are consumed
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        Thread.sleep(INITIAL_WAIT)

        // Check logs content
        LogsListAssert.assertThat(mockDatadogServerRule.requestObjects)
            .containsOnlyLogsWithMessagesInOrder(
                "MainActivity/onCreate",
                "MainActivity/onStart",
                "MainActivity/onResume"
            )
            .hasService(expectedServiceName())
            .hasAttributes(expectedAttributes())
            .hasTags(expectedTags())
        // TODO check logger name

        // Check the headers
        HeadersAssert.assertThat(mockDatadogServerRule.requestHeaders)
            .hasHeader(HeadersAssert.HEADER_CT, Runtime.DD_CONTENT_TYPE)
            .hasHeader(HeadersAssert.HEADER_UA, expectedUserAgent())
    }

    // region Abstract

    open fun expectedServiceName(): String {
        return InstrumentationRegistry.getInstrumentation().targetContext.packageName
    }

    open fun expectedUserAgent(): String {
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

    abstract fun expectedMessages(): List<String>

    abstract fun expectedTags(): List<String>

    abstract fun expectedAttributes(): Map<String, Any?>

    // endregion

    companion object {
        private val INITIAL_WAIT = TimeUnit.SECONDS.toMillis(30)
    }
}
