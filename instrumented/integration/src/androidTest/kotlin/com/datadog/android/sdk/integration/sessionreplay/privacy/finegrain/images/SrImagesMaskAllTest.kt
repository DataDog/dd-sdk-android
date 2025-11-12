/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sdk.integration.sessionreplay.privacy.finegrain.images

import com.datadog.android.internal.sessionreplay.WIREFRAME_TYPE_PLACEHOLDER
import com.datadog.android.privacy.TrackingConsent
import com.datadog.android.sdk.integration.sessionreplay.SessionReplayImagesMixedSizesActivity
import com.datadog.android.sdk.integration.sessionreplay.privacy.ImagePrivacyTestBase
import com.datadog.android.sdk.rules.SessionReplayTestRule
import com.datadog.android.sdk.utils.SR_IMAGE_PRIVACY
import com.datadog.android.sessionreplay.ImagePrivacy
import org.junit.Rule
import org.junit.Test

internal class SrImagesMaskAllTest :
    ImagePrivacyTestBase<SessionReplayImagesMixedSizesActivity>() {

    @get:Rule
    val rule = SessionReplayTestRule(
        SessionReplayImagesMixedSizesActivity::class.java,
        trackingConsent = TrackingConsent.GRANTED,
        keepRequests = true,
        intentExtras = mapOf(
            SR_IMAGE_PRIVACY to ImagePrivacy.MASK_ALL
        )
    )

    @Test
    fun assessRecordedScreenPayloadMaskAll() {
        runInstrumentationScenario()
        assertImageWireframes(
            rule = rule,
            expectedImageWireframeType = WIREFRAME_TYPE_PLACEHOLDER,
            minimumImageCount = 2
        )
    }
}
