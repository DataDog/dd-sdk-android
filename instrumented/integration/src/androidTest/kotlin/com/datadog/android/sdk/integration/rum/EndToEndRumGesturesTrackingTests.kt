/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sdk.integration.rum

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.datadog.android.sdk.assertj.HeadersAssert
import com.datadog.android.sdk.integration.R
import com.datadog.android.sdk.integration.RuntimeConfig
import com.datadog.android.sdk.rules.RumGesturesTrackingActivityTestRule
import com.datadog.android.sdk.utils.isRumUrl
import com.google.gson.JsonObject
import java.util.concurrent.TimeUnit
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
internal class EndToEndRumGesturesTrackingTests {

    @get:Rule
    val mockServerRule = RumGesturesTrackingActivityTestRule(
        RumGesturesTrackingPlaygroundActivity::class.java,
        keepRequests = true
    )

    @Test
    fun verifyTrackedGestures() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()

        onView(withId(R.id.button)).perform(click())
        instrumentation.waitForIdleSync()
        Thread.sleep(500)
        onView(withId(R.id.textView)).perform(click())
        instrumentation.waitForIdleSync()
        Thread.sleep(500)
        onView(withId(R.id.textView)).perform(click()) // last one won't be sent (yet)

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
        val expectedGestures = expectedEvents()
        sentGestureEvents.assertMatches(expectedGestures)
    }

    private fun expectedEvents(): List<ExpectedEvent> {
        return listOf(
            ExpectedGestureEvent(
                Gesture.TAP,
                "${mockServerRule.activity.button.javaClass.canonicalName}",
                "button"
            ),
            ExpectedGestureEvent(
                Gesture.TAP,
                "${mockServerRule.activity.textView.javaClass.canonicalName}",
                "textView"
            )
        )
    }

    // endregion
    companion object {
        private val INITIAL_WAIT_MS = TimeUnit.SECONDS.toMillis(40)
    }
}
