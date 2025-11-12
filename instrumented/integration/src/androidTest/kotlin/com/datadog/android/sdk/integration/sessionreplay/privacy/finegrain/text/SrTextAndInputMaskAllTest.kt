/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sdk.integration.sessionreplay.privacy.finegrain.text

import com.datadog.android.privacy.TrackingConsent
import com.datadog.android.sdk.integration.sessionreplay.SessionReplayTextAndInputPrivacyActivity
import com.datadog.android.sdk.rules.SessionReplayTestRule
import com.datadog.android.sdk.utils.SR_TEXT_AND_INPUT_PRIVACY
import com.datadog.android.sessionreplay.TextAndInputPrivacy
import org.junit.Rule
import org.junit.Test

internal class SrTextAndInputMaskAllTest :
    TextAndInputPrivacyTestBase<SessionReplayTextAndInputPrivacyActivity>() {

    @get:Rule
    val rule = SessionReplayTestRule(
        SessionReplayTextAndInputPrivacyActivity::class.java,
        trackingConsent = TrackingConsent.GRANTED,
        keepRequests = true,
        intentExtras = mapOf(
            SR_TEXT_AND_INPUT_PRIVACY to TextAndInputPrivacy.MASK_ALL
        )
    )

    @Test
    fun assessMaskAllPayload() {
        runInstrumentationScenario()
        assertStaticTextMasked(rule, "Default Text View")
        assertInputTextMaskedWithFixedMask(rule)
    }
}
