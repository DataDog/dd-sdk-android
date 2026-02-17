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
import org.mockito.junit.jupiter.MockitoExtension

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
internal class ViewEventTrackerTest {

    private lateinit var testedTracker: ViewEventTracker

    private lateinit var fakeConfig: RumConfiguration

    @BeforeEach
    fun setUp() {
        fakeConfig = RumConfiguration(
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
                enablePartialViewUpdates = false
            )
        )

        testedTracker = ViewEventTracker(fakeConfig)
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
}
