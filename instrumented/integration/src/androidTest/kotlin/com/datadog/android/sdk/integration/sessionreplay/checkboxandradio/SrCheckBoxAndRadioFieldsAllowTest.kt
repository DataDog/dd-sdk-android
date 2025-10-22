/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sdk.integration.sessionreplay.checkboxandradio

import android.os.Build
import androidx.test.filters.SdkSuppress
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

internal class SrCheckBoxAndRadioFieldsAllowTest :
    BaseSessionReplayTest<SessionReplayRadioCheckboxFieldsActivity>() {

    @get:Rule
    val rule = SessionReplayTestRule(
        SessionReplayRadioCheckboxFieldsActivity::class.java,
        trackingConsent = TrackingConsent.GRANTED,
        keepRequests = true,
        intentExtras = mapOf(SR_PRIVACY_LEVEL to SessionReplayPrivacy.ALLOW)
    )

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.N)
    fun assessRecordedScreenPayload() {
        runInstrumentationScenario()
        
        ConditionWatcher {
            val requests = rule.getRequests(RuntimeConfig.sessionReplayEndpointUrl)
            val records = extractRecordsFromRequests(requests)
            
            assertRecordStructure(records)
            
            val wireframes = extractWireframesFromRequests(requests)
            
            assertThat(wireframes)
                .describedAs("Should capture wireframes with ALLOW privacy")
                .isNotEmpty
            
            true
        }.doWait(timeoutMs = INITIAL_WAIT_MS)
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.M, maxSdkVersion = Build.VERSION_CODES.M)
    fun assessRecordedScreenPayload23() {
        runInstrumentationScenario()
        
        ConditionWatcher {
            val requests = rule.getRequests(RuntimeConfig.sessionReplayEndpointUrl)
            val records = extractRecordsFromRequests(requests)
            
            assertRecordStructure(records)
            
            val wireframes = extractWireframesFromRequests(requests)
            
            assertThat(wireframes)
                .describedAs("Should capture wireframes with ALLOW privacy on API 23")
                .isNotEmpty
            
            true
        }.doWait(timeoutMs = INITIAL_WAIT_MS)
    }
}
