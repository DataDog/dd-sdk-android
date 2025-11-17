/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sdk.integration.sessionreplay.privacy.legacy.images

import com.datadog.android.privacy.TrackingConsent
import com.datadog.android.sdk.integration.RuntimeConfig
import com.datadog.android.sdk.integration.sessionreplay.BaseSessionReplayTest
import com.datadog.android.sdk.integration.sessionreplay.INITIAL_WAIT_MS
import com.datadog.android.sdk.integration.sessionreplay.SessionReplayImagesMixedSizesActivity
import com.datadog.android.sdk.rules.SessionReplayTestRule
import com.datadog.android.sdk.utils.SR_PRIVACY_LEVEL
import com.datadog.android.sessionreplay.IMAGE_DIMEN_CONSIDERED_PII_IN_DP
import com.datadog.android.sessionreplay.SessionReplayPrivacy
import com.datadog.android.sessionreplay.model.WIREFRAME_TYPE_IMAGE
import com.datadog.android.sessionreplay.model.WIREFRAME_TYPE_PLACEHOLDER
import com.datadog.tools.unit.ConditionWatcher
import org.assertj.core.api.Assertions.assertThat
import org.junit.Rule
import org.junit.Test

internal class SrImagesMaskUserInputTest :
    BaseSessionReplayTest<SessionReplayImagesMixedSizesActivity>() {

    @get:Rule
    override val rule = SessionReplayTestRule(
        SessionReplayImagesMixedSizesActivity::class.java,
        trackingConsent = TrackingConsent.GRANTED,
        keepRequests = true,
        intentExtras = mapOf(SR_PRIVACY_LEVEL to SessionReplayPrivacy.MASK_USER_INPUT)
    )

    @Test
    fun assessRecordedScreenPayload() {
        ConditionWatcher {
            val requests = rule.getRequests(RuntimeConfig.sessionReplayEndpointUrl)
            val records = extractRecordsFromRequests(requests)

            assertRecordStructure(records)

            val wireframes = extractWireframesFromRequests(requests)

            val imageWireframes = wireframes.filter { wireframe ->
                wireframe.get("type")?.asString == WIREFRAME_TYPE_IMAGE
            }

            val placeholderWireframes = wireframes.filter { wireframe ->
                wireframe.get("type")?.asString == WIREFRAME_TYPE_PLACEHOLDER
            }

            assertThat(imageWireframes)
                .describedAs(
                    "Should capture small images (<${IMAGE_DIMEN_CONSIDERED_PII_IN_DP}dp) as image wireframes with " +
                        "MASK_USER_INPUT privacy (MASK_LARGE_ONLY)"
                )
                .hasSizeGreaterThanOrEqualTo(1)

            assertThat(placeholderWireframes)
                .describedAs(
                    "Should capture large images (>=${IMAGE_DIMEN_CONSIDERED_PII_IN_DP}dp) as placeholder " +
                        "wireframes with MASK_USER_INPUT privacy (MASK_LARGE_ONLY)"
                )
                .hasSizeGreaterThanOrEqualTo(1)

            true
        }.doWait(timeoutMs = INITIAL_WAIT_MS)
    }
}
