/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sdk.integration.sessionreplay.trackingconsent

import com.datadog.android.internal.sessionreplay.RECORD_TYPE_FOCUS
import com.datadog.android.internal.sessionreplay.RECORD_TYPE_FULL_SNAPSHOT
import com.datadog.android.internal.sessionreplay.RECORD_TYPE_META
import com.datadog.android.privacy.TrackingConsent
import com.datadog.android.sdk.integration.RuntimeConfig
import com.datadog.android.sdk.integration.sessionreplay.BaseSessionReplayTest
import com.datadog.android.sdk.integration.sessionreplay.INITIAL_WAIT_MS
import com.datadog.android.sdk.integration.sessionreplay.SessionReplayPlaygroundActivity
import com.datadog.android.sdk.rules.SessionReplayTestRule
import com.datadog.tools.unit.ConditionWatcher
import org.assertj.core.api.Assertions.assertThat
import org.junit.Rule
import org.junit.Test

internal class ConsentGrantedSrTest : BaseSessionReplayTest<SessionReplayPlaygroundActivity>() {

    @get:Rule
    override val rule = SessionReplayTestRule(
        SessionReplayPlaygroundActivity::class.java,
        trackingConsent = TrackingConsent.GRANTED,
        keepRequests = true
    )

    @Test
    fun assessRecordedScreenPayload() {
        ConditionWatcher {
            val requests = rule.getRequests(RuntimeConfig.sessionReplayEndpointUrl)
            val records = extractRecordsFromRequests(requests)

            assertThat(records)
                .describedAs("Session Replay should capture records when consent is granted")
                .isNotEmpty

            val metaRecord = records.firstOrNull {
                it.get("type")?.asString == RECORD_TYPE_META.toString()
            }
            val focusRecord = records.firstOrNull {
                it.get("type")?.asString == RECORD_TYPE_FOCUS.toString()
            }
            val fullSnapshotRecord = records.firstOrNull {
                it.get("type")?.asString == RECORD_TYPE_FULL_SNAPSHOT.toString()
            }

            assertThat(metaRecord)
                .describedAs("Should contain a meta record (type $RECORD_TYPE_META)")
                .isNotNull

            assertThat(focusRecord)
                .describedAs("Should contain a focus record (type $RECORD_TYPE_FOCUS)")
                .isNotNull

            assertThat(fullSnapshotRecord)
                .describedAs("Should contain a full snapshot record (type $RECORD_TYPE_FULL_SNAPSHOT)")
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
