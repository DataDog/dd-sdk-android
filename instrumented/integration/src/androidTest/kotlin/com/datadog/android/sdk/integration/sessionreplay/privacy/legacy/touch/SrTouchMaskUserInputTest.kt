/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sdk.integration.sessionreplay.privacy.legacy.touch

import androidx.test.espresso.Espresso
import androidx.test.espresso.action.ViewActions
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.platform.app.InstrumentationRegistry
import com.datadog.android.privacy.TrackingConsent
import com.datadog.android.sdk.integration.R
import com.datadog.android.sdk.integration.RuntimeConfig
import com.datadog.android.sdk.integration.sessionreplay.BaseSessionReplayTest
import com.datadog.android.sdk.integration.sessionreplay.INITIAL_WAIT_MS
import com.datadog.android.sdk.integration.sessionreplay.SessionReplaySegmentUtils.extractSrSegmentAsJson
import com.datadog.android.sdk.integration.sessionreplay.SessionReplayTouchActivity
import com.datadog.android.sdk.integration.sessionreplay.UI_THREAD_DELAY_MS
import com.datadog.android.sdk.rules.HandledRequest
import com.datadog.android.sdk.rules.SessionReplayTestRule
import com.datadog.android.sdk.utils.SR_PRIVACY_LEVEL
import com.datadog.android.sdk.utils.waitFor
import com.datadog.android.sessionreplay.RECORD_TYPE_INCREMENTAL_SNAPSHOT
import com.datadog.android.sessionreplay.SessionReplayPrivacy
import com.datadog.tools.unit.ConditionWatcher
import com.google.gson.JsonObject
import org.assertj.core.api.Assertions.assertThat
import org.junit.Rule
import org.junit.Test

internal class SrTouchMaskUserInputTest : BaseSessionReplayTest<SessionReplayTouchActivity>() {

    @get:Rule
    override val rule = SessionReplayTestRule(
        SessionReplayTouchActivity::class.java,
        trackingConsent = TrackingConsent.GRANTED,
        keepRequests = true,
        intentExtras = mapOf(SR_PRIVACY_LEVEL to SessionReplayPrivacy.MASK_USER_INPUT)
    )

    @Test
    fun assessTouchRecordsWithMaskUserInput() {
        runTouchScenario()

        ConditionWatcher {
            val requests = rule.getRequests(RuntimeConfig.sessionReplayEndpointUrl)
            val records = extractRecordsFromRequests(requests)

            assertRecordStructure(records)

            val touchRecords = extractTouchRecordsFromRequests(requests)

            assertThat(touchRecords)
                .describedAs(
                    "Should NOT capture touch records with MASK_USER_INPUT privacy (maps to TouchPrivacy.HIDE)"
                )
                .isEmpty()

            true
        }.doWait(timeoutMs = INITIAL_WAIT_MS)
    }

    private fun runTouchScenario() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()

        Espresso.onView(ViewMatchers.withId(R.id.button1))
            .perform(ViewActions.click())

        Espresso.onView(ViewMatchers.isRoot()).perform(waitFor(UI_THREAD_DELAY_MS))
        instrumentation.waitForIdleSync()
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
