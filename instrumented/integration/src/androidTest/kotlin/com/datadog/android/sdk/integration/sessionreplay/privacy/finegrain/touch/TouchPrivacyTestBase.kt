/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sdk.integration.sessionreplay.privacy.finegrain.touch

import android.app.Activity
import androidx.test.espresso.Espresso
import androidx.test.espresso.action.ViewActions
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.platform.app.InstrumentationRegistry
import com.datadog.android.sdk.integration.R
import com.datadog.android.sdk.integration.RuntimeConfig
import com.datadog.android.sdk.integration.sessionreplay.BaseSessionReplayTest
import com.datadog.android.sdk.integration.sessionreplay.INITIAL_WAIT_MS
import com.datadog.android.sdk.integration.sessionreplay.SessionReplaySegmentUtils.extractSrSegmentAsJson
import com.datadog.android.sdk.integration.sessionreplay.UI_THREAD_DELAY_MS
import com.datadog.android.sdk.rules.HandledRequest
import com.datadog.android.sdk.rules.SessionReplayTestRule
import com.datadog.android.sdk.utils.waitFor
import com.datadog.android.sessionreplay.RECORD_TYPE_INCREMENTAL_SNAPSHOT
import com.datadog.tools.unit.ConditionWatcher
import com.google.gson.JsonObject
import org.assertj.core.api.Assertions.assertThat

/**
 * Base class for Session Replay integration tests that verify touch event privacy behavior.
 * Provides common utilities for extracting and asserting on touch interaction records.
 */
internal abstract class TouchPrivacyTestBase<T : Activity> : BaseSessionReplayTest<T>() {

    protected fun runTouchScenario() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()

        Espresso.onView(ViewMatchers.withId(R.id.button1))
            .perform(ViewActions.click())

        Espresso.onView(ViewMatchers.isRoot()).perform(waitFor(UI_THREAD_DELAY_MS))
        instrumentation.waitForIdleSync()
    }

    protected fun assertTouchRecordsExist(rule: SessionReplayTestRule<T>) {
        ConditionWatcher {
            val requests = rule.getRequests(RuntimeConfig.sessionReplayEndpointUrl)
            val touchRecords = extractTouchRecordsFromRequests(requests)

            assertThat(touchRecords)
                .describedAs("Should capture touch interaction records with SHOW privacy")
                .isNotEmpty

            touchRecords.forEach { record ->
                val data = record.get("data")?.asJsonObject
                assertThat(data).isNotNull

                val pointerType = data?.get("pointerType")?.asString
                val x = data?.get("x")?.asLong
                val y = data?.get("y")?.asLong

                requireNotNull(x)
                requireNotNull(y)

                assertThat(x).isGreaterThan(0)
                assertThat(y).isGreaterThan(0)

                assertThat(pointerType)
                    .describedAs("Pointer type should be TOUCH")
                    .isEqualTo("touch")
            }

            true
        }.doWait(timeoutMs = INITIAL_WAIT_MS)
    }

    protected fun assertNoTouchRecords(rule: SessionReplayTestRule<T>) {
        ConditionWatcher {
            val requests = rule.getRequests(RuntimeConfig.sessionReplayEndpointUrl)
            val touchRecords = extractTouchRecordsFromRequests(requests)

            assertThat(touchRecords)
                .describedAs("Should NOT capture touch records with HIDE privacy")
                .isEmpty()

            true
        }.doWait(timeoutMs = INITIAL_WAIT_MS)
    }

    private fun extractTouchRecordsFromRequests(requests: List<HandledRequest>): List<JsonObject> {
        return requests
            .mapNotNull { it.extractSrSegmentAsJson()?.asJsonObject }
            .flatMap { segment ->
                segment.getAsJsonArray("records")
                    ?.filter { record ->
                        val recordObj = record.asJsonObject
                        val type = recordObj.get("type")?.asString
                        type == RECORD_TYPE_INCREMENTAL_SNAPSHOT.toString()
                    }
                    ?.filter { record ->
                        val data = record.asJsonObject.get("data")?.asJsonObject
                        data?.has("pointerType") == true
                    }
                    ?.map { it.asJsonObject }
                    ?: emptyList()
            }
    }
}
