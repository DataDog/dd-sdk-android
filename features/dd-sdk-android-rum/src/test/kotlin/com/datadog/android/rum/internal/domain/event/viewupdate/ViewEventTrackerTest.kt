/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.domain.event.viewupdate

import com.datadog.android.event.NoOpEventMapper
import com.datadog.android.rum.RumConfiguration
import com.datadog.android.rum.configuration.VitalsUpdateFrequency
import com.datadog.android.rum.internal.RumFeature
import com.datadog.android.rum.internal.instrumentation.insights.NoOpInsightsCollector
import com.datadog.android.rum.internal.NoOpRumSessionListener
import com.datadog.android.rum.internal.tracking.NoOpInteractionPredicate
import com.datadog.android.rum.metric.networksettled.NoOpInitialResourceIdentifier
import com.datadog.android.rum.tracking.NoOpActionTrackingStrategy
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.never
import org.mockito.kotlin.reset
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
internal class ViewEventTrackerTest {

    private lateinit var testedTracker: ViewEventTracker

    private lateinit var fakeConfigEnabled: RumConfiguration
    private lateinit var fakeConfigDisabled: RumConfiguration

    @Mock
    private lateinit var mockWriter: EventWriter

    @BeforeEach
    fun setUp() {
        // Create config with feature enabled
        fakeConfigEnabled = createRumConfig(enablePartialViewUpdates = true)

        // Create config with feature disabled
        fakeConfigDisabled = createRumConfig(enablePartialViewUpdates = false)

        testedTracker = ViewEventTracker(
            config = fakeConfigEnabled,
            writer = mockWriter
        )
    }

    private fun createRumConfig(enablePartialViewUpdates: Boolean): RumConfiguration {
        return RumConfiguration(
            applicationId = "test-app-id",
            featureConfiguration = RumFeature.Configuration(
                customEndpointUrl = null,
                sampleRate = 100f,
                telemetrySampleRate = 20f,
                telemetryConfigurationSampleRate = 20f,
                userActionTracking = true,
                touchTargetExtraAttributesProviders = emptyList(),
                interactionPredicate = NoOpInteractionPredicate(),
                viewTrackingStrategy = null,
                longTaskTrackingStrategy = null,
                viewEventMapper = NoOpEventMapper(),
                errorEventMapper = NoOpEventMapper(),
                resourceEventMapper = NoOpEventMapper(),
                actionEventMapper = NoOpEventMapper(),
                longTaskEventMapper = NoOpEventMapper(),
                vitalOperationStepEventMapper = NoOpEventMapper(),
                vitalAppLaunchEventMapper = NoOpEventMapper(),
                telemetryConfigurationMapper = NoOpEventMapper(),
                backgroundEventTracking = false,
                trackFrustrations = true,
                trackNonFatalAnrs = true,
                vitalsMonitorUpdateFrequency = VitalsUpdateFrequency.AVERAGE,
                sessionListener = NoOpRumSessionListener(),
                initialResourceIdentifier = NoOpInitialResourceIdentifier(),
                lastInteractionIdentifier = null,
                slowFramesConfiguration = null,
                composeActionTrackingStrategy = NoOpActionTrackingStrategy(),
                additionalConfig = emptyMap(),
                trackAnonymousUser = true,
                rumSessionTypeOverride = null,
                collectAccessibility = false,
                disableJankStats = false,
                insightsCollector = NoOpInsightsCollector(),
                enablePartialViewUpdates = enablePartialViewUpdates
            )
        )
    }

    @Test
    fun `M return true W isFirstEvent() called for new view`() {
        // Given
        val viewId = "view-123"

        // When
        val result = testedTracker.isFirstEvent(viewId)

        // Then
        assertThat(result).isTrue()
    }

    @Test
    fun `M return false W hasLastSentEvent() called for new view`() {
        // Given
        val viewId = "view-123"

        // When
        val result = testedTracker.hasLastSentEvent(viewId)

        // Then
        assertThat(result).isFalse()
    }

    @Test
    fun `M return null W getDocumentVersion() called for new view`() {
        // Given
        val viewId = "view-123"

        // When
        val version = testedTracker.getDocumentVersion(viewId)

        // Then
        assertThat(version).isNull()
    }

    @Test
    fun `M clear stored state W onViewEnded() called`() {
        // Given
        val viewId = "view-123"

        // When
        testedTracker.onViewEnded(viewId)

        // Then
        assertThat(testedTracker.hasLastSentEvent(viewId)).isFalse()
        assertThat(testedTracker.getDocumentVersion(viewId)).isNull()
    }

    @Test
    fun `M clear all state W onSdkShutdown() called`() {
        // Given - tracker is initialized

        // When
        testedTracker.onSdkShutdown()

        // Then
        assertThat(testedTracker.hasLastSentEvent("any-view")).isFalse()
        assertThat(testedTracker.getDocumentVersion("any-view")).isNull()
    }

