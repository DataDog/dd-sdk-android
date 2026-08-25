/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sdk.integration.rum

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.datadog.android.privacy.TrackingConsent
import com.datadog.android.rum.model.TimeseriesCpuEvent
import com.datadog.android.rum.model.TimeseriesMemoryEvent
import com.datadog.android.sdk.integration.RuntimeConfig
import com.datadog.android.sdk.integration.rum.TimeseriesTrackingPlaygroundActivity.Companion.SAMPLE_INTERVAL_MS
import com.datadog.android.sdk.rules.HandledRequest
import com.datadog.android.sdk.rules.RumMockServerActivityTestRule
import com.datadog.tools.unit.ConditionWatcher
import com.google.gson.JsonObject
import org.assertj.core.api.Assertions.assertThat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
internal class TimeseriesCollectionTest {

    @get:Rule
    val mockServerRule = RumMockServerActivityTestRule(
        TimeseriesTrackingPlaygroundActivity::class.java,
        keepRequests = true,
        trackingConsent = TrackingConsent.GRANTED
    )

    @Test
    fun verifyCpuAndMemoryTimeseriesAreCollected() {
        // Given
        Thread.sleep(FOREGROUND_COLLECTION_DURATION_MS)

        // When
        moveActivityToBackground()
        val events: List<JsonObject> = waitForTimeseriesEvents(expectedCount = TIMESERIES_PIPELINE_COUNT)

        // Then
        assertTrue(events.size >= TIMESERIES_PIPELINE_COUNT)
        events.assertForegroundDataPointCounts(EXPECTED_FOREGROUND_MEMORY_DATA_POINTS)
    }

    @Test
    fun verifyCollectionFlushesPausesAndResumesAcrossBackgroundTransition() {
        // Given
        Thread.sleep(FOREGROUND_COLLECTION_DURATION_MS)

        // When
        moveActivityToBackground()
        val firstForegroundEvents = waitForTimeseriesEvents(expectedCount = TIMESERIES_PIPELINE_COUNT)
        Thread.sleep(BACKGROUND_OBSERVATION_DURATION_MS)
        val backgroundEvents = timeseriesEvents()
        moveActivityToForeground()
        Thread.sleep(FOREGROUND_COLLECTION_DURATION_MS)
        moveActivityToBackground()
        val secondForegroundEvents = waitForTimeseriesEvents(expectedCount = TIMESERIES_PIPELINE_COUNT * 2)

        // Then
        assertEquals(TIMESERIES_PIPELINE_COUNT, firstForegroundEvents.size)
        firstForegroundEvents.assertForegroundDataPointCounts(EXPECTED_FOREGROUND_MEMORY_DATA_POINTS)
        assertThat(firstForegroundEvents.size).isEqualTo(backgroundEvents.size)
        assertThat(secondForegroundEvents.size).isEqualTo(TIMESERIES_PIPELINE_COUNT * 2)
        secondForegroundEvents.assertForegroundDataPointCounts(EXPECTED_FOREGROUND_MEMORY_DATA_POINTS)
    }

