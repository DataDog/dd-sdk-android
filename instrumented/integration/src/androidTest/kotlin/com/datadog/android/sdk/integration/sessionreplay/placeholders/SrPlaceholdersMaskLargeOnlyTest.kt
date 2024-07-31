/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sdk.integration.sessionreplay.placeholders

import com.datadog.android.privacy.TrackingConsent
import com.datadog.android.sdk.integration.sessionreplay.BaseSessionReplayTest
import com.datadog.android.sdk.integration.sessionreplay.SessionReplayImagesActivity
import com.datadog.android.sdk.rules.SessionReplayTestRule
import com.datadog.android.sdk.utils.SR_IMAGE_PRIVACY_LEVEL
import com.datadog.android.sessionreplay.ImagePrivacy
import org.junit.Rule
import org.junit.Test

internal class SrPlaceholdersMaskLargeOnlyTest :
    BaseSessionReplayTest<SessionReplayImagesActivity>() {

    @get:Rule
    val rule = SessionReplayTestRule(
        SessionReplayImagesActivity::class.java,
        trackingConsent = TrackingConsent.GRANTED,
        keepRequests = true,
        intentExtras = mapOf(SR_IMAGE_PRIVACY_LEVEL to ImagePrivacy.MASK_LARGE_ONLY)
    )

    @Test
    fun assessRecordedScreenPayload() {
        runInstrumentationScenario()
        assessSrPayload(EXPECTED_PAYLOAD_FILE_NAME, rule)
    }

    companion object {
        const val EXPECTED_PAYLOAD_FILE_NAME = "sr_placeholders_mask_large_only_payload.json"
    }
}
