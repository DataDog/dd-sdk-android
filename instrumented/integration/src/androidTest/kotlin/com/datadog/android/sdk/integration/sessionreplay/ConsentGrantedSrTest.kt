/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sdk.integration.sessionreplay

import android.os.Build
import androidx.test.filters.SdkSuppress
import com.datadog.android.privacy.TrackingConsent
import com.datadog.android.sdk.rules.SessionReplayTestRule
import org.junit.Rule
import org.junit.Test

internal class ConsentGrantedSrTest : BaseSessionReplayTest<SessionReplayPlaygroundActivity>() {

    @get:Rule
    val rule = SessionReplayTestRule(
        SessionReplayPlaygroundActivity::class.java,
        trackingConsent = TrackingConsent.GRANTED,
        keepRequests = true
    )

    // TODO RUM-6839: Fix test on API 21
    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.P)
    fun assessRecordedScreenPayload() {
        runInstrumentationScenario()
        assessSrPayload(EXPECTED_PAYLOAD_FILE_NAME, rule)
    }

    companion object {
        const val EXPECTED_PAYLOAD_FILE_NAME = "consent_granted_sr_test_payload.json"
    }
}
