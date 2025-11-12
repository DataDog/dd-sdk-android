/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sdk.integration.sessionreplay

import android.app.Activity
import androidx.test.espresso.Espresso
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.platform.app.InstrumentationRegistry
import com.datadog.android.Datadog
import com.datadog.android.internal.sessionreplay.RECORD_TYPE_FOCUS
import com.datadog.android.internal.sessionreplay.RECORD_TYPE_FULL_SNAPSHOT
import com.datadog.android.internal.sessionreplay.RECORD_TYPE_META
import com.datadog.android.rum.GlobalRumMonitor
import com.datadog.android.sdk.integration.sessionreplay.SessionReplaySegmentUtils.extractSrSegmentAsJson
import com.datadog.android.sdk.rules.HandledRequest
import com.datadog.android.sdk.utils.waitFor
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import org.assertj.core.api.Assertions.assertThat
import org.junit.After

internal abstract class BaseSessionReplayTest<R : Activity> {

    @After
    fun tearDown() {
        GlobalRumMonitor.get().stopSession()
        Datadog.stopInstance()
    }

    protected fun extractRecordsFromRequests(handledRequests: List<HandledRequest>): List<JsonObject> {
        return handledRequests
            .mapNotNull { it.extractSrSegmentAsJson()?.asJsonObject }
            .flatMap { it.getAsJsonArray("records") }
            .map { it.asJsonObject }
    }

    protected fun extractWireframesFromRequests(handledRequests: List<HandledRequest>): List<JsonObject> {
        return extractRecordsFromRequests(handledRequests)
            .filter { it.get("type")?.asString == RECORD_TYPE_FULL_SNAPSHOT.toString() }
            .flatMap { record ->
                val data = record.asJsonObject.get("data")?.asJsonObject
                data?.getAsJsonArray("wireframes") ?: JsonArray()
            }
            .map { it.asJsonObject }
    }

    protected fun assertRecordStructure(records: List<JsonObject>) {
        assertThat(records)
            .describedAs("Session Replay should capture records")
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
            .describedAs("Should contain a meta record (type 4)")
            .isNotNull

        assertThat(focusRecord)
            .describedAs("Should contain a focus record (type 6)")
            .isNotNull

        assertThat(fullSnapshotRecord)
            .describedAs("Should contain a full snapshot record (type 10)")
            .isNotNull
    }

    protected fun runInstrumentationScenario() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        // we need this to avoid the Bitrise flakiness by requiring waiting for
        // SurfaceFlinger to call the onDraw method which will trigger the screen snapshot.
        Espresso.onView(ViewMatchers.isRoot()).perform(waitFor(UI_THREAD_DELAY_MS))
        instrumentation.waitForIdleSync()
    }
}
