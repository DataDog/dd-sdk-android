/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sdk.integration.trace

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.datadog.android.privacy.TrackingConsent
import com.datadog.android.sdk.rules.MockServerActivityTestRule
import com.datadog.tools.unit.ConditionWatcher
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
internal class ConsentGrantedTracesTest : TracesTest() {

    @get:Rule
    val mockServerRule = MockServerActivityTestRule(
        ActivityLifecycleTrace::class.java,
        keepRequests = true,
        trackingConsent = TrackingConsent.GRANTED
    )

    @Test
    fun verifyExpectedActivitySpansAndLogs() {
        runInstrumentationScenario(mockServerRule)

        // Wait to make sure all batches are consumed
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()

        ConditionWatcher {
            // Check sent requests
            val handledRequests = mockServerRule.getRequests()
            val datadogContext = mockServerRule.activity.getDatadogContext()!!
            verifyExpectedSpans(datadogContext, handledRequests, mockServerRule.activity.getSentSpans())
            verifyExpectedLogs(handledRequests, mockServerRule.activity.getSentLogs())
            true
        }.doWait(timeoutMs = INITIAL_WAIT_MS)
    }
}
