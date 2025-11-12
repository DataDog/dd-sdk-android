/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sdk.integration.sessionreplay.privacy.legacy.text.textfields

import android.os.Build
import androidx.test.filters.SdkSuppress
import com.datadog.android.internal.sessionreplay.WIREFRAME_TYPE_TEXT
import com.datadog.android.privacy.TrackingConsent
import com.datadog.android.sdk.integration.RuntimeConfig
import com.datadog.android.sdk.integration.sessionreplay.BaseSessionReplayTest
import com.datadog.android.sdk.integration.sessionreplay.INITIAL_WAIT_MS
import com.datadog.android.sdk.integration.sessionreplay.SessionReplayTextFieldsActivity
import com.datadog.android.sdk.rules.SessionReplayTestRule
import com.datadog.android.sdk.utils.SR_PRIVACY_LEVEL
import com.datadog.android.sessionreplay.SessionReplayPrivacy
import com.datadog.tools.unit.ConditionWatcher
import org.assertj.core.api.Assertions.assertThat
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

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.P)
    fun assessRecordedScreenPayload() {
        runInstrumentationScenario()

        ConditionWatcher {
            val requests = rule.getRequests(RuntimeConfig.sessionReplayEndpointUrl)
            val records = extractRecordsFromRequests(requests)

            assertRecordStructure(records)

            val wireframes = extractWireframesFromRequests(requests)

            val textWireframes = wireframes.filter { wireframe ->
                wireframe.get("type")?.asString == WIREFRAME_TYPE_TEXT
            }

            assertThat(textWireframes)
                .describedAs("Should capture text wireframes with ALLOW privacy")
                .hasSizeGreaterThanOrEqualTo(5)

            val staticTextWireframes = textWireframes.filter { wireframe ->
                val text = wireframe.get("text")?.asString
                text == "Default Text View" || text == "MaterialTextView" || text == "AppCompat"
            }

            assertThat(staticTextWireframes)
                .describedAs("Static text should be visible (not obfuscated) with ALLOW privacy")
                .hasSizeGreaterThanOrEqualTo(1)

            val inputTextWireframes = textWireframes.filter { wireframe ->
                val text = wireframe.get("text")?.asString
                text == "User input text" || text == "AppCompat input"
            }

            assertThat(inputTextWireframes)
                .describedAs("Input text should be visible (not masked) with ALLOW privacy")
                .hasSizeGreaterThanOrEqualTo(2)

            true
        }.doWait(timeoutMs = INITIAL_WAIT_MS)
    }
}
