/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sdk.integration.log

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.datadog.android.Datadog
import com.datadog.android.privacy.TrackingConsent
import com.datadog.android.sdk.integration.RuntimeConfig
import com.datadog.android.sdk.rules.MockServerActivityTestRule
import com.datadog.tools.unit.ConditionWatcher
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
internal class ConsentPendingGrantedLogsTest : LogsTest() {

    @get:Rule
    val mockServerRule = MockServerActivityTestRule(
        ActivityLifecycleLogs::class.java,
        trackingConsent = TrackingConsent.PENDING,
        keepRequests = true
    )

    @Test
    fun verifyActivityLogs() {
        // update the tracking consent
        Datadog.setTrackingConsent(TrackingConsent.GRANTED)

        // Wait to make sure all batches are consumed
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()

        ConditionWatcher {
            // verify the captured log events into the MockedWebServer
            verifyExpectedLogs(
                mockServerRule.activity,
                mockServerRule.getRequests(RuntimeConfig.logsEndpointUrl)
            )
            true
        }.doWait(timeoutMs = INITIAL_WAIT_MS)
    }
}
