/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sdk.integration.rum

import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.ViewAction
import androidx.test.espresso.action.ViewActions.swipeDown
import androidx.test.espresso.action.ViewActions.swipeUp
import androidx.test.espresso.action.ViewActions.swipeLeft
import androidx.test.espresso.action.ViewActions.swipeRight
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.contrib.RecyclerViewActions.actionOnItemAtPosition
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.datadog.android.rum.RumAttributes
import com.datadog.android.sdk.assertj.HeadersAssert
import com.datadog.android.sdk.integration.R
import com.datadog.android.sdk.integration.RuntimeConfig
import com.datadog.android.sdk.rules.RumGesturesTrackingActivityTestRule
import com.datadog.android.sdk.utils.isRumUrl
import com.google.gson.JsonObject
import org.junit.Before
import java.util.concurrent.TimeUnit
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Random

@RunWith(AndroidJUnit4::class)
@LargeTest
internal class EndToEndRumGesturesTrackingTests {

    lateinit var swipeActionBundle: Pair<ViewAction, String>

    @get:Rule
    val mockServerRule = RumGesturesTrackingActivityTestRule(
        RumGesturesTrackingPlaygroundActivity::class.java,
        keepRequests = true
    )

    @Before
    fun setUp() {
        val random = Random().nextInt()
        swipeActionBundle = when {
            random < 0.25 -> Pair(swipeDown(), "down")
            random < 0.5 -> Pair(swipeLeft(), "left")
            random < 0.75 -> Pair(swipeRight(), "right")
            else -> Pair(swipeUp(), "up")
        }
    }

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
        onView(withId(R.id.recyclerView))
            .perform(
                swipeActionBundle.first
            )
        instrumentation.waitForIdleSync()
        Thread.sleep(500)
        onView(withId(R.id.button)).perform(click()) // last one won't be sent (yet)
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
                "${CardView::class.java.canonicalName}",
                "recyclerViewRow",
                extraAttributes = mapOf(
                    RumAttributes.TAG_TARGET_POSITION_IN_SCROLLABLE_CONTAINER to 2,
                    RumAttributes.TAG_TARGET_SCROLLABLE_CONTAINER_CLASS_NAME to
                            mockServerRule.activity.recyclerView.javaClass.canonicalName,
                    RumAttributes.TAG_TARGET_SCROLLABLE_CONTAINER_RESOURCE_ID to
                            "recyclerView"
                )
            ),
            ExpectedGestureEvent(
                Gesture.SWIPE,
                "${RecyclerView::class.java.canonicalName}",
                "recyclerView",
                extraAttributes = mapOf(
                    RumAttributes.TAG_GESTURE_DIRECTION to swipeActionBundle.second
                )
            )
        )
    }

    // endregion
    companion object {
        private val INITIAL_WAIT_MS = TimeUnit.SECONDS.toMillis(40)
    }
}
