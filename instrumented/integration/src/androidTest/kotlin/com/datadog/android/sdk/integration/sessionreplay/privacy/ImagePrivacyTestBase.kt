/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sdk.integration.sessionreplay.privacy

import android.app.Activity
import com.datadog.android.internal.sessionreplay.RECORD_TYPE_FULL_SNAPSHOT
import com.datadog.android.internal.sessionreplay.WIREFRAME_TYPE_IMAGE
import com.datadog.android.internal.sessionreplay.WIREFRAME_TYPE_PLACEHOLDER
import com.datadog.android.sdk.integration.RuntimeConfig
import com.datadog.android.sdk.integration.sessionreplay.BaseSessionReplayTest
import com.datadog.android.sdk.integration.sessionreplay.INITIAL_WAIT_MS
import com.datadog.android.sdk.integration.sessionreplay.SessionReplaySegmentUtils.extractSrSegmentAsJson
import com.datadog.android.sdk.rules.HandledRequest
import com.datadog.android.sdk.rules.SessionReplayTestRule
import com.datadog.tools.unit.ConditionWatcher
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import org.assertj.core.api.Assertions

/**
 * Base class for Session Replay integration tests that verify image masking behavior.
 * Provides common utilities for extracting and asserting on image wireframes.
 */
internal abstract class ImagePrivacyTestBase<R : Activity> : BaseSessionReplayTest<R>() {

    protected fun assertImageWireframes(
        rule: SessionReplayTestRule<R>,
        expectedImageWireframeType: String,
        minimumImageCount: Int = 1
    ) {
        ConditionWatcher {
            val requests = rule.getRequests(RuntimeConfig.sessionReplayEndpointUrl)
            val wireframes = extractImageWireframesFromRequests(requests)

            Assertions.assertThat(wireframes)
                .describedAs("Expected at least $minimumImageCount image wireframes")
                .hasSizeGreaterThanOrEqualTo(minimumImageCount)

            wireframes.forEach { wireframe ->
                val type = wireframe.get("type")?.asString
                Assertions.assertThat(type)
                    .describedAs("All image wireframes should be of type '$expectedImageWireframeType'")
                    .isEqualTo(expectedImageWireframeType)
            }

            true
        }.doWait(timeoutMs = INITIAL_WAIT_MS)
    }

    protected fun assertMixedImageWireframes(
        rule: SessionReplayTestRule<R>,
        expectedPlaceholderCount: Int,
        expectedImageCount: Int
    ) {
        ConditionWatcher {
            val requests = rule.getRequests(RuntimeConfig.sessionReplayEndpointUrl)
            val wireframes = extractImageWireframesFromRequests(requests)

            val placeholderWireframes = wireframes.filter {
                it.get("type")?.asString == WIREFRAME_TYPE_PLACEHOLDER
            }
            val imageWireframes = wireframes.filter {
                it.get("type")?.asString == WIREFRAME_TYPE_IMAGE
            }

            Assertions.assertThat(placeholderWireframes)
                .describedAs("Expected $expectedPlaceholderCount placeholder wireframes for large images")
                .hasSize(expectedPlaceholderCount)

            Assertions.assertThat(imageWireframes)
                .describedAs("Expected $expectedImageCount image wireframes for small images")
                .hasSize(expectedImageCount)

            true
        }.doWait(timeoutMs = INITIAL_WAIT_MS)
    }

    private fun extractImageWireframesFromRequests(requests: List<HandledRequest>): List<JsonObject> {
        return requests
            .mapNotNull { it.extractSrSegmentAsJson()?.asJsonObject }
            .flatMap { segment ->
                segment.getAsJsonArray("records")
                    ?.filter { record ->
                        val recordObj = record.asJsonObject
                        recordObj.get("type")?.asString == RECORD_TYPE_FULL_SNAPSHOT.toString()
                    }
                    ?.flatMap { record ->
                        val data = record.asJsonObject.get("data")?.asJsonObject
                        data?.getAsJsonArray("wireframes") ?: JsonArray()
                    }
                    ?.filter { wireframe ->
                        val wireframeObj = wireframe.asJsonObject
                        val type = wireframeObj.get("type")?.asString
                        type == WIREFRAME_TYPE_IMAGE ||
                            type == WIREFRAME_TYPE_PLACEHOLDER
                    }
                    ?.map { it.asJsonObject }
                    ?: emptyList()
            }
    }
}
