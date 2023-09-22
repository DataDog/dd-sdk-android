/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sdk.integration.sessionreplay

import com.datadog.android.privacy.TrackingConsent
import com.datadog.android.sdk.integration.RuntimeConfig
import com.datadog.android.sdk.integration.log.LogsTest
import com.datadog.android.sdk.rules.SessionReplayTestRule
import com.datadog.tools.unit.ConditionWatcher
import org.assertj.core.api.Assertions.assertThat
import org.junit.Rule
import org.junit.Test

internal class ConsentNotGrantedSrTest : BaseSessionReplayTest<SessionReplayPlaygroundActivity>() {

    @get:Rule
    val rule = SessionReplayTestRule(
        SessionReplayPlaygroundActivity::class.java,
        trackingConsent = TrackingConsent.NOT_GRANTED,
        keepRequests = true
    )

    @Test
    fun verifySessionFirstSnapshot() {
        runInstrumentationScenario()
        ConditionWatcher {
            // verify the captured log events into the MockedWebServer
            assertThat(rule.getRequests(RuntimeConfig.sessionReplayEndpointUrl)).isEmpty()
            true
        }.doWait(timeoutMs = LogsTest.INITIAL_WAIT_MS)
    }
}
