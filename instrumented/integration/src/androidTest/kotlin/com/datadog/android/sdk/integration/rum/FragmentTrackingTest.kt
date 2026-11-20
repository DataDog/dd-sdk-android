/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sdk.integration.rum

import androidx.fragment.app.Fragment
import androidx.test.platform.app.InstrumentationRegistry
import com.datadog.android.sdk.integration.R
import com.datadog.android.sdk.rules.RumMockServerActivityTestRule
import com.datadog.android.sdk.utils.asMap

internal abstract class FragmentTrackingTest :
    RumTest<
        FragmentTrackingPlaygroundActivity,
        RumMockServerActivityTestRule<FragmentTrackingPlaygroundActivity>
        >() {

    // region RumTest

    override fun runInstrumentationScenario(
        mockServerRule: RumMockServerActivityTestRule<FragmentTrackingPlaygroundActivity>
    ): MutableList<ExpectedEvent> {
        val activity = mockServerRule.activity
        val expectedEvents = mutableListOf<ExpectedEvent>()

        expectedEvents.add(
            ExpectedApplicationLaunchViewEvent(
                docVersion = 3
            )
        )

        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        waitForPendingRUMEvents()

        val fragmentAViewUrl = currentFragmentViewUrl(activity)

        // fragment A view started
        expectedEvents.add(
            ExpectedViewEvent(
                fragmentAViewUrl,
                2,
                currentFragmentExtras(activity)
            )
        )

        // fragment A view stopped
        expectedEvents.add(
            ExpectedViewUpdateEvent(
                fragmentAViewUrl,
                3,
                currentFragmentExtras(activity)
            )
        )

        // Exercise fragment tracking without depending on injected taps or smooth scrolling.
        instrumentation.runOnMainSync {
            activity.viewPager.setCurrentItem(1, false)
        }
        val fragmentBViewUrl = currentFragmentViewUrl(activity)
        waitForPendingRUMEvents()

        // fragment B view started
        expectedEvents.add(
            ExpectedViewEvent(
                fragmentBViewUrl,
                2,
                currentFragmentExtras(activity)
            )
        )

        // fragment B view stopped
        expectedEvents.add(
            ExpectedViewUpdateEvent(
                fragmentBViewUrl,
                3,
                currentFragmentExtras(activity)
            )
        )

        // navigate back to fragment A
        instrumentation.runOnMainSync {
            activity.viewPager.setCurrentItem(0, false)
        }
        waitForPendingRUMEvents()

        // fragment A view started (again)
        expectedEvents.add(
            ExpectedViewEvent(
                fragmentAViewUrl,
                2,
                currentFragmentExtras(activity)
            )
        )

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
        var fragment: Fragment? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val position = activity.viewPager.currentItem
            fragment = activity.supportFragmentManager
                .findFragmentByTag("android:switcher:${R.id.pager}:$position")
        }
        return fragment
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
