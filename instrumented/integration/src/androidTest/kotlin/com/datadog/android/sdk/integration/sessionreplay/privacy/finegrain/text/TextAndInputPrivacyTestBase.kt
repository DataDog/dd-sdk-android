/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sdk.integration.sessionreplay.privacy.finegrain.text

import android.app.Activity
import com.datadog.android.sdk.integration.RuntimeConfig
import com.datadog.android.sdk.integration.sessionreplay.BaseSessionReplayTest
import com.datadog.android.sdk.integration.sessionreplay.INITIAL_WAIT_MS
import com.datadog.android.sdk.rules.HandledRequest
import com.datadog.android.sdk.rules.SessionReplayTestRule
import com.datadog.android.sessionreplay.model.RECORD_TYPE_FULL_SNAPSHOT
import com.datadog.android.sessionreplay.model.WIREFRAME_TYPE_TEXT
import com.datadog.tools.unit.ConditionWatcher
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import org.assertj.core.api.Assertions.assertThat

/**
 * Base class for Session Replay integration tests that verify text and input masking behavior.
 * Provides common utilities for extracting and asserting on text wireframes.
 */
internal abstract class TextAndInputPrivacyTestBase<R : Activity> : BaseSessionReplayTest<R>() {

    private companion object {
        const val FIXED_MASK_TEXT = "***"
    }

    protected fun extractTextWireframesFromRequests(requests: List<HandledRequest>): List<JsonObject> {
        return extractRecordsFromRequests(requests)
            .filter { it.get("type")?.asString == RECORD_TYPE_FULL_SNAPSHOT.toString() }
            .flatMap { record ->
                val data = record.asJsonObject.get("data")?.asJsonObject
                data?.getAsJsonArray("wireframes") ?: JsonArray()
            }
            .map { it.asJsonObject }
            .filter { it.get("type")?.asString == WIREFRAME_TYPE_TEXT }
    }

    /**
     * Common pattern for asserting on text wireframes with a custom predicate.
     *
     * @param rule The test rule for accessing requests
     * @param assertion Lambda that performs assertions on the text wireframes
     */
    private fun assertOnTextWireframes(
        rule: SessionReplayTestRule<R>,
        assertion: (List<JsonObject>) -> Unit
    ) {
        ConditionWatcher {
            val requests = rule.getRequests(RuntimeConfig.sessionReplayEndpointUrl)
            val textWireframes = extractTextWireframesFromRequests(requests)
            assertion(textWireframes)
            true
        }.doWait(timeoutMs = INITIAL_WAIT_MS)
    }

    protected fun assertStaticTextVisible(
        rule: SessionReplayTestRule<R>,
        expectedText: String
    ) {
        assertOnTextWireframes(rule) { textWireframes ->
            val staticTextWireframe = textWireframes.firstOrNull {
                it.get("text")?.asString == expectedText
            }
            assertThat(staticTextWireframe)
                .describedAs("Static text should be visible with text: $expectedText")
                .isNotNull
        }
    }

    protected fun assertStaticTextMasked(
        rule: SessionReplayTestRule<R>,
        originalText: String
    ) {
        assertOnTextWireframes(rule) { textWireframes ->
            val maskedTextWireframe = textWireframes.firstOrNull {
                val text = it.get("text")?.asString
                text != null && text != originalText && text.isNotEmpty()
            }
            assertThat(maskedTextWireframe)
                .describedAs("Static text should be masked (not equal to original: $originalText)")
                .isNotNull
        }
    }

    protected fun assertInputTextVisible(
        rule: SessionReplayTestRule<R>,
        expectedText: String
    ) {
        assertOnTextWireframes(rule) { textWireframes ->
            val inputTextWireframe = textWireframes.firstOrNull {
                it.get("text")?.asString == expectedText
            }
            assertThat(inputTextWireframe)
                .describedAs("Input text should be visible with text: $expectedText")
                .isNotNull
        }
    }

    protected fun assertInputTextMaskedWithFixedMask(rule: SessionReplayTestRule<R>) {
        assertOnTextWireframes(rule) { textWireframes ->
            val maskedInputWireframe = textWireframes.firstOrNull {
                it.get("text")?.asString == FIXED_MASK_TEXT
            }
            assertThat(maskedInputWireframe)
                .describedAs("Input should be masked with fixed mask: $FIXED_MASK_TEXT")
                .isNotNull
        }
    }

    protected fun assertSensitiveInputMasked(rule: SessionReplayTestRule<R>) {
        assertOnTextWireframes(rule) { textWireframes ->
            val sensitiveWireframes = textWireframes.filter {
                it.get("text")?.asString == FIXED_MASK_TEXT
            }
            assertThat(sensitiveWireframes)
                .describedAs("Sensitive inputs (password, email, phone) should be masked with $FIXED_MASK_TEXT")
                .hasSizeGreaterThanOrEqualTo(3)
        }
    }
}
