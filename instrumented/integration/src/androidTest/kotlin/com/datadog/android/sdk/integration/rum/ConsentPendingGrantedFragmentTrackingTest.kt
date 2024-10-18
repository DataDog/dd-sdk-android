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
import com.datadog.android.Datadog
import com.datadog.android.privacy.TrackingConsent
import com.datadog.android.sdk.integration.RuntimeConfig
import com.datadog.android.sdk.rules.RumMockServerActivityTestRule
import com.datadog.tools.unit.ConditionWatcher
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
internal class ConsentPendingGrantedFragmentTrackingTest : FragmentTrackingTest() {

    @get:Rule
    val mockServerRule = RumMockServerActivityTestRule(
        FragmentTrackingPlaygroundActivity::class.java,
        keepRequests = true,
        trackingConsent = TrackingConsent.PENDING
    )

    @Test
    fun verifyViewEventsOnSwipe() {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            // We skip this test on Android 28 and below because Espresso doesn't work well with ViewPager
            return
        }
        val expectedEvents = runInstrumentationScenario(mockServerRule)

        // update the tracking consent
        Datadog.setTrackingConsent(TrackingConsent.GRANTED)

        // Wait to make sure all batches are consumed
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()

        ConditionWatcher {
            verifyExpectedEvents(
                mockServerRule.getRequests(RuntimeConfig.rumEndpointUrl),
                expectedEvents
            )
            true
        }.doWait(timeoutMs = FINAL_WAIT_MS)
    }
}
