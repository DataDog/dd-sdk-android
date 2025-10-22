/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sdk.integration.sessionreplay

import com.datadog.android.privacy.TrackingConsent
import com.datadog.android.sdk.integration.RuntimeConfig
import com.datadog.android.sdk.rules.SessionReplayTestRule
import com.datadog.tools.unit.ConditionWatcher
import org.assertj.core.api.Assertions.assertThat
import org.junit.Rule
import org.junit.Test

internal class ConsentGrantedSrTest : BaseSessionReplayTest<SessionReplayPlaygroundActivity>() {

    @get:Rule
    val rule = SessionReplayTestRule(
        SessionReplayPlaygroundActivity::class.java,
        trackingConsent = TrackingConsent.GRANTED,
        keepRequests = true
    )

    @Test
    fun assessRecordedScreenPayload() {
        runInstrumentationScenario()
        
        ConditionWatcher {
            val requests = rule.getRequests(RuntimeConfig.sessionReplayEndpointUrl)
            val records = extractRecordsFromRequests(requests)
            
            assertThat(records)
                .describedAs("Session Replay should capture records when consent is granted")
                .isNotEmpty
            
            val metaRecord = records.firstOrNull { it.get("type")?.asString == "4" }
            val focusRecord = records.firstOrNull { it.get("type")?.asString == "6" }
            val fullSnapshotRecord = records.firstOrNull { it.get("type")?.asString == "10" }
            
            assertThat(metaRecord)
                .describedAs("Should contain a meta record (type 4)")
                .isNotNull
            
            assertThat(focusRecord)
                .describedAs("Should contain a focus record (type 6)")
                .isNotNull
            
            assertThat(fullSnapshotRecord)
                .describedAs("Should contain a full snapshot record (type 10)")
                .isNotNull
            
            val wireframes = fullSnapshotRecord
                ?.asJsonObject
                ?.get("data")?.asJsonObject
                ?.getAsJsonArray("wireframes")
            
            assertThat(wireframes)
                .describedAs("Full snapshot should contain wireframes")
                .isNotNull
                .isNotEmpty
            
            true
        }.doWait(timeoutMs = INITIAL_WAIT_MS)
    }
}
