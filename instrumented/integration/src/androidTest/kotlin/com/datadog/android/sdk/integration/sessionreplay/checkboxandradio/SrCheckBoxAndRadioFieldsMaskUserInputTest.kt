/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sdk.integration.sessionreplay.checkboxandradio

import com.datadog.android.privacy.TrackingConsent
import com.datadog.android.sdk.integration.RuntimeConfig
import com.datadog.android.sdk.integration.sessionreplay.BaseSessionReplayTest
import com.datadog.android.sdk.integration.sessionreplay.SessionReplayRadioCheckboxFieldsActivity
import com.datadog.android.sdk.rules.SessionReplayTestRule
import com.datadog.android.sdk.utils.SR_PRIVACY_LEVEL
import com.datadog.android.sessionreplay.SessionReplayPrivacy
import com.datadog.tools.unit.ConditionWatcher
import org.assertj.core.api.Assertions.assertThat
import org.junit.Rule
import org.junit.Test

internal class SrCheckBoxAndRadioFieldsMaskUserInputTest :
    BaseSessionReplayTest<SessionReplayRadioCheckboxFieldsActivity>() {

    @get:Rule
    val rule = SessionReplayTestRule(
        SessionReplayRadioCheckboxFieldsActivity::class.java,
        trackingConsent = TrackingConsent.GRANTED,
        keepRequests = true,
        intentExtras = mapOf(SR_PRIVACY_LEVEL to SessionReplayPrivacy.MASK_USER_INPUT)
    )

    @Test
    fun assessRecordedScreenPayload() {
        runInstrumentationScenario()

        ConditionWatcher {
            val requests = rule.getRequests(RuntimeConfig.sessionReplayEndpointUrl)
            val records = extractRecordsFromRequests(requests)

            assertRecordStructure(records)

            val wireframes = extractWireframesFromRequests(requests)

            val shapeWireframes = wireframes.filter { wireframe ->
                wireframe.get("type")?.asString == "shape"
            }

            assertThat(shapeWireframes)
                .describedAs("Should capture checkbox/radio as masked shape wireframes with MASK_USER_INPUT privacy")
                .hasSizeGreaterThanOrEqualTo(2)

            val textWireframes = wireframes.filter { wireframe ->
                wireframe.get("type")?.asString == "text"
            }

            assertThat(textWireframes)
                .describedAs("Should capture checkbox/radio labels as visible text with MASK_USER_INPUT privacy")
                .hasSizeGreaterThanOrEqualTo(3)

            true
        }.doWait(timeoutMs = INITIAL_WAIT_MS)
    }
}
