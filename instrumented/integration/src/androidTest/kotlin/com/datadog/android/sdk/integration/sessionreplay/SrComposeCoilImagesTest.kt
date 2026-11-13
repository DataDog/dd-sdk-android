/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sdk.integration.sessionreplay

import com.datadog.android.privacy.TrackingConsent
import com.datadog.android.sdk.integration.RuntimeConfig
import com.datadog.android.sdk.rules.HandledRequest
import com.datadog.android.sdk.rules.SessionReplayTestRule
import com.datadog.android.sdk.utils.SR_IMAGE_PRIVACY
import com.datadog.android.sessionreplay.ImagePrivacy
import com.datadog.android.sessionreplay.RECORD_TYPE_FULL_SNAPSHOT
import com.datadog.android.sessionreplay.RECORD_TYPE_INCREMENTAL_SNAPSHOT
import com.datadog.android.sessionreplay.WIREFRAME_TYPE_IMAGE
import com.datadog.tools.unit.ConditionWatcher
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import org.assertj.core.api.Assertions.assertThat
import org.junit.Rule
import org.junit.Test

internal class SrComposeCoilImagesTest : BaseSessionReplayTest<SessionReplayComposeCoilActivity>() {

    @get:Rule
    override val rule = SessionReplayTestRule(
        SessionReplayComposeCoilActivity::class.java,
        trackingConsent = TrackingConsent.GRANTED,
        keepRequests = true,
        intentExtras = mapOf(
            SR_IMAGE_PRIVACY to ImagePrivacy.MASK_NONE
        )
    )

    @Test
    fun assessRecordedCoilCrossfadeImagesMaskNone() {
        ConditionWatcher {
            val requests = rule.getRequests(RuntimeConfig.sessionReplayEndpointUrl)
            val imageWireframeIds = extractSnapshotAndAddedWireframes(requests)
                .filter { it.get("type")?.asString == WIREFRAME_TYPE_IMAGE }
                .map { it.get("id").asLong }
                .toSet()

            assertThat(imageWireframeIds)
                .describedAs("Expected an image wireframe for each Coil image")
                .hasSize(EXPECTED_IMAGE_COUNT)

            true
        }.doWait(timeoutMs = INITIAL_WAIT_MS)
    }

    /**
     * Coil loads the images after the first full snapshot, so their wireframes can also
     * arrive as additions in incremental snapshots.
     */
    private fun extractSnapshotAndAddedWireframes(requests: List<HandledRequest>): List<JsonObject> {
        return extractRecordsFromRequests(requests)
            .flatMap { record ->
                val data = record.get("data")?.asJsonObject
                when (record.get("type")?.asString) {
                    RECORD_TYPE_FULL_SNAPSHOT.toString() ->
                        data?.getAsJsonArray("wireframes") ?: JsonArray()
                    RECORD_TYPE_INCREMENTAL_SNAPSHOT.toString() ->
                        data?.getAsJsonArray("adds")?.mapNotNull { it.asJsonObject.get("wireframe") }
                            ?: emptyList()
                    else -> emptyList()
                }
            }
            .map { it.asJsonObject }
    }

    private companion object {
        const val EXPECTED_IMAGE_COUNT = 4
    }
}
