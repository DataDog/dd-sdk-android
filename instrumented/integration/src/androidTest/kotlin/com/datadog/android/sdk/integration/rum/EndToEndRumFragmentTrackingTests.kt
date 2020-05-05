/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sdk.integration.rum

import androidx.fragment.app.Fragment
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.swipeLeft
import androidx.test.espresso.action.ViewActions.swipeRight
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.datadog.android.sdk.assertj.HeadersAssert
import com.datadog.android.sdk.integration.R
import com.datadog.android.sdk.integration.RuntimeConfig
import com.datadog.android.sdk.rules.RumMockServerActivityTestRule
import com.datadog.android.sdk.utils.asMap
import com.datadog.android.sdk.utils.isRumUrl
import com.google.gson.JsonObject
import java.util.concurrent.TimeUnit
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
internal class EndToEndRumFragmentTrackingTests {

    private val expectedEvents: MutableList<ExpectedViewEvent> = mutableListOf()

    @get:Rule
    val mockServerRule = RumMockServerActivityTestRule(
        RumFragmentTrackingPlaygroundActivity::class.java,
        keepRequests = true
    )

    @Test
    fun verifyViewEvents() {

        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        val fragmentAViewUrl = currentFragmentViewUrl()
        // add the first expected event
        expectedEvents.add(
            ExpectedViewEvent(
                fragmentAViewUrl,
                2,
                currentFragmentExtras()
            )
        )

        // swipe to change the fragment
        onView(withId(R.id.tab_layout)).perform(swipeLeft())
        instrumentation.waitForIdleSync()
        Thread.sleep(200) // give time to the view id to update
        val fragmentBViewUrl = currentFragmentViewUrl()
        mockServerRule.activity.supportFragmentManager.fragments
        expectedEvents.add(
            ExpectedViewEvent(
                fragmentBViewUrl,
                2,
                currentFragmentExtras()
            )
        )

        // swipe to close the view
        onView(withId(R.id.tab_layout)).perform(swipeLeft())
        instrumentation.waitForIdleSync()
        Thread.sleep(200) // give time to the view id to update

        val fragmentCViewUrl = currentFragmentViewUrl()
        expectedEvents.add(
            ExpectedViewEvent(
                fragmentCViewUrl,
                2,
                currentFragmentExtras()
            )
        )

        // swipe to close the view
        onView(withId(R.id.tab_layout)).perform(swipeRight())
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
                    .hasHeader(HeadersAssert.HEADER_CT, RuntimeConfig.CONTENT_TYPE_TEXT)
                if (request.textBody != null) {
                    sentGestureEvents += rumPayloadToJsonList(request.textBody)
                }
            }
        sentGestureEvents.assertMatches(expectedEvents)
    }

    private fun currentFragment(): Fragment? {
        val activity = mockServerRule.activity
        val viewPager = activity.viewPager
        return activity.supportFragmentManager
            .findFragmentByTag("android:switcher:${R.id.pager}:${viewPager.getCurrentItem()}")
    }

    private fun currentFragmentExtras(): Map<String, Any?> {
        return currentFragment()?.arguments.asMap()
    }

    private fun currentFragmentViewUrl(): String {
        return currentFragment()?.javaClass?.canonicalName?.replace(
            '.',
            '/'
        ) ?: ""
    }

    // endregion
    companion object {
        private val INITIAL_WAIT_MS = TimeUnit.SECONDS.toMillis(30)
    }
}
