/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sdk.integration.sessionreplay.imagebuttons

import com.datadog.android.privacy.TrackingConsent
import com.datadog.android.sdk.integration.sessionreplay.BaseSessionReplayTest
import com.datadog.android.sdk.integration.sessionreplay.SessionReplayImageButtonsActivity
import com.datadog.android.sdk.rules.SessionReplayTestRule
import com.datadog.android.sdk.utils.SR_PRIVACY_LEVEL
import com.datadog.android.sessionreplay.SessionReplayPrivacy
import org.junit.Rule
import org.junit.Test

internal class SrImageButtonsMaskUserInputTest :
    BaseSessionReplayTest<SessionReplayImageButtonsActivity>() {

    @get:Rule
    val rule = SessionReplayTestRule(
        SessionReplayImageButtonsActivity::class.java,
        trackingConsent = TrackingConsent.GRANTED,
        keepRequests = true,
        intentExtras = mapOf(SR_PRIVACY_LEVEL to SessionReplayPrivacy.MASK_USER_INPUT)
    )

    @Test
    fun assessRecordedScreenPayload() {
        runInstrumentationScenario()
        assessSrPayload(EXPECTED_PAYLOAD_FILE_NAME, rule)
    }

    companion object {
        const val EXPECTED_PAYLOAD_FILE_NAME = "sr_image_buttons_mask_user_input_payload.json"
    }
}
