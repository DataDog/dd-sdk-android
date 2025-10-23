/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sdk.integration.sessionreplay.images

import com.datadog.android.privacy.TrackingConsent
import com.datadog.android.sdk.integration.sessionreplay.SessionReplayImagesMixedSizesActivity
import com.datadog.android.sdk.rules.SessionReplayTestRule
import com.datadog.android.sdk.utils.SR_IMAGE_PRIVACY
import com.datadog.android.sdk.utils.SR_PRIVACY_LEVEL
import com.datadog.android.sessionreplay.ImagePrivacy
import com.datadog.android.sessionreplay.SessionReplayPrivacy
import org.junit.Rule
import org.junit.Test

internal class SrImagesMaskLargeOnlyTest :
    ImagePrivacyTestBase<SessionReplayImagesMixedSizesActivity>() {

    @get:Rule
    val rule = SessionReplayTestRule(
        SessionReplayImagesMixedSizesActivity::class.java,
        trackingConsent = TrackingConsent.GRANTED,
        keepRequests = true,
        intentExtras = mapOf(
            SR_PRIVACY_LEVEL to SessionReplayPrivacy.ALLOW,
            SR_IMAGE_PRIVACY to ImagePrivacy.MASK_LARGE_ONLY
        )
    )

    @Test
    fun assessRecordedScreenPayloadMaskLargeOnly() {
        runInstrumentationScenario()
        assertMixedImageWireframes(
            rule = rule,
            expectedPlaceholderCount = 1,
            expectedImageCount = 1
        )
    }
}
