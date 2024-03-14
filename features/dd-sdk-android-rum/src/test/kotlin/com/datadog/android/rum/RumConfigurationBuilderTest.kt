/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum

import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.event.EventMapper
import com.datadog.android.event.NoOpEventMapper
import com.datadog.android.rum.assertj.ConfigurationRumAssert
import com.datadog.android.rum.configuration.VitalsUpdateFrequency
import com.datadog.android.rum.event.ViewEventMapper
import com.datadog.android.rum.internal.NoOpRumSessionListener
import com.datadog.android.rum.internal.RumFeature
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
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.mock
import org.mockito.quality.Strictness
import java.util.UUID

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class RumConfigurationBuilderTest {

    private lateinit var testedBuilder: RumConfiguration.Builder

    @Mock
    lateinit var mockSdkCore: FeatureSdkCore

    @Forgery
    lateinit var fakeApplicationId: UUID

    @BeforeEach
    fun `set up`() {
        testedBuilder = RumConfiguration.Builder(fakeApplicationId.toString())
    }

    @Test
    fun `𝕄 use sensible defaults 𝕎 build()`() {
        // When
        val rumConfiguration = testedBuilder.build()

        // Then
        assertThat(rumConfiguration.featureConfiguration).isEqualTo(
            RumFeature.Configuration(
                customEndpointUrl = null,
                sampleRate = RumFeature.DEFAULT_SAMPLE_RATE,
                telemetrySampleRate = RumFeature.DEFAULT_TELEMETRY_SAMPLE_RATE,
                telemetryConfigurationSampleRate = RumFeature.DEFAULT_TELEMETRY_CONFIGURATION_SAMPLE_RATE,
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
                // on Android R+ this should be false, but since default value is static property
                // RumFeature.DEFAULT_RUM_CONFIG, it is evaluated at the static() block during class
                // loading, so we are not able to set Build API version at this point. We will test
                // it through a helper method in RumFeature.Companion
                trackNonFatalAnrs = true,
                vitalsMonitorUpdateFrequency = VitalsUpdateFrequency.AVERAGE,
                sessionListener = NoOpRumSessionListener(),
                additionalConfig = emptyMap()
            )
        )
    }

    @Test
    fun `𝕄 use applicationId provided 𝕎 build()`() {
        // When
        val rumConfiguration = testedBuilder.build()

        // Then
        assertThat(rumConfiguration.applicationId).isEqualTo(fakeApplicationId.toString())
    }

    @Test
    fun `𝕄 build config with custom endpoint 𝕎 useCustomEndpoint() and build()`(
        @StringForgery(regex = "https://[a-z]+\\.com") rumUrl: String
    ) {
        // When
        val rumConfiguration = testedBuilder
            .useCustomEndpoint(rumUrl)
            .build()

        // Then
        assertThat(rumConfiguration.featureConfiguration).isEqualTo(
            RumFeature.DEFAULT_RUM_CONFIG.copy(customEndpointUrl = rumUrl)
        )
    }

    @Test
    fun `𝕄 disable user action tracking W disableUserInteractionTracking()`() {
        // Given

        // When
        val rumConfiguration = testedBuilder
            .disableUserInteractionTracking()
            .build()

        // Then
        ConfigurationRumAssert.assertThat(rumConfiguration.featureConfiguration)
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
        val rumConfiguration = testedBuilder
            .trackUserInteractions(mockProviders)
            .build()

        // Then
        ConfigurationRumAssert.assertThat(rumConfiguration.featureConfiguration)
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
        val rumConfiguration = testedBuilder
            .trackUserInteractions(interactionPredicate = mockInteractionPredicate)
            .build()

        // Then
        ConfigurationRumAssert.assertThat(rumConfiguration.featureConfiguration)
            .hasUserActionTrackingEnabled()
            .hasInteractionPredicate(mockInteractionPredicate)
            .hasViewTrackingStrategy(RumFeature.DEFAULT_RUM_CONFIG.viewTrackingStrategy!!)
            .hasLongTaskTrackingEnabled(RumFeature.DEFAULT_LONG_TASK_THRESHOLD_MS)
    }

    @Test
    fun `𝕄 use the NoOpInteractionPredicate 𝕎 trackInteractions() { predicate not provided }`() {
        // When
        val rumConfiguration = testedBuilder
            .trackUserInteractions()
            .build()

        // Then
        ConfigurationRumAssert.assertThat(rumConfiguration.featureConfiguration)
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
        val rumConfiguration = testedBuilder
            .trackLongTasks(durationMs)
            .build()

        // Then
        assertThat(rumConfiguration.featureConfiguration).isEqualTo(
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
        val rumConfiguration = testedBuilder
            .trackLongTasks(-durationMs)
            .build()

        // Then
        assertThat(rumConfiguration.featureConfiguration).isEqualTo(
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
        val rumConfiguration = testedBuilder
            .useViewTrackingStrategy(strategy)
            .build()

        // Then
        assertThat(rumConfiguration.featureConfiguration).isEqualTo(
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
        val rumConfiguration = testedBuilder
            .useViewTrackingStrategy(null)
            .build()

        // Then
        assertThat(rumConfiguration.featureConfiguration).isEqualTo(
            RumFeature.DEFAULT_RUM_CONFIG.copy(
                viewTrackingStrategy = null
            )
        )
    }

    @Test
    fun `𝕄 build config with sample rate 𝕎 setSessionSampleRate() and build()`(
        @FloatForgery(min = 0f, max = 100f) sampling: Float
    ) {
        // When
        val rumConfiguration = testedBuilder
            .setSessionSampleRate(sampling)
            .build()

        // Then
        assertThat(rumConfiguration.featureConfiguration).isEqualTo(
            RumFeature.DEFAULT_RUM_CONFIG.copy(
                sampleRate = sampling
            )
        )
    }

    @Test
    fun `𝕄 build config with sample rate 𝕎 telemetrySampleRate() and build()`(
        @FloatForgery(min = 0f, max = 100f) sampling: Float
    ) {
        // When
        val rumConfiguration = testedBuilder
            .setTelemetrySampleRate(sampling)
            .build()

        // Then
        assertThat(rumConfiguration.featureConfiguration).isEqualTo(
            RumFeature.DEFAULT_RUM_CONFIG.copy(
                telemetrySampleRate = sampling
            )
        )
    }

    @Test
    fun `𝕄 build config with background event 𝕎 trackBackgroundEvents() and build()`(
        @BoolForgery backgroundEventEnabled: Boolean
    ) {
        // When
        val rumConfiguration = testedBuilder
            .trackBackgroundEvents(backgroundEventEnabled)
            .build()

        // Then
        assertThat(rumConfiguration.featureConfiguration).isEqualTo(
            RumFeature.DEFAULT_RUM_CONFIG.copy(
                backgroundEventTracking = backgroundEventEnabled
            )
        )
    }

    @Test
    fun `𝕄 build config with track non-fatal ANRs 𝕎 trackNonFatalAnrs() and build()`(
        @BoolForgery trackNonFatalAnrsEnabled: Boolean
    ) {
        // When
        val rumConfiguration = testedBuilder
            .trackNonFatalAnrs(trackNonFatalAnrsEnabled)
            .build()

        // Then
        assertThat(rumConfiguration.featureConfiguration).isEqualTo(
            RumFeature.DEFAULT_RUM_CONFIG.copy(
                trackNonFatalAnrs = trackNonFatalAnrsEnabled
            )
        )
    }

    @Test
    fun `𝕄 build config with RUM View eventMapper 𝕎 setViewEventMapper() and build()`() {
        // Given
        val eventMapper: ViewEventMapper = mock()

        // When
        val rumConfiguration = testedBuilder
            .setViewEventMapper(eventMapper)
            .build()

        // Then
        assertThat(rumConfiguration.featureConfiguration).isEqualTo(
            RumFeature.DEFAULT_RUM_CONFIG.copy(
                viewEventMapper = eventMapper
            )
        )
    }

    @Test
    fun `𝕄 build config with RUM Resource eventMapper 𝕎 setResourceEventMapper() & build()`() {
        // Given
        val eventMapper: EventMapper<ResourceEvent> = mock()

        // When
        val rumConfiguration = testedBuilder
            .setResourceEventMapper(eventMapper)
            .build()

        // Then
        assertThat(rumConfiguration.featureConfiguration).isEqualTo(
            RumFeature.DEFAULT_RUM_CONFIG.copy(
                resourceEventMapper = eventMapper
            )
        )
    }

    @Test
    fun `𝕄 build config with RUM Action eventMapper 𝕎 setActionEventMapper() and build()`() {
        // Given
        val eventMapper: EventMapper<ActionEvent> = mock()

        // When
        val rumConfiguration = testedBuilder
            .setActionEventMapper(eventMapper)
            .build()

        // Then
        assertThat(rumConfiguration.featureConfiguration).isEqualTo(
            RumFeature.DEFAULT_RUM_CONFIG.copy(
                actionEventMapper = eventMapper
            )
        )
    }

    @Test
    fun `𝕄 build config with RUM Error eventMapper 𝕎 setErrorEventMapper() and build()`() {
        // Given
        val eventMapper: EventMapper<ErrorEvent> = mock()

        // When
        val rumConfiguration = testedBuilder
            .setErrorEventMapper(eventMapper)
            .build()

        // Then
        assertThat(rumConfiguration.featureConfiguration).isEqualTo(
            RumFeature.DEFAULT_RUM_CONFIG.copy(
                errorEventMapper = eventMapper
            )
        )
    }

    @Test
    fun `𝕄 build config with RUM LongTask eventMapper 𝕎 setLongTaskEventMapper() & build()`() {
        // Given
        val eventMapper: EventMapper<LongTaskEvent> = mock()

        // When
        val rumConfiguration = testedBuilder
            .setLongTaskEventMapper(eventMapper)
            .build()

        // Then
        assertThat(rumConfiguration.featureConfiguration).isEqualTo(
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
        val rumConfiguration = testedBuilder
            .setVitalsUpdateFrequency(fakeFrequency)
            .build()

        // Then
        assertThat(rumConfiguration.featureConfiguration).isEqualTo(
            RumFeature.DEFAULT_RUM_CONFIG.copy(vitalsMonitorUpdateFrequency = fakeFrequency)
        )
    }

    @Test
    fun `𝕄 use track frustration flag 𝕎 trackFrustrations`(
        @BoolForgery fakeTrackFrustrations: Boolean
    ) {
        // When
        val rumConfiguration = testedBuilder
            .trackFrustrations(fakeTrackFrustrations)
            .build()

        // Then
        assertThat(rumConfiguration.featureConfiguration).isEqualTo(
            RumFeature.DEFAULT_RUM_CONFIG.copy(trackFrustrations = fakeTrackFrustrations)
        )
    }

    @Test
    fun `𝕄 log warning 𝕎 builder with missing application ID`(
        @BoolForgery fakeTrackFrustrations: Boolean
    ) {
        // When
        val rumConfiguration = testedBuilder
            .trackFrustrations(fakeTrackFrustrations)
            .build()

        // Then
        assertThat(rumConfiguration.featureConfiguration).isEqualTo(
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
        val rumConfiguration = builder.build()

        // Then
        assertThat(rumConfiguration.featureConfiguration).isEqualTo(
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
        val rumConfiguration = testedBuilder
            .setAdditionalConfiguration(
                mapOf(RumFeature.DD_TELEMETRY_CONFIG_SAMPLE_RATE_TAG to sampleRate)
            )
            .build()

        // Then
        assertThat(rumConfiguration.featureConfiguration.telemetryConfigurationSampleRate)
            .isEqualTo(sampleRate)
    }

    @Test
    fun `𝕄 set a session listener W setSessionListener()`() {
        // Given
        val mockSessionListener = mock<RumSessionListener>()

        // When
        val rumConfiguration = testedBuilder
            .setSessionListener(mockSessionListener)
            .build()

        // Then
        assertThat(rumConfiguration.featureConfiguration.sessionListener)
            .isSameAs(mockSessionListener)
    }
}