    // ========== Event Sending Logic Tests ==========

    @Test
    fun `M send full view event W sendViewUpdate() first time`() {
        // Given
        whenever(mockWriter.write(any())).thenReturn(true)
        val viewId = "view-123"
        val viewData = mapOf(
            "application" to mapOf("id" to "app-123"),
            "session" to mapOf("id" to "session-456"),
            "view" to mapOf("id" to viewId, "url" to "https://example.com"),
            "time_spent" to 100L
        )

        // When
        testedTracker.sendViewUpdate(viewId, viewData)

        // Then
        argumentCaptor<Map<String, Any?>>().apply {
            verify(mockWriter).write(capture())
            val event = firstValue
            assertThat(event["type"]).isEqualTo("view")
            assertThat((event["_dd"] as Map<*, *>)["document_version"]).isEqualTo(1)
            assertThat(event["time_spent"]).isEqualTo(100L)
        }
    }

    @Test
    fun `M send view_update event W sendViewUpdate() subsequent time`() {
        // Given
        whenever(mockWriter.write(any())).thenReturn(true)
        val viewId = "view-123"
        val initialData = mapOf(
            "application" to mapOf("id" to "app-123"),
            "session" to mapOf("id" to "session-456"),
            "view" to mapOf("id" to viewId, "url" to "https://example.com"),
            "time_spent" to 100L
        )
        val updatedData = initialData.toMutableMap().apply {
            put("time_spent", 200L)
        }

        testedTracker.sendViewUpdate(viewId, initialData)
        reset(mockWriter)
        whenever(mockWriter.write(any())).thenReturn(true)

        // When
        testedTracker.sendViewUpdate(viewId, updatedData)

        // Then
        argumentCaptor<Map<String, Any?>>().apply {
            verify(mockWriter).write(capture())
            val event = firstValue
            assertThat(event["type"]).isEqualTo("view_update")
            assertThat((event["_dd"] as Map<*, *>)["document_version"]).isEqualTo(2)
            assertThat(event["time_spent"]).isEqualTo(200L)
            // Other unchanged fields should not be present
            assertThat(event.containsKey("url")).isFalse()
        }
    }

    @Test
    fun `M skip sending W sendViewUpdate() no changes`() {
        // Given
        whenever(mockWriter.write(any())).thenReturn(true)
        val viewId = "view-123"
        val viewData = mapOf(
            "application" to mapOf("id" to "app-123"),
            "session" to mapOf("id" to "session-456"),
            "view" to mapOf("id" to viewId),
            "time_spent" to 100L
        )

        testedTracker.sendViewUpdate(viewId, viewData)
        reset(mockWriter)

        // When
        testedTracker.sendViewUpdate(viewId, viewData) // Same data

        // Then
        verify(mockWriter, never()).write(any())
    }

    @Test
    fun `M send full view W sendViewUpdate() feature disabled`() {
        // Given
        whenever(mockWriter.write(any())).thenReturn(true)
        val trackerDisabled = ViewEventTracker(
            config = fakeConfigDisabled,
            writer = mockWriter
        )
        val viewId = "view-123"
        val viewData1 = mapOf(
            "application" to mapOf("id" to "app-123"),
            "session" to mapOf("id" to "session-456"),
            "view" to mapOf("id" to viewId),
            "time_spent" to 100L
        )
        val viewData2 = mapOf(
            "application" to mapOf("id" to "app-123"),
            "session" to mapOf("id" to "session-456"),
            "view" to mapOf("id" to viewId),
            "time_spent" to 200L
        )

        // When
        trackerDisabled.sendViewUpdate(viewId, viewData1)
        trackerDisabled.sendViewUpdate(viewId, viewData2)

        // Then
        argumentCaptor<Map<String, Any?>>().apply {
            verify(mockWriter, times(2)).write(capture())
            val events = allValues
            assertThat(events[0]["type"]).isEqualTo("view")
            assertThat(events[1]["type"]).isEqualTo("view") // Still full view, not update
        }
    }

    // ========== Document Version Management Tests ==========

    @Test
    fun `M increment document version W sendViewUpdate() multiple times`() {
        // Given
        whenever(mockWriter.write(any())).thenReturn(true)
        val viewId = "view-123"

        // When
        testedTracker.sendViewUpdate(viewId, mapOf("time_spent" to 100L))
        testedTracker.sendViewUpdate(viewId, mapOf("time_spent" to 200L))
        testedTracker.sendViewUpdate(viewId, mapOf("time_spent" to 300L))

        // Then
        argumentCaptor<Map<String, Any?>>().apply {
            verify(mockWriter, times(3)).write(capture())
            val events = allValues
            assertThat((events[0]["_dd"] as Map<*, *>)["document_version"]).isEqualTo(1)
            assertThat((events[1]["_dd"] as Map<*, *>)["document_version"]).isEqualTo(2)
            assertThat((events[2]["_dd"] as Map<*, *>)["document_version"]).isEqualTo(3)
        }
    }

