/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sdk.integration.sessionreplay.textfields

import android.os.Build
import androidx.test.filters.SdkSuppress
import com.datadog.android.privacy.TrackingConsent
import com.datadog.android.sdk.integration.sessionreplay.BaseSessionReplayTest
import com.datadog.android.sdk.integration.sessionreplay.SessionReplayTextFieldsActivity
import com.datadog.android.sdk.rules.SessionReplayTestRule
import com.datadog.android.sdk.utils.SR_PRIVACY_LEVEL
import com.datadog.android.sessionreplay.SessionReplayPrivacy
import org.junit.Rule
import org.junit.Test

internal class SrTextFieldsAllowTest : BaseSessionReplayTest<SessionReplayTextFieldsActivity>() {

    @get:Rule
    val rule = SessionReplayTestRule(
        SessionReplayTextFieldsActivity::class.java,
        trackingConsent = TrackingConsent.GRANTED,
        keepRequests = true,
        intentExtras = mapOf(SR_PRIVACY_LEVEL to SessionReplayPrivacy.ALLOW)
    )

    // TODO RUM-6839: Fix test on API 21, the test failure is caused by different drawable
    //  on the text background among API versions. the background wireframes on higher version are
    //  images, on lower version are shapes.
    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.P)
    fun assessRecordedScreenPayload() {
        runInstrumentationScenario()
        assessSrPayload(EXPECTED_PAYLOAD_FILE_NAME, rule)
    }
    companion object {
        const val EXPECTED_PAYLOAD_FILE_NAME = "sr_text_fields_allow_payload.json"
    }
}
