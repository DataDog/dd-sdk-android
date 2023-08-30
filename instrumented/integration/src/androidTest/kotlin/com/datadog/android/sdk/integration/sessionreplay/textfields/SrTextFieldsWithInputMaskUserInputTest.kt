/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sdk.integration.sessionreplay.textfields

import androidx.test.platform.app.InstrumentationRegistry
import com.datadog.android.privacy.TrackingConsent
import com.datadog.android.sdk.integration.RuntimeConfig
import com.datadog.android.sdk.integration.sessionreplay.SessionReplayTextFieldsWithInputActivity
import com.datadog.android.sdk.integration.sessionreplay.SrSnapshotTest
import com.datadog.android.sdk.rules.SessionReplayTestRule
import com.datadog.android.sdk.utils.MASK_USER_INPUT
import com.datadog.android.sdk.utils.SR_PRIVACY_LEVEL
import com.datadog.tools.unit.ConditionWatcher
import org.junit.Rule
import org.junit.Test

internal class SrTextFieldsWithInputMaskUserInputTest :
    SrSnapshotTest<SessionReplayTextFieldsWithInputActivity>() {

    @get:Rule
    val rule = SessionReplayTestRule(
        SessionReplayTextFieldsWithInputActivity::class.java,
        trackingConsent = TrackingConsent.GRANTED,
        keepRequests = true,
        intentExtras = mapOf(SR_PRIVACY_LEVEL to MASK_USER_INPUT)
    )

    @Test
    fun verifySessionFirstSnapshot() {
        // Wait to make sure all batches are consumed
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        runInstrumentationScenario(rule)
        ConditionWatcher {
            // verify the captured log events into the MockedWebServer
            // Here we are going to look for a payload where all edit texts that have
            // predefined text are masked.
            verifyExpectedSrData(
                rule.getRequests(RuntimeConfig.sessionReplayEndpointUrl),
                EXPECTED_PAYLOAD_FILE_NAME,
                MatchingStrategy.CONTAINS
            )
            true
        }.doWait(timeoutMs = INITIAL_WAIT_MS)
    }

    companion object {
        const val EXPECTED_PAYLOAD_FILE_NAME =
            "sr_text_fields_with_input_mask_user_input_payload.json"
    }
}
