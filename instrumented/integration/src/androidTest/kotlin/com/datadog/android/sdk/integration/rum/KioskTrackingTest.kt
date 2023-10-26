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
import com.datadog.android.privacy.TrackingConsent
import com.datadog.android.sdk.integration.R
import com.datadog.android.sdk.rules.KioskTrackingActivityTestRule
import com.datadog.android.sdk.rules.RumMockServerActivityTestRule
import com.datadog.tools.unit.ConditionWatcher
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
internal class KioskTrackingTest :
    RumTest<KioskSplashPlaygroundActivity,
        RumMockServerActivityTestRule<KioskSplashPlaygroundActivity>>() {

    @get:Rule
    val mockServerRule = KioskTrackingActivityTestRule(
        KioskSplashPlaygroundActivity::class.java,
        keepRequests = true,
        trackingConsent = TrackingConsent.GRANTED
    )

    @Test
    fun verifyRumEvents() {
        val expectedEvents = runInstrumentationScenario(mockServerRule)

        // Wait to make sure all batches are consumed
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()

        ConditionWatcher {
            verifyExpectedEvents(mockServerRule.getRequests(), expectedEvents)
            true
        }.doWait(timeoutMs = FINAL_WAIT_MS)
    }

    override fun runInstrumentationScenario(
        mockServerRule: RumMockServerActivityTestRule<KioskSplashPlaygroundActivity>
    ): List<ExpectedEvent> {
        val activity = mockServerRule.activity
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val expectedEvents = mutableListOf<ExpectedEvent>()
        val firstViewUrl = activity.javaClass.canonicalName!!.replace(
            '.',
            '/'
        )
        val secondViewUrl = KioskTrackedPlaygroundActivity::class.java.canonicalName!!.replace(
            '.',
            '/'
        )

        instrumentation.waitForIdleSync()
        waitForPendingRUMEvents()

        expectedEvents.add(ExpectedApplicationStartActionEvent())
        // ignore first view event for application launch, it will be reduced

        // Stop launch view
        expectedEvents.add(
            ExpectedApplicationLaunchViewEvent(
                docVersion = 3
            )
        )

        // No events on this view - one for view stop view / stop session
        // ignore first view event, it will be reduced
        expectedEvents.add(
            ExpectedViewEvent(
                firstViewUrl,
                docVersion = 3,
                viewArguments = mapOf(),
                sessionIsActive = false
            )
        )

        Thread.sleep(2000)
        onView(withId(R.id.end_session)).perform(click())
        instrumentation.waitForIdleSync()
        Thread.sleep(500)

        onView(withId(R.id.start_kiosk)).perform(click())
        instrumentation.waitForIdleSync()

        // one for view start
        // ignore first view event, it will be reduced

        // one for view stop
        expectedEvents.add(
            ExpectedViewEvent(
                secondViewUrl,
                docVersion = 3,
                viewArguments = mapOf(),
                sessionIsActive = true
            )
        )

        onView(withId(R.id.kiosk_button)).perform(click())
        instrumentation.waitForIdleSync()
        onView(withId(R.id.kiosk_back_button)).perform(click())
        instrumentation.waitForIdleSync()

        // No events on this view - one for view stop view / stop session
        // ignore first view event, it will be reduced

        expectedEvents.add(
            ExpectedViewEvent(
                firstViewUrl,
                docVersion = 3,
                viewArguments = mapOf(),
                sessionIsActive = false
            )
        )

        onView(withId(R.id.end_session)).perform(click())
        instrumentation.waitForIdleSync()

        return expectedEvents
    }
}
