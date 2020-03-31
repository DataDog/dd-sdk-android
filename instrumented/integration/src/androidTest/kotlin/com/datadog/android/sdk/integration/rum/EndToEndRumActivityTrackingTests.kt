/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sdk.integration.rum

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.datadog.android.sdk.assertj.HeadersAssert
import com.datadog.android.sdk.integration.RuntimeConfig
import com.datadog.android.sdk.rules.RumMockServerActivityTestRule
import com.datadog.android.sdk.utils.isRumUrl
import com.google.gson.JsonObject
import java.util.concurrent.TimeUnit
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
internal class EndToEndRumActivityTrackingTests {

    private val expectedEvents: MutableList<ExpectedViewEvent> = mutableListOf()

    @get:Rule
    val mockServerRule = RumMockServerActivityTestRule(
        RumActivityTrackingPlaygroundActivity::class.java,
        keepRequests = true
    )

    @Test
    fun verifyViewEvents() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val viewUrl = mockServerRule.activity.javaClass.canonicalName!!.replace(
            '.',
            '/'
        )
        instrumentation.waitForIdleSync()
        expectedEvents.add(ExpectedViewEvent(viewUrl, docVersion = 2))
        // activity on pause
        instrumentation.runOnMainSync {
            instrumentation
                .callActivityOnPause(mockServerRule.activity)
        }
        instrumentation.waitForIdleSync()
        Thread.sleep(INITIAL_WAIT_MS)

        // Check sent requests
        checkSentRequests()
    }

    // region Internal

    private fun checkSentRequests() {
        val requests = mockServerRule.getRequests()
        val sentGestureEvents = mutableListOf<JsonObject>()
        requests
            .filter { it.url?.isRumUrl() ?: false }
            .forEach { request ->
                HeadersAssert.assertThat(request.headers)
                    .isNotNull
                    .hasHeader(HeadersAssert.HEADER_CT, RuntimeConfig.TEXT_PLAIN_CONTENT_TYPE)
                if (request.textBody != null) {
                    sentGestureEvents += rumPayloadToJsonList(request.textBody)
                }
            }
        sentGestureEvents.assertMatches(expectedEvents)
    }

    // endregion

    companion object {
        private val INITIAL_WAIT_MS = TimeUnit.SECONDS.toMillis(30)
    }
}
