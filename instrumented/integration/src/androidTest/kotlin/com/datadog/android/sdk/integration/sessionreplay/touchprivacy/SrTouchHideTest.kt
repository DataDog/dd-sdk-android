/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sdk.integration.sessionreplay.touchprivacy

import com.datadog.android.privacy.TrackingConsent
import com.datadog.android.sdk.integration.sessionreplay.SessionReplayTouchActivity
import com.datadog.android.sdk.rules.SessionReplayTestRule
import com.datadog.android.sdk.utils.SR_PRIVACY_LEVEL
import com.datadog.android.sdk.utils.SR_TOUCH_PRIVACY
import com.datadog.android.sessionreplay.SessionReplayPrivacy
import com.datadog.android.sessionreplay.TouchPrivacy
import org.junit.Rule
import org.junit.Test

internal class SrTouchHideTest : TouchPrivacyTestBase<SessionReplayTouchActivity>() {

    @get:Rule
    val rule = SessionReplayTestRule(
        SessionReplayTouchActivity::class.java,
        trackingConsent = TrackingConsent.GRANTED,
        keepRequests = true,
        intentExtras = mapOf(
            SR_PRIVACY_LEVEL to SessionReplayPrivacy.ALLOW,
            SR_TOUCH_PRIVACY to TouchPrivacy.HIDE
        )
    )

    @Test
    fun assessTouchRecordsWithHide() {
        runTouchScenario()
        assertNoTouchRecords(rule)
    }
}
