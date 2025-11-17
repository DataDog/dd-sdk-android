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
import com.datadog.android.sdk.rules.SessionReplayTestRule
import com.datadog.android.sdk.utils.waitFor
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before

internal abstract class BaseSessionReplayTest<R : Activity> {

    abstract val rule: SessionReplayTestRule<R>

    @Before
    fun setUp() {
        runInstrumentationScenario(rule)
    }

    @After
    fun tearDown() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        try {
            val activity = rule.activity
            instrumentation.runOnMainSync {
                if (!activity.isFinishing && !activity.isDestroyed) {
                    activity.finish()
                }
            }
            instrumentation.waitForIdleSync()
        } catch (e: Exception) {
            // Activity may already be finished or not available
        }
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
            .describedAs("Should contain a meta record (type $RECORD_TYPE_META)")
            .isNotNull

        assertThat(focusRecord)
            .describedAs("Should contain a focus record (type $RECORD_TYPE_FOCUS)")
            .isNotNull

        assertThat(fullSnapshotRecord)
            .describedAs("Should contain a full snapshot record (type $RECORD_TYPE_FULL_SNAPSHOT)")
            .isNotNull
    }

    protected fun runInstrumentationScenario(rule: SessionReplayTestRule<R>) {
        val activity = rule.activity
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()

        instrumentation.runOnMainSync {
            instrumentation.callActivityOnStart(activity)
            instrumentation.callActivityOnResume(activity)
            val window = activity.window ?: return@runOnMainSync
            window.decorView.visibility = android.view.View.VISIBLE
            window.decorView.requestFocus()
        }
        instrumentation.waitForIdleSync()

        var focusAttempts = 0
        val maxFocusAttempts = 100
        while (focusAttempts < maxFocusAttempts) {
            var hasFocus = false
            var isVisible = false
            instrumentation.runOnMainSync {
                val window = activity.window ?: return@runOnMainSync
                isVisible = window.decorView.visibility == android.view.View.VISIBLE
                if (!isVisible) {
                    window.decorView.visibility = android.view.View.VISIBLE
                }
                hasFocus = window.decorView.hasWindowFocus()
                if (!hasFocus) {
                    window.decorView.requestFocus()
                }
            }
            instrumentation.waitForIdleSync()

            if (hasFocus && isVisible) {
                break
            }
            focusAttempts++
            Thread.sleep(50)
        }

        Thread.sleep(500)

        var espressoAttempts = 0
        val maxEspressoAttempts = 20
        while (espressoAttempts < maxEspressoAttempts) {
            try {
                Espresso.onView(ViewMatchers.isRoot()).perform(waitFor(UI_THREAD_DELAY_MS))
                break
            } catch (e: androidx.test.espresso.NoActivityResumedException) {
                handleEspressoException(
                    e,
                    activity,
                    instrumentation,
                    espressoAttempts,
                    maxEspressoAttempts,
                    resumeActivity = true
                )
                espressoAttempts++
            } catch (e: RuntimeException) {
                if (e.message?.contains("window focus") == true ||
                    e.javaClass.simpleName.contains("RootViewWithoutFocus")
                ) {
                    handleEspressoException(
                        e,
                        activity,
                        instrumentation,
                        espressoAttempts,
                        maxEspressoAttempts,
                        resumeActivity = false
                    )
                    espressoAttempts++
                } else {
                    throw e
                }
            }
        }
        instrumentation.waitForIdleSync()
    }

    private fun handleEspressoException(
        e: Throwable,
        activity: Activity,
        instrumentation: android.app.Instrumentation,
        attempt: Int,
        maxAttempts: Int,
        resumeActivity: Boolean
    ) {
        if (attempt == maxAttempts - 1) {
            throw e
        }
        instrumentation.runOnMainSync {
            if (resumeActivity) {
                instrumentation.callActivityOnResume(activity)
            }
            val window = activity.window ?: return@runOnMainSync
            window.decorView.visibility = android.view.View.VISIBLE
            window.decorView.requestFocus()
        }
        instrumentation.waitForIdleSync()
        Thread.sleep(300)
    }
}
