/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sdk.integration.rum

import android.view.View
import androidx.cardview.widget.CardView
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.swipeDown
import androidx.test.espresso.contrib.RecyclerViewActions
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.platform.app.InstrumentationRegistry
import com.datadog.android.rum.RumAttributes
import com.datadog.android.sdk.integration.R
import com.datadog.android.sdk.rules.GesturesTrackingActivityTestRule

internal abstract class GesturesTrackingTest :
    RumTest<GesturesTrackingPlaygroundActivity,
        GesturesTrackingActivityTestRule<GesturesTrackingPlaygroundActivity>>() {

    // region RumTest

    override fun runInstrumentationScenario(
        mockServerRule: GesturesTrackingActivityTestRule<GesturesTrackingPlaygroundActivity>
    ): List<ExpectedEvent> {
        val activity = mockServerRule.activity
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()

        onView(withId(R.id.button)).perform(click())
        instrumentation.waitForIdleSync()
        Thread.sleep(500)
        // perform a tap event on a RecyclerView item
        onView(withId(R.id.recyclerView))
            .perform(
                RecyclerViewActions
                    .actionOnItemAtPosition<GesturesTrackingPlaygroundActivity.Adapter.ViewHolder>(
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
        onView(withId(R.id.button))
            .perform(click()) // last one won't be sent (yet)
        instrumentation.waitForIdleSync()

        val viewUrl = activity.javaClass.canonicalName!!.replace(
            '.',
            '/'
        )
        return expectedEvents(viewUrl, activity)
    }

    // endregion

    // region Internal

    private fun View.targetName(): String {
        return this.javaClass.canonicalName ?: this.javaClass.simpleName
    }

    private fun expectedEvents(
        viewUrl: String,
        activity: GesturesTrackingPlaygroundActivity
    ): List<ExpectedEvent> {
        return listOf(
            ExpectedApplicationStartActionEvent(),
            // ignore first view event for application launch, it will be reduced
            ExpectedApplicationLaunchViewEvent(docVersion = 3),
            // ignore first view event, it will be reduced
            ExpectedGestureEvent(
                Gesture.TAP,
                activity.button.targetName(),
                "button"
            ),
            ExpectedGestureEvent(
                Gesture.TAP,
                CardView::class.java.canonicalName
                    ?: CardView::class.java.simpleName,
                "recyclerViewRow",
                extraAttributes = mapOf(
                    RumAttributes.ACTION_TARGET_PARENT_INDEX to 2,
                    RumAttributes.ACTION_TARGET_PARENT_CLASSNAME to
                        activity.recyclerView.javaClass.canonicalName,
                    RumAttributes.ACTION_TARGET_PARENT_RESOURCE_ID to
                        "recyclerView"
                )
            ),
            ExpectedGestureEvent(
                Gesture.SWIPE,
                activity.recyclerView.targetName(),
                "recyclerView",
                extraAttributes = mapOf(
                    RumAttributes.ACTION_GESTURE_DIRECTION to "down"
                )
            ),
            ExpectedViewEvent(
                viewUrl,
                docVersion = 5
            )
        )
    }

    // endregion
}
