/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sdk.integration.rum

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.datadog.android.privacy.TrackingConsent
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
internal class ConsentGrantedActivityTrackingTests {

    private val expectedEvents: MutableList<ExpectedEvent> = mutableListOf()
    private val expectedViewArguments = mapOf<String, Any?>(
        "key1" to "keyValue1",
        "key2" to 1,
        "key3" to 2.0f
    )

    @get:Rule
    val mockServerRule = RumMockServerActivityTestRule(
        RumActivityTrackingPlaygroundActivity::class.java,
        keepRequests = true,
        intentExtras = expectedViewArguments,
        trackingConsent = TrackingConsent.GRANTED
    )

    @Test
    fun verifyViewEvents() {
        expectedEvents.add(ExpectedApplicationStart())
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val activity = mockServerRule.activity
        val viewUrl = activity.javaClass.canonicalName!!.replace(
            '.',
            '/'
        )

        instrumentation.waitForIdleSync()

        // one for application start update
        expectedEvents.add(
            ExpectedViewEvent(
                viewUrl,
                docVersion = 2,
                viewArguments = expectedViewArguments
            )
        )

        // one for view loading time update
        expectedEvents.add(
            ExpectedViewEvent(
                viewUrl,
                docVersion = 3,
                viewArguments = expectedViewArguments,
                extraViewAttributes = mapOf(
                    "loading_type" to "activity_display"
                ),
                extraViewAttributesWithPredicate = mapOf(
                    "loading_time" to { time ->
                        time.asLong >= 0
                    }
                )
            )
        )

        // one for view stopped
        expectedEvents.add(
            ExpectedViewEvent(
                viewUrl,
                docVersion = 4,
                viewArguments = expectedViewArguments
            )
        )

        // activity on pause
        instrumentation.runOnMainSync {
            instrumentation.callActivityOnPause(activity)
        }

        instrumentation.waitForIdleSync()

        // activity start - resume

        instrumentation.runOnMainSync {
            instrumentation.callActivityOnStart(activity)
            instrumentation.callActivityOnResume(activity)
            // this function is only available from Android Q and above
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // we cannot instrument the onPostResume so we had to improvise this
                mockServerRule.performOnLifecycleCallbacks {
                    it.onActivityPostResumed(activity)
                }
            }
        }
        instrumentation.waitForIdleSync()

        // give time to view id to update
        Thread.sleep(500)

        // one for loading time update
        expectedEvents.add(
            ExpectedViewEvent(
                viewUrl,
                docVersion = 2,
                viewArguments = expectedViewArguments,
                extraViewAttributes = mapOf(
                    "loading_type" to "activity_redisplay"
                ),
                extraViewAttributesWithPredicate = mapOf(
                    "loading_time" to { time ->
                        time.asLong > 0
                    }
                )
            )
        )

        // one for view stopped
        expectedEvents.add(
            ExpectedViewEvent(
                viewUrl,
                docVersion = 3,
                viewArguments = expectedViewArguments
            )
        )

        // activity on pause
        instrumentation.runOnMainSync {
            instrumentation.callActivityOnPause(activity)
        }
        instrumentation.waitForIdleSync()
        Thread.sleep(INITIAL_WAIT_MS)
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
                    .hasHeader(HeadersAssert.HEADER_CT, RuntimeConfig.CONTENT_TYPE_TEXT)
                if (request.textBody != null) {
                    sentGestureEvents += rumPayloadToJsonList(request.textBody)
                }
            }
        sentGestureEvents.verifyEventMatches(expectedEvents)
    }

    // endregion

    companion object {
        private val INITIAL_WAIT_MS = TimeUnit.SECONDS.toMillis(30)
    }
}
