/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sdk.integration.rum

import androidx.fragment.app.Fragment
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.platform.app.InstrumentationRegistry
import com.datadog.android.sdk.integration.R
import com.datadog.android.sdk.rules.RumMockServerActivityTestRule
import com.datadog.android.sdk.utils.asMap

internal abstract class FragmentTrackingTest :
    RumTest<FragmentTrackingPlaygroundActivity,
        RumMockServerActivityTestRule<FragmentTrackingPlaygroundActivity>>() {

    // region RumTest

    override fun runInstrumentationScenario(
        mockServerRule: RumMockServerActivityTestRule<FragmentTrackingPlaygroundActivity>
    ): MutableList<ExpectedEvent> {
        val activity = mockServerRule.activity
        val expectedEvents = mutableListOf<ExpectedEvent>()
        expectedEvents.add(ExpectedApplicationStart())

        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        val fragmentAViewUrl = currentFragmentViewUrl(activity)
        // one for application start update
        expectedEvents.add(
            ExpectedViewEvent(
                fragmentAViewUrl,
                2,
                currentFragmentExtras(activity)
            )
        )

        // for update view time
        expectedEvents.add(
            ExpectedViewEvent(
                fragmentAViewUrl,
                3,
                currentFragmentExtras(activity),
                extraViewAttributes = mapOf(
                    "loading_type" to "fragment_display"
                ),
                extraViewAttributesWithPredicate = mapOf(
                    "loading_time" to { time ->
                        time.asLong >= 0
                    }
                )
            )
        )

        // view stopped
        expectedEvents.add(
            ExpectedViewEvent(
                fragmentAViewUrl,
                4,
                currentFragmentExtras(activity),
                extraViewAttributes = mapOf(
                    "loading_type" to "fragment_display"
                ),
                extraViewAttributesWithPredicate = mapOf(
                    "loading_time" to { time ->
                        time.asLong >= 0
                    }
                )
            )
        )

        // swipe to change the fragment
        onView(ViewMatchers.withId(R.id.tab_layout)).perform(ViewActions.swipeLeft())
        instrumentation.waitForIdleSync()
        Thread.sleep(200) // give time to the view id to update
        val fragmentBViewUrl = currentFragmentViewUrl(activity)
        mockServerRule.activity.supportFragmentManager.fragments
        // for updating the time
        expectedEvents.add(
            ExpectedViewEvent(
                fragmentBViewUrl,
                2,
                currentFragmentExtras(activity),
                extraViewAttributes = mapOf(
                    "loading_type" to "fragment_display"
                ),
                extraViewAttributesWithPredicate = mapOf(
                    "loading_time" to { time ->
                        time.asLong >= 0
                    }
                )
            )
        )
        // view stopped
        expectedEvents.add(
            ExpectedViewEvent(
                fragmentBViewUrl,
                3,
                currentFragmentExtras(activity),
                extraViewAttributes = mapOf(
                    "loading_type" to "fragment_display"
                ),
                extraViewAttributesWithPredicate = mapOf(
                    "loading_time" to { time ->
                        time.asLong >= 0
                    }
                )
            )
        )

        // swipe to close the view
        onView(ViewMatchers.withId(R.id.tab_layout)).perform(ViewActions.swipeRight())
        instrumentation.waitForIdleSync()
        Thread.sleep(200) // give time to the view id to update

        // for updating the time
        expectedEvents.add(
            ExpectedViewEvent(
                fragmentAViewUrl,
                2,
                currentFragmentExtras(activity),
                extraViewAttributes = mapOf(
                    "loading_type" to "fragment_redisplay"
                ),
                extraViewAttributesWithPredicate = mapOf(
                    "loading_time" to { time ->
                        time.asLong >= 0
                    }
                )
            )
        )

        // view stopped
        expectedEvents.add(
            ExpectedViewEvent(
                fragmentAViewUrl,
                3,
                currentFragmentExtras(activity),
                extraViewAttributes = mapOf(
                    "loading_type" to "fragment_redisplay"
                ),
                extraViewAttributesWithPredicate = mapOf(
                    "loading_time" to { time ->
                        time.asLong >= 0
                    }
                )
            )
        )

        instrumentation.runOnMainSync {
            instrumentation.callActivityOnPause(mockServerRule.activity)
        }

        return expectedEvents
    }

    // endregion

    // region Internal

    private fun currentFragment(
        activity: FragmentTrackingPlaygroundActivity
    ): Fragment? {
        val viewPager = activity.viewPager
        return activity.supportFragmentManager
            .findFragmentByTag("android:switcher:${R.id.pager}:${viewPager.getCurrentItem()}")
    }

    private fun currentFragmentExtras(
        activity: FragmentTrackingPlaygroundActivity
    ): Map<String, Any?> {
        return currentFragment(activity)?.arguments.asMap()
    }

    private fun currentFragmentViewUrl(
        activity: FragmentTrackingPlaygroundActivity
    ): String {
        return currentFragment(activity)?.javaClass?.canonicalName?.replace(
            '.',
            '/'
        ) ?: ""
    }

    // endregion
}
