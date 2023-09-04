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

        expectedEvents.add(ExpectedApplicationStartActionEvent())
        expectedEvents.add(
            ExpectedApplicationLaunchViewEvent(
                docVersion = 2
            )
        )

        expectedEvents.add(
            ExpectedApplicationLaunchViewEvent(
                docVersion = 3
            )
        )

        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        waitForPendingRUMEvents()

        val fragmentAViewUrl = currentFragmentViewUrl(activity)
        // one for view start
        expectedEvents.add(
            ExpectedViewEvent(
                fragmentAViewUrl,
                2,
                currentFragmentExtras(activity)
            )
        )

        // view stopped
        expectedEvents.add(
            ExpectedViewEvent(
                fragmentAViewUrl,
                3,
                currentFragmentExtras(activity)
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
                currentFragmentExtras(activity)
            )
        )
        // view stopped
        expectedEvents.add(
            ExpectedViewEvent(
                fragmentBViewUrl,
                3,
                currentFragmentExtras(activity)
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
                currentFragmentExtras(activity)
            )
        )

        // view stopped
//        expectedEvents.add(
//            ExpectedViewEvent(
//                fragmentAViewUrl,
//                3,
//                currentFragmentExtras(activity)
//            )
//        )

        instrumentation.runOnMainSync {
            instrumentation.callActivityOnStop(mockServerRule.activity)
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
