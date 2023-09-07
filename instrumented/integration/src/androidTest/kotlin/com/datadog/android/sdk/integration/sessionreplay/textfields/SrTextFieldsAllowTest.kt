/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sdk.integration.sessionreplay.textfields

import androidx.test.platform.app.InstrumentationRegistry
import com.datadog.android.privacy.TrackingConsent
import com.datadog.android.sdk.integration.RuntimeConfig
import com.datadog.android.sdk.integration.sessionreplay.SessionReplayTextFieldsActivity
import com.datadog.android.sdk.integration.sessionreplay.SrSnapshotTest
import com.datadog.android.sdk.rules.SessionReplayTestRule
import com.datadog.android.sdk.utils.SR_PRIVACY_LEVEL
import com.datadog.android.sessionreplay.SessionReplayPrivacy
import com.datadog.tools.unit.ConditionWatcher
import org.junit.Rule
import org.junit.Test

internal class SrTextFieldsAllowTest : SrSnapshotTest<SessionReplayTextFieldsActivity>() {

    @get:Rule
    val rule = SessionReplayTestRule(
        SessionReplayTextFieldsActivity::class.java,
        trackingConsent = TrackingConsent.GRANTED,
        keepRequests = true,
        intentExtras = mapOf(SR_PRIVACY_LEVEL to SessionReplayPrivacy.ALLOW)
    )

    @Test
    fun verifySessionFirstSnapshot() {
        // Wait to make sure all batches are consumed
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        runInstrumentationScenario(rule)
        ConditionWatcher {
            // verify the captured log events into the MockedWebServer
            verifyExpectedSrData(
                rule.getRequests(RuntimeConfig.sessionReplayEndpointUrl),
                EXPECTED_PAYLOAD_FILE_NAME,
                MatchingStrategy.CONTAINS
            )
            true
        }.doWait(timeoutMs = INITIAL_WAIT_MS)
    }

    companion object {
        const val EXPECTED_PAYLOAD_FILE_NAME = "sr_text_fields_allow_payload.json"
    }
}
