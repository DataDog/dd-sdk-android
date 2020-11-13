/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sdk.integration.rum

import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.swipeDown
import androidx.test.espresso.contrib.RecyclerViewActions.actionOnItemAtPosition
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.datadog.android.privacy.TrackingConsent
import com.datadog.android.rum.RumAttributes
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
internal class ConsentGrantedGesturesTrackingTests {

    @get:Rule
    val mockServerRule = RumGesturesTrackingActivityTestRule(
        RumGesturesTrackingPlaygroundActivity::class.java,
        keepRequests = true,
        trackingConsent = TrackingConsent.GRANTED
    )

    @Test
    fun verifyTrackedGestures() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()

        onView(withId(R.id.button)).perform(click())
        instrumentation.waitForIdleSync()
        Thread.sleep(500)
        // perform a tap event on a RecyclerView item
        onView(withId(R.id.recyclerView))
            .perform(
                actionOnItemAtPosition<RumGesturesTrackingPlaygroundActivity.Adapter.ViewHolder>(
                    2,
                    click()
                )
            )
        instrumentation.waitForIdleSync()
        Thread.sleep(500)
        // perform a swipe event on the RecyclerView
        onView(withId(R.id.recyclerView)).perform(swipeDown())
        instrumentation.waitForIdleSync()
        Thread.sleep(500)
        onView(withId(R.id.button)).perform(click()) // last one won't be sent (yet)
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
        val expectedGestures = expectedEvents()
        sentGestureEvents.verifyEventMatches(expectedGestures)
    }

    private fun expectedEvents(): List<ExpectedEvent> {
        val viewUrl = mockServerRule.activity.javaClass.canonicalName!!.replace(
            '.',
            '/'
        )
        return listOf(
            ExpectedApplicationStart(),
            ExpectedViewEvent(
                viewUrl,
                2
            ),
            ExpectedGestureEvent(
                Gesture.TAP,
                "${mockServerRule.activity.button.javaClass.canonicalName}",
                "button"
            ),
            ExpectedViewEvent(
                viewUrl,
                3
            ),
            ExpectedGestureEvent(
                Gesture.TAP,
                "${CardView::class.java.canonicalName}",
                "recyclerViewRow",
                extraAttributes = mapOf(
                    RumAttributes.ACTION_TARGET_PARENT_INDEX to 2,
                    RumAttributes.ACTION_TARGET_PARENT_CLASSNAME to
                        mockServerRule.activity.recyclerView.javaClass.canonicalName,
                    RumAttributes.ACTION_TARGET_PARENT_RESOURCE_ID to
                        "recyclerView"
                )
            ),
            ExpectedViewEvent(
                viewUrl,
                4
            ),
            ExpectedGestureEvent(
                Gesture.SWIPE,
                "${RecyclerView::class.java.canonicalName}",
                "recyclerView",
                extraAttributes = mapOf(
                    RumAttributes.ACTION_GESTURE_DIRECTION to "down"
                )
            ),
            ExpectedViewEvent(
                viewUrl,
                5
            )
        )
    }

    // endregion

    companion object {
        private val INITIAL_WAIT_MS = TimeUnit.SECONDS.toMillis(40)
    }
}
