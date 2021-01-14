/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sdk.integration.rum

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.datadog.android.Datadog
import com.datadog.android.privacy.TrackingConsent
import com.datadog.android.sdk.rules.RumMockServerActivityTestRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
internal class ConsentPendingNotGrantedFragmentTrackingTest : FragmentTrackingTest() {

    @get:Rule
    val mockServerRule = RumMockServerActivityTestRule(
        FragmentTrackingPlaygroundActivity::class.java,
        keepRequests = true,
        trackingConsent = TrackingConsent.PENDING
    )

    @Test
    fun verifyAllRumEventsAreDropped() {
        runInstrumentationScenario(mockServerRule)

        // update the tracking consent
        Datadog.setTrackingConsent(TrackingConsent.NOT_GRANTED)

        // Wait to make sure all batches are consumed
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        Thread.sleep(FINAL_WAIT_MS)

        verifyNoRumPayloadSent(mockServerRule.getRequests())
    }
}
