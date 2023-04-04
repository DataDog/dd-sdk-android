/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum

import com.datadog.android.event.EventMapper
import com.datadog.android.event.NoOpEventMapper
import com.datadog.android.rum.assertj.ConfigurationRumAssert
import com.datadog.android.rum.configuration.VitalsUpdateFrequency
import com.datadog.android.rum.event.ViewEventMapper
import com.datadog.android.rum.internal.instrumentation.MainLooperLongTaskStrategy
import com.datadog.android.rum.model.ActionEvent
import com.datadog.android.rum.model.ErrorEvent
import com.datadog.android.rum.model.LongTaskEvent
import com.datadog.android.rum.model.ResourceEvent
import com.datadog.android.rum.tracking.ActivityViewTrackingStrategy
import com.datadog.android.rum.tracking.InteractionPredicate
import com.datadog.android.rum.tracking.NoOpInteractionPredicate
import com.datadog.android.rum.tracking.ViewAttributesProvider
import com.datadog.android.rum.tracking.ViewTrackingStrategy
import com.datadog.android.rum.utils.forge.Configurator
import com.datadog.android.telemetry.model.TelemetryConfigurationEvent
import com.nhaarman.mockitokotlin2.mock
import fr.xgouchet.elmyr.annotation.BoolForgery
import fr.xgouchet.elmyr.annotation.FloatForgery
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.annotation.LongForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness
import java.util.UUID

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class RumFeatureBuilderTest {

    private lateinit var testedBuilder: RumFeature.Builder

    @Forgery
    lateinit var fakeApplicationId: UUID

    @BeforeEach
    fun `set up`() {
        testedBuilder = RumFeature.Builder(fakeApplicationId.toString())
    }

    @Test
    fun `𝕄 use sensible defaults 𝕎 build()`() {
        // When
        val rumFeature = testedBuilder.build()

        // Then
        assertThat(rumFeature.configuration).isEqualTo(
            RumFeature.Configuration(
                customEndpointUrl = null,
                samplingRate = RumFeature.DEFAULT_SAMPLING_RATE,
                telemetrySamplingRate = RumFeature.DEFAULT_TELEMETRY_SAMPLING_RATE,
                telemetryConfigurationSamplingRate = RumFeature.DEFAULT_TELEMETRY_CONFIGURATION_SAMPLING_RATE,
                userActionTracking = true,
                touchTargetExtraAttributesProviders = emptyList(),
                interactionPredicate = NoOpInteractionPredicate(),
                viewTrackingStrategy = ActivityViewTrackingStrategy(false),
                viewEventMapper = NoOpEventMapper(),
                errorEventMapper = NoOpEventMapper(),
                actionEventMapper = NoOpEventMapper(),
                resourceEventMapper = NoOpEventMapper(),
                longTaskEventMapper = NoOpEventMapper(),
                telemetryConfigurationMapper = NoOpEventMapper(),
                longTaskTrackingStrategy = MainLooperLongTaskStrategy(100L),
                backgroundEventTracking = false,
                trackFrustrations = true,
                vitalsMonitorUpdateFrequency = VitalsUpdateFrequency.AVERAGE,
                additionalConfig = emptyMap()
            )
        )
    }

    @Test
    fun `𝕄 use applicationId provided 𝕎 build()`() {
        // When
        val rumFeature = testedBuilder.build()

        // Then
        assertThat(rumFeature.applicationId).isEqualTo(fakeApplicationId.toString())
    }

    @Test
    fun `𝕄 build config with custom endpoint 𝕎 useCustomEndpoint() and build()`(
        @StringForgery(regex = "https://[a-z]+\\.com") rumUrl: String
    ) {
        // When
        val rumFeature = testedBuilder
            .useCustomEndpoint(rumUrl)
            .build()

        // Then
        assertThat(rumFeature.configuration).isEqualTo(
            RumFeature.DEFAULT_RUM_CONFIG.copy(customEndpointUrl = rumUrl)
        )
    }

    @Test
    fun `𝕄 disable user action tracking W disableInteractionTracking()`() {
        // Given

        // When
        val rumFeature = testedBuilder
            .disableInteractionTracking()
            .build()

        // Then
        ConfigurationRumAssert.assertThat(rumFeature.configuration)
            .hasUserActionTrackingDisabled()
            .hasViewTrackingStrategy(RumFeature.DEFAULT_RUM_CONFIG.viewTrackingStrategy!!)
            .hasLongTaskTrackingEnabled(RumFeature.DEFAULT_LONG_TASK_THRESHOLD_MS)
    }

    @Test
    fun `𝕄 bundle the custom attributes providers W trackInteractions()`(
        @IntForgery(0, 10) attributesCount: Int
    ) {
        // Given
        val mockProviders = Array<ViewAttributesProvider>(attributesCount) {
            mock()
        }

        // When
        val rumFeature = testedBuilder
            .trackInteractions(mockProviders)
            .build()

        // Then
        ConfigurationRumAssert.assertThat(rumFeature.configuration)
            .hasUserActionTrackingEnabled()
            .hasActionTargetAttributeProviders(mockProviders)
            .hasViewTrackingStrategy(RumFeature.DEFAULT_RUM_CONFIG.viewTrackingStrategy!!)
            .hasLongTaskTrackingEnabled(RumFeature.DEFAULT_LONG_TASK_THRESHOLD_MS)
    }

    @Test
    fun `𝕄 use the custom predicate 𝕎 trackInteractions()`() {
        // Given
        val mockInteractionPredicate: InteractionPredicate = mock()

        // When
        val rumFeature = testedBuilder
            .trackInteractions(interactionPredicate = mockInteractionPredicate)
            .build()

        // Then
        ConfigurationRumAssert.assertThat(rumFeature.configuration)
            .hasUserActionTrackingEnabled()
            .hasInteractionPredicate(mockInteractionPredicate)
            .hasViewTrackingStrategy(RumFeature.DEFAULT_RUM_CONFIG.viewTrackingStrategy!!)
            .hasLongTaskTrackingEnabled(RumFeature.DEFAULT_LONG_TASK_THRESHOLD_MS)
    }

    @Test
    fun `𝕄 use the NoOpInteractionPredicate 𝕎 trackInteractions() { predicate not provided }`() {
        // When
        val rumFeature = testedBuilder
            .trackInteractions()
            .build()

        // Then
        ConfigurationRumAssert.assertThat(rumFeature.configuration)
            .hasUserActionTrackingEnabled()
            .hasInteractionPredicateOfType(NoOpInteractionPredicate::class.java)
            .hasViewTrackingStrategy(RumFeature.DEFAULT_RUM_CONFIG.viewTrackingStrategy!!)
            .hasLongTaskTrackingEnabled(RumFeature.DEFAULT_LONG_TASK_THRESHOLD_MS)
    }

    @Test
    fun `𝕄 build config with long tasks enabled 𝕎 trackLongTasks() and build()`(
        @LongForgery(1L, 65536L) durationMs: Long
    ) {
        // Given

        // When
        val rumFeature = testedBuilder
            .trackLongTasks(durationMs)
            .build()

        // Then
        assertThat(rumFeature.configuration).isEqualTo(
            RumFeature.DEFAULT_RUM_CONFIG.copy(
                longTaskTrackingStrategy = MainLooperLongTaskStrategy(durationMs)
            )
        )
    }

    @Test
    fun `𝕄 build config with long tasks disabled 𝕎 trackLongTasks() and build()`(
        @LongForgery(0L, 65536L) durationMs: Long
    ) {
        // Given

        // When
        val rumFeature = testedBuilder
            .trackLongTasks(-durationMs)
            .build()

        // Then
        assertThat(rumFeature.configuration).isEqualTo(
            RumFeature.DEFAULT_RUM_CONFIG.copy(
                longTaskTrackingStrategy = null
            )
        )
    }

    @Test
    fun `𝕄 build config with view strategy enabled 𝕎 useViewTrackingStrategy() and build()`() {
        // Given
        val strategy: ViewTrackingStrategy = mock()

        // When
        val rumFeature = testedBuilder
            .useViewTrackingStrategy(strategy)
            .build()

        // Then
        assertThat(rumFeature.configuration).isEqualTo(
            RumFeature.DEFAULT_RUM_CONFIG.copy(
                userActionTracking = true,
                touchTargetExtraAttributesProviders = emptyList(),
                interactionPredicate = NoOpInteractionPredicate(),
                viewTrackingStrategy = strategy
            )
        )
    }

    @Test
    fun `𝕄 build config without view strategy 𝕎 useViewTrackingStrategy(null) and build()`() {
        // When
        val rumFeature = testedBuilder
            .useViewTrackingStrategy(null)
            .build()

        // Then
        assertThat(rumFeature.configuration).isEqualTo(
            RumFeature.DEFAULT_RUM_CONFIG.copy(
                viewTrackingStrategy = null
            )
        )
    }

    @Test
    fun `𝕄 build config with sampling rate 𝕎 sampleRumSessions() and build()`(
        @FloatForgery(min = 0f, max = 100f) sampling: Float
    ) {
        // When
        val rumFeature = testedBuilder
            .sampleRumSessions(sampling)
            .build()

        // Then
        assertThat(rumFeature.configuration).isEqualTo(
            RumFeature.DEFAULT_RUM_CONFIG.copy(
                samplingRate = sampling
            )
        )
    }

    @Test
    fun `𝕄 build config with sampling rate 𝕎 sampleTelemetry() and build()`(
        @FloatForgery(min = 0f, max = 100f) sampling: Float
    ) {
        // When
        val rumFeature = testedBuilder
            .sampleTelemetry(sampling)
            .build()

        // Then
        assertThat(rumFeature.configuration).isEqualTo(
            RumFeature.DEFAULT_RUM_CONFIG.copy(
                telemetrySamplingRate = sampling
            )
        )
    }

    @Test
    fun `𝕄 build config with background event 𝕎 trackBackgroundEvents() and build()`(
        @BoolForgery backgroundEventEnabled: Boolean
    ) {
        // When
        val rumFeature = testedBuilder
            .trackBackgroundRumEvents(backgroundEventEnabled)
            .build()

        // Then
        assertThat(rumFeature.configuration).isEqualTo(
            RumFeature.DEFAULT_RUM_CONFIG.copy(
                backgroundEventTracking = backgroundEventEnabled
            )
        )
    }

    @Test
    fun `𝕄 build config with RUM View eventMapper 𝕎 setRumViewEventMapper() and build()`() {
        // Given
        val eventMapper: ViewEventMapper = mock()

        // When
        val rumFeature = testedBuilder
            .setRumViewEventMapper(eventMapper)
            .build()

        // Then
        assertThat(rumFeature.configuration).isEqualTo(
            RumFeature.DEFAULT_RUM_CONFIG.copy(
                viewEventMapper = eventMapper
            )
        )
    }

    @Test
    fun `𝕄 build config with RUM Resource eventMapper 𝕎 setRumResourceEventMapper() & build()`() {
        // Given
        val eventMapper: EventMapper<ResourceEvent> = mock()

        // When
        val rumFeature = testedBuilder
            .setRumResourceEventMapper(eventMapper)
            .build()

        // Then
        assertThat(rumFeature.configuration).isEqualTo(
            RumFeature.DEFAULT_RUM_CONFIG.copy(
                resourceEventMapper = eventMapper
            )
        )
    }

    @Test
    fun `𝕄 build config with RUM Action eventMapper 𝕎 setRumActionEventMapper() and build()`() {
        // Given
        val eventMapper: EventMapper<ActionEvent> = mock()

        // When
        val rumFeature = testedBuilder
            .setRumActionEventMapper(eventMapper)
            .build()

        // Then
        assertThat(rumFeature.configuration).isEqualTo(
            RumFeature.DEFAULT_RUM_CONFIG.copy(
                actionEventMapper = eventMapper
            )
        )
    }

    @Test
    fun `𝕄 build config with RUM Error eventMapper 𝕎 setRumErrorEventMapper() and build()`() {
        // Given
        val eventMapper: EventMapper<ErrorEvent> = mock()

        // When
        val rumFeature = testedBuilder
            .setRumErrorEventMapper(eventMapper)
            .build()

        // Then
        assertThat(rumFeature.configuration).isEqualTo(
            RumFeature.DEFAULT_RUM_CONFIG.copy(
                errorEventMapper = eventMapper
            )
        )
    }

    @Test
    fun `𝕄 build config with RUM LongTask eventMapper 𝕎 setRumLongTaskEventMapper() & build()`() {
        // Given
        val eventMapper: EventMapper<LongTaskEvent> = mock()

        // When
        val rumFeature = testedBuilder
            .setRumLongTaskEventMapper(eventMapper)
            .build()

        // Then
//        val expectedRumEventMapper = RumEventMapper(
//            sdkCore = RumFeature.DEFAULT_NOOP_SDK_CORE,
//            longTaskEventMapper = eventMapper
//        )
        assertThat(rumFeature.configuration).isEqualTo(
            RumFeature.DEFAULT_RUM_CONFIG.copy(
                longTaskEventMapper = eventMapper
            )
        )
    }

    @Test
    fun `𝕄 use the given frequency 𝕎 setVitalsMonitorUpdateFrequency`(
        @Forgery fakeFrequency: VitalsUpdateFrequency
    ) {
        // When
        val rumFeature = testedBuilder
            .setVitalsUpdateFrequency(fakeFrequency)
            .build()

        // Then
        assertThat(rumFeature.configuration).isEqualTo(
            RumFeature.DEFAULT_RUM_CONFIG.copy(vitalsMonitorUpdateFrequency = fakeFrequency)
        )
    }

    @Test
    fun `𝕄 use track frustration flag 𝕎 trackFrustrations`(
        @BoolForgery fakeTrackFrustrations: Boolean
    ) {
        // When
        val rumFeature = testedBuilder
            .trackFrustrations(fakeTrackFrustrations)
            .build()

        // Then
        assertThat(rumFeature.configuration).isEqualTo(
            RumFeature.DEFAULT_RUM_CONFIG.copy(trackFrustrations = fakeTrackFrustrations)
        )
    }

    @Test
    fun `𝕄 log warning 𝕎 builder with missing application ID`(
        @BoolForgery fakeTrackFrustrations: Boolean
    ) {
        // When
        val rumFeature = testedBuilder
            .trackFrustrations(fakeTrackFrustrations)
            .build()

        // Then
        assertThat(rumFeature.configuration).isEqualTo(
            RumFeature.DEFAULT_RUM_CONFIG.copy(trackFrustrations = fakeTrackFrustrations)
        )
    }

    @Test
    fun `𝕄 build config with RUM Telemetry eventMapper 𝕎 setTelemetryConfigurationEventMapper()`() {
        // Given
        val eventMapper: EventMapper<TelemetryConfigurationEvent> = mock()

        // When
        val builder = testedBuilder
        _RumInternalProxy.setTelemetryConfigurationEventMapper(builder, eventMapper)
        val rumFeature = builder.build()

        // Then
        assertThat(rumFeature.configuration).isEqualTo(
            RumFeature.DEFAULT_RUM_CONFIG.copy(
                telemetryConfigurationMapper = eventMapper
            )
        )
    }

    @Test
    fun `𝕄 apply configuration telemetry sample rate W applyAdditionalConfig(config) { with sample rate }`(
        @FloatForgery(0.0f, 100.0f) sampleRate: Float
    ) {
        // When
        val rumFeature = testedBuilder
            .setAdditionalConfiguration(
                mapOf(RumFeature.DD_TELEMETRY_CONFIG_SAMPLE_RATE_TAG to sampleRate)
            )
            .build()

        // Then
        assertThat(rumFeature.configuration.telemetryConfigurationSamplingRate)
            .isEqualTo(sampleRate)
    }
}
