/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sdk.integration.sessionreplay.composition

import com.datadog.android.privacy.TrackingConsent
import com.datadog.android.sdk.integration.RuntimeConfig
import com.datadog.android.sdk.integration.sessionreplay.BaseSessionReplayTest
import com.datadog.android.sdk.integration.sessionreplay.INITIAL_WAIT_MS
import com.datadog.android.sdk.integration.sessionreplay.SessionReplayCompositionImageButtonsActivity
import com.datadog.android.sdk.rules.SessionReplayTestRule
import com.datadog.android.sdk.utils.SR_PRIVACY_LEVEL
import com.datadog.android.sessionreplay.SessionReplayPrivacy
import com.datadog.android.sessionreplay.WIREFRAME_TYPE_IMAGE
import com.datadog.tools.unit.ConditionWatcher
import org.assertj.core.api.Assertions.assertThat
import org.junit.Rule
import org.junit.Test

/**
 * Verifies the experimental composition-tree pipeline (`setCompositionTreeRecordingEnabled`)
 * records real on-screen content end-to-end - traversal, pixel-fallback resolution, wire mapping,
 * upload - using the same ImageButton fixture `SrImageButtonsAllowTest` already verifies for the
 * legacy pipeline. Prior to this test the pipeline had no automated coverage beyond unit tests
 * exercising hand-built fixtures, not a real View hierarchy.
 *
 * Source image drawables must remain recordable without the optional text-detection module.
 * This asserts the same image behavior as the legacy fixture, through the composition pipeline.
 */
internal class SrCompositionImageButtonsAllowTest :
    BaseSessionReplayTest<SessionReplayCompositionImageButtonsActivity>() {

    @get:Rule
    override val rule = SessionReplayTestRule(
        SessionReplayCompositionImageButtonsActivity::class.java,
        trackingConsent = TrackingConsent.GRANTED,
        keepRequests = true,
        intentExtras = mapOf(SR_PRIVACY_LEVEL to SessionReplayPrivacy.ALLOW)
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

            assertThat(imageWireframes)
                .describedAs(
                    "Composition pipeline should record source image buttons without a text detector"
                )
                .hasSizeGreaterThanOrEqualTo(2)

            val resourceIds = imageWireframes.mapNotNull { it.get("resourceId")?.asString }
            assertThat(resourceIds).isNotEmpty()
            resourceIds.forEach { id ->
                assertThat(requests.any { it.textBody?.contains("filename=\"$id\"") == true })
                    .describedAs("Image resource %s should reach the local intake", id)
                    .isTrue()
            }

            true
        }.doWait(timeoutMs = INITIAL_WAIT_MS)
    }
}
