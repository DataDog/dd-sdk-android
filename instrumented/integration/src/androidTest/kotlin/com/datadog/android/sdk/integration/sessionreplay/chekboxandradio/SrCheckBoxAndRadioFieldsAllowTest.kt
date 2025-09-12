/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sdk.integration.sessionreplay.chekboxandradio

import android.os.Build
import androidx.test.filters.SdkSuppress
import com.datadog.android.privacy.TrackingConsent
import com.datadog.android.sdk.integration.sessionreplay.BaseSessionReplayTest
import com.datadog.android.sdk.integration.sessionreplay.SessionReplayRadioCheckboxFieldsActivity
import com.datadog.android.sdk.rules.SessionReplayTestRule
import com.datadog.android.sdk.utils.SR_PRIVACY_LEVEL
import com.datadog.android.sessionreplay.SessionReplayPrivacy
import org.junit.Rule
import org.junit.Test

internal class SrCheckBoxAndRadioFieldsAllowTest :
    BaseSessionReplayTest<SessionReplayRadioCheckboxFieldsActivity>() {

    @get:Rule
    val rule = SessionReplayTestRule(
        SessionReplayRadioCheckboxFieldsActivity::class.java,
        trackingConsent = TrackingConsent.GRANTED,
        keepRequests = true,
        intentExtras = mapOf(SR_PRIVACY_LEVEL to SessionReplayPrivacy.ALLOW)
    )

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.N)
    fun assessRecordedScreenPayload() {
        runInstrumentationScenario()
        assessSrPayload(EXPECTED_PAYLOAD_FILE_NAME, rule)
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.M, maxSdkVersion = Build.VERSION_CODES.M)
    fun assessRecordedScreenPayload23() {
        runInstrumentationScenario()
        assessSrPayload(EXPECTED_PAYLOAD_FILE_NAME_OS_23, rule)
    }
    companion object {
        const val EXPECTED_PAYLOAD_FILE_NAME = "sr_checkbox_and_radio_fields_allow_payload.json"
        const val EXPECTED_PAYLOAD_FILE_NAME_OS_23 = "sr_checkbox_and_radio_fields_allow_payload_23.json"
    }
}