    private fun moveActivityToBackground() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            instrumentation.callActivityOnPause(mockServerRule.activity)
            instrumentation.callActivityOnStop(mockServerRule.activity)
        }
        instrumentation.waitForIdleSync()
    }

    private fun moveActivityToForeground() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            instrumentation.callActivityOnStart(mockServerRule.activity)
            instrumentation.callActivityOnResume(mockServerRule.activity)
        }
        instrumentation.waitForIdleSync()
    }

    private fun waitForTimeseriesEvents(expectedCount: Int): List<JsonObject> {
        var events = emptyList<JsonObject>()
        ConditionWatcher {
            events = timeseriesEvents()
            assertTrue(events.size >= expectedCount)
            true
        }.doWait(timeoutMs = RumTest.FINAL_WAIT_MS)
        return events
    }

    private fun timeseriesEvents(): List<JsonObject> = mockServerRule
        .getRequests(RuntimeConfig.rumEndpointUrl)
        .rumEvents()
        .filter { it.get(TYPE_KEY)?.asString == TIMESERIES_TYPE }

    private fun List<HandledRequest>.rumEvents(): List<JsonObject> = flatMap { request ->
        request.textBody?.let(::rumPayloadToJsonList).orEmpty()
    }

    companion object {
        private const val CPU_TIMESERIES_NAME = "cpu"
        private const val MEMORY_TIMESERIES_NAME = "memory"
        private const val TIMESERIES_TYPE = "timeseries"
        private const val TYPE_KEY = "type"
        private const val TIMESERIES_KEY = "timeseries"
        private const val NAME_KEY = "name"

        // One independent pipeline for each timeseries source: CPU and memory.
        private const val TIMESERIES_PIPELINE_COUNT = 2

        // Backgrounding defers the flush twice: ActivityViewTrackingStrategy waits before stopping
        // the view, then the collector waits before suspending sampling and flushing the buffers.
        private const val ACTIVITY_STOP_DELAY_MS = 200L
        private const val TIMESERIES_SUSPEND_DELAY_MS = 200L
        private const val FLUSH_DELAY_MS = ACTIVITY_STOP_DELAY_MS + TIMESERIES_SUSPEND_DELAY_MS

        // Sized so the flush lands in the middle of a sampling interval, leaving half an interval
        // of room on either side before the expected data point count changes.
        private const val FOREGROUND_COLLECTION_DURATION_MS =
            SAMPLE_INTERVAL_MS * 3 + SAMPLE_INTERVAL_MS / 2 - FLUSH_DELAY_MS
        private const val UPLOAD_GRACE_PERIOD_MS = 500L
        private const val BACKGROUND_OBSERVATION_DURATION_MS =
            SAMPLE_INTERVAL_MS + UPLOAD_GRACE_PERIOD_MS

        private const val EXPECTED_FOREGROUND_MEMORY_DATA_POINTS =
            ((FOREGROUND_COLLECTION_DURATION_MS + FLUSH_DELAY_MS) / SAMPLE_INTERVAL_MS).toInt()

        // Sampling shares a single executor with the other vitals on a device the test does not
        // own, so a stalled tick costs a data point and a late flush adds one. Keeps the lower
        // bound at two points, so the CPU event — which trails memory by one — always exists.
        private const val DATA_POINTS_TOLERANCE = 1

        private val JsonObject.timeseriesName: String?
            get() = getAsJsonObject(TIMESERIES_KEY).get(NAME_KEY).asString

        private fun List<JsonObject>.filterByName(name: String): List<JsonObject> = filter { it.timeseriesName == name }

        private fun List<JsonObject>.cpuEvents(): List<TimeseriesCpuEvent> =
            filterByName(CPU_TIMESERIES_NAME).map(TimeseriesCpuEvent::fromJsonObject)

        private fun List<JsonObject>.memoryEvents(): List<TimeseriesMemoryEvent> =
            filterByName(MEMORY_TIMESERIES_NAME).map(TimeseriesMemoryEvent::fromJsonObject)

        /**
         * Asserts the data point count of every foreground batch: memory within the tolerance of
         * the collection window, CPU exactly one point behind memory, because the first CPU read
         * only establishes the delta baseline and yields no data point.
         */
        private fun List<JsonObject>.assertForegroundDataPointCounts(
            expected: Int,
            tolerance: Int = DATA_POINTS_TOLERANCE
        ) {
            val memoryDataPointCounts = memoryEvents().map { it.timeseries.data.timestamps.size }
            val cpuDataPointCounts = cpuEvents().map { it.timeseries.data.timestamps.size }

            memoryDataPointCounts.forEach { count ->
                assertThat(count).isBetween(expected - tolerance, expected + tolerance)
            }
            assertThat(cpuDataPointCounts)
                .containsExactlyElementsOf(memoryDataPointCounts.map { it - 1 })
        }
    }
}