    @Test
    fun `M use separate counters W sendViewUpdate() different views`() {
        // Given
        whenever(mockWriter.write(any())).thenReturn(true)
        val viewId1 = "view-1"
        val viewId2 = "view-2"

        // When
        testedTracker.sendViewUpdate(viewId1, mapOf("time_spent" to 100L))
        testedTracker.sendViewUpdate(viewId2, mapOf("time_spent" to 50L))
        testedTracker.sendViewUpdate(viewId1, mapOf("time_spent" to 200L))
        testedTracker.sendViewUpdate(viewId2, mapOf("time_spent" to 100L))

        // Then
        argumentCaptor<Map<String, Any?>>().apply {
            verify(mockWriter, times(4)).write(capture())
            val events = allValues

            // View 1 events
            assertThat((events[0]["_dd"] as Map<*, *>)["document_version"]).isEqualTo(1)
            assertThat((events[2]["_dd"] as Map<*, *>)["document_version"]).isEqualTo(2)

            // View 2 events
            assertThat((events[1]["_dd"] as Map<*, *>)["document_version"]).isEqualTo(1)
            assertThat((events[3]["_dd"] as Map<*, *>)["document_version"]).isEqualTo(2)
        }
    }

    // ========== Required Fields Tests ==========

    @Test
    fun `M include required fields W sendViewUpdate() sends view_update`() {
        // Given
        whenever(mockWriter.write(any())).thenReturn(true)
        val viewId = "view-123"
        val initialData = mapOf(
            "application" to mapOf("id" to "app-123"),
            "session" to mapOf("id" to "session-456"),
            "view" to mapOf("id" to viewId, "url" to "https://example.com"),
            "time_spent" to 100L
        )
        val updatedData = initialData.toMutableMap().apply {
            put("time_spent", 200L)
        }

        testedTracker.sendViewUpdate(viewId, initialData)
        reset(mockWriter)
        whenever(mockWriter.write(any())).thenReturn(true)

        // When
        testedTracker.sendViewUpdate(viewId, updatedData)

        // Then
        argumentCaptor<Map<String, Any?>>().apply {
            verify(mockWriter).write(capture())
            val event = firstValue

            // Required fields must be present
            assertThat(event["application"]).isNotNull()
            assertThat((event["application"] as Map<*, *>)["id"]).isEqualTo("app-123")
            assertThat(event["session"]).isNotNull()
            assertThat((event["session"] as Map<*, *>)["id"]).isEqualTo("session-456")
            assertThat(event["view"]).isNotNull()
            assertThat((event["view"] as Map<*, *>)["id"]).isEqualTo(viewId)
            assertThat(event["_dd"]).isNotNull()
            assertThat((event["_dd"] as Map<*, *>)["document_version"]).isNotNull()
        }
    }

    // ========== Memory Management Tests ==========

    @Test
    fun `M clear state W onViewEnded() called`() {
        // Given
        whenever(mockWriter.write(any())).thenReturn(true)
        val viewId = "view-123"
        testedTracker.sendViewUpdate(viewId, mapOf("time_spent" to 100L))

        // When
        testedTracker.onViewEnded(viewId)

        // Then
        assertThat(testedTracker.isFirstEvent(viewId)).isTrue()
        assertThat(testedTracker.getDocumentVersion(viewId)).isNull()
    }

    @Test
    fun `M clear all state W onSdkShutdown() called after sending events`() {
        // Given
        whenever(mockWriter.write(any())).thenReturn(true)
        testedTracker.sendViewUpdate("view-1", mapOf("time_spent" to 100L))
        testedTracker.sendViewUpdate("view-2", mapOf("time_spent" to 50L))

        // When
        testedTracker.onSdkShutdown()

        // Then
        assertThat(testedTracker.isFirstEvent("view-1")).isTrue()
        assertThat(testedTracker.isFirstEvent("view-2")).isTrue()
        assertThat(testedTracker.getDocumentVersion("view-1")).isNull()
        assertThat(testedTracker.getDocumentVersion("view-2")).isNull()
    }

    @Test
    fun `M restart from version 1 W onViewEnded() then sendViewUpdate() again`() {
        // Given
        whenever(mockWriter.write(any())).thenReturn(true)
        val viewId = "view-123"
        testedTracker.sendViewUpdate(viewId, mapOf("time_spent" to 100L))
        testedTracker.onViewEnded(viewId)
        reset(mockWriter)
        whenever(mockWriter.write(any())).thenReturn(true)

        // When - Next update for same viewId should be first event again
        testedTracker.sendViewUpdate(viewId, mapOf("time_spent" to 200L))

        // Then
        argumentCaptor<Map<String, Any?>>().apply {
            verify(mockWriter).write(capture())
            val event = firstValue
            assertThat(event["type"]).isEqualTo("view")
            assertThat((event["_dd"] as Map<*, *>)["document_version"]).isEqualTo(1)
        }
    }
}
