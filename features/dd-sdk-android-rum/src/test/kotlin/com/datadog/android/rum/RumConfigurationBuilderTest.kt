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
import com.datadog.android.rum.configuration.SlowFramesConfiguration
import com.datadog.android.rum.configuration.VitalsUpdateFrequency
import com.datadog.android.rum.event.ViewEventMapper
import com.datadog.android.rum.internal.NoOpRumSessionListener
import com.datadog.android.rum.internal.RumFeature
import com.datadog.android.rum.internal.instrumentation.MainLooperLongTaskStrategy
import com.datadog.android.rum.internal.instrumentation.insights.InsightsCollector
import com.datadog.android.rum.internal.instrumentation.insights.NoOpInsightsCollector
import com.datadog.android.rum.internal.tracking.NoOpInteractionPredicate
import com.datadog.android.rum.metric.interactiontonextview.LastInteractionIdentifier
import com.datadog.android.rum.metric.interactiontonextview.TimeBasedInteractionIdentifier
import com.datadog.android.rum.metric.networksettled.InitialResourceIdentifier
import com.datadog.android.rum.metric.networksettled.TimeBasedInitialResourceIdentifier
import com.datadog.android.rum.model.ActionEvent
import com.datadog.android.rum.model.ErrorEvent
import com.datadog.android.rum.model.LongTaskEvent
import com.datadog.android.rum.model.ResourceEvent
import com.datadog.android.rum.model.RumVitalOperationStepEvent
import com.datadog.android.rum.model.ViewEvent
import com.datadog.android.rum.tracking.ActionTrackingStrategy
import com.datadog.android.rum.tracking.ActivityViewTrackingStrategy
import com.datadog.android.rum.tracking.InteractionPredicate
import com.datadog.android.rum.tracking.NoOpActionTrackingStrategy
import com.datadog.android.rum.tracking.ViewAttributesProvider
import com.datadog.android.rum.tracking.ViewTrackingStrategy
import com.datadog.android.rum.utils.forge.Configurator
import com.datadog.android.telemetry.model.TelemetryConfigurationEvent
import fr.xgouchet.elmyr.Forge
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

@Suppress("OPT_IN_USAGE")
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
    fun `M use sensible defaults W build()`() {
        // When
        val rumConfiguration = testedBuilder.build()

        // Then
        with(rumConfiguration.featureConfiguration) {
            assertThat(customEndpointUrl).isEqualTo(null)
            assertThat(sampleRate).isEqualTo(RumFeature.DEFAULT_SAMPLE_RATE)
            assertThat(telemetrySampleRate).isEqualTo(RumFeature.DEFAULT_TELEMETRY_SAMPLE_RATE)
            assertThat(telemetryConfigurationSampleRate)
                .isEqualTo(RumFeature.DEFAULT_TELEMETRY_CONFIGURATION_SAMPLE_RATE)
            assertThat(userActionTracking).isTrue()
            assertThat(touchTargetExtraAttributesProviders)
                .isEqualTo(emptyList<ViewAttributesProvider>())
            assertThat(interactionPredicate).isEqualTo(NoOpInteractionPredicate())
            assertThat(viewTrackingStrategy)
                .isEqualTo(ActivityViewTrackingStrategy(false))
            assertThat(viewEventMapper).isEqualTo(NoOpEventMapper<ViewEvent>())
            assertThat(errorEventMapper).isEqualTo(NoOpEventMapper<ErrorEvent>())
            assertThat(actionEventMapper).isEqualTo(NoOpEventMapper<ActionEvent>())
            assertThat(resourceEventMapper).isEqualTo(NoOpEventMapper<ResourceEvent>())
            assertThat(longTaskEventMapper).isEqualTo(NoOpEventMapper<LongTaskEvent>())
            assertThat(telemetryConfigurationMapper)
                .isEqualTo(NoOpEventMapper<TelemetryConfigurationEvent>())
            assertThat(longTaskTrackingStrategy)
                .isEqualTo(MainLooperLongTaskStrategy(100L))
            assertThat(backgroundEventTracking).isFalse()
            assertThat(trackFrustrations).isTrue()
            // on Android R+ this should be false, but since default value is static property
            // RumFeature.DEFAULT_RUM_CONFIG, it is evaluated at the static() block during class
            // loading, so we are not able to set Build API version at this point. We will test
            // it through a helper method in RumFeature.Companion
            assertThat(trackNonFatalAnrs).isTrue()
            assertThat(vitalsMonitorUpdateFrequency).isEqualTo(VitalsUpdateFrequency.AVERAGE)
            assertThat(sessionListener).isEqualTo(NoOpRumSessionListener())
            assertThat(additionalConfig).isEqualTo(emptyMap<String, Any>())
            assertThat(initialResourceIdentifier).isEqualTo(TimeBasedInitialResourceIdentifier())
            assertThat(lastInteractionIdentifier).isEqualTo(TimeBasedInteractionIdentifier())
            assertThat(composeActionTrackingStrategy)
                .isInstanceOf(NoOpActionTrackingStrategy::class.java)
            assertThat(slowFramesConfiguration).isNull()
        }
    }

    @Test
    fun `M use applicationId provided W build()`() {
        // When
        val rumConfiguration = testedBuilder.build()

        // Then
        assertThat(rumConfiguration.applicationId).isEqualTo(fakeApplicationId.toString())
    }

    @Test
    fun `M build config with custom endpoint W useCustomEndpoint() and build()`(
        @StringForgery(regex = "https://[a-z]+\\.com(/[a-z]+)+") rumUrl: String
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
    fun `M disable user action tracking W disableUserInteractionTracking()`() {
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
    fun `M bundle the custom attributes providers W trackInteractions()`(
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
    fun `M use the custom predicate W trackInteractions()`() {
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
    fun `M use the NoOpInteractionPredicate W trackInteractions() { predicate not provided }`() {
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
    fun `M build config with long tasks enabled W trackLongTasks() and build()`(
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
    fun `M build config with long tasks disabled W trackLongTasks() and build()`(
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
    fun `M build config with view strategy enabled W useViewTrackingStrategy() and build()`() {
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
    fun `M build config without view strategy W useViewTrackingStrategy(null) and build()`() {
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
    fun `M build config with sample rate W setSessionSampleRate() and build()`(
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
    fun `M build config with sample rate W telemetrySampleRate() and build()`(
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
    fun `M build config with background event W trackBackgroundEvents() and build()`(
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
    fun `M build config with track non-fatal ANRs W trackNonFatalAnrs() and build()`(
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
    fun `M build config with RUM View eventMapper W setViewEventMapper() and build()`() {
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
    fun `M build config with RUM Resource eventMapper W setResourceEventMapper() & build()`() {
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
    fun `M build config with RUM Action eventMapper W setActionEventMapper() and build()`() {
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
    fun `M build config with RUM Error eventMapper W setErrorEventMapper() and build()`() {
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
    fun `M build config with RUM LongTask eventMapper W setLongTaskEventMapper() & build()`() {
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

    @OptIn(ExperimentalRumApi::class)
    @Test
    fun `M build config with RUM Vital eventMapper W setVitalOperationStepEventMapper() & build()`() {
        // Given
        val eventMapper: EventMapper<RumVitalOperationStepEvent> = mock()

        // When
        val rumConfiguration = testedBuilder
            .setVitalOperationStepEventMapper(eventMapper)
            .build()

        // Then
        assertThat(rumConfiguration.featureConfiguration).isEqualTo(
            RumFeature.DEFAULT_RUM_CONFIG.copy(
                vitalOperationStepEventMapper = eventMapper
            )
        )
    }

    @Test
    fun `M use the given frequency W setVitalsMonitorUpdateFrequency`(
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
    fun `M use track frustration flag W trackFrustrations`(
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
    fun `M log warning W builder with missing application ID`(
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
    fun `M build config with RUM Telemetry eventMapper W setTelemetryConfigurationEventMapper()`() {
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
    fun `M apply configuration telemetry sample rate W applyAdditionalConfig(config) { with sample rate }`(
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
    fun `M set a session listener W setSessionListener()`() {
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

    @Test
    fun `M use a custom initialResourceIdentifier W setInitialResourceIdentifierStrategy()`() {
        // Given
        val customInitialResourceIdentifier = mock<InitialResourceIdentifier>()

        // When
        val rumConfiguration = testedBuilder
            .setInitialResourceIdentifier(customInitialResourceIdentifier)
            .build()

        // Then
        assertThat(rumConfiguration.featureConfiguration.initialResourceIdentifier)
            .isSameAs(customInitialResourceIdentifier)
    }

    @Test
    fun `M use a custom lastInteractionIdentifier W setLastInteractionIdentifier()`() {
        // Given
        val customLastInteractionIdentifier = mock<LastInteractionIdentifier>()

        // When
        val rumConfiguration = testedBuilder
            .setLastInteractionIdentifier(customLastInteractionIdentifier)
            .build()

        // Then
        assertThat(rumConfiguration.featureConfiguration.lastInteractionIdentifier)
            .isSameAs(customLastInteractionIdentifier)
    }

    @OptIn(ExperimentalRumApi::class)
    @Test
    fun `M changes default trackAnonymousUser W trackAnonymousUser()`(
        @BoolForgery trackAnonymousUser: Boolean
    ) {
        // When
        val rumConfiguration = testedBuilder
            .trackAnonymousUser(trackAnonymousUser)
            .build()

        // Then
        assertThat(rumConfiguration.featureConfiguration.trackAnonymousUser)
            .isEqualTo(trackAnonymousUser)
    }

    @OptIn(ExperimentalRumApi::class)
    @Test
    fun `M use a custom slowFramesConfiguration W setSlowFramesConfiguration()`() {
        // Given
        val slowFramesConfiguration = mock<SlowFramesConfiguration>()

        // When
        val rumConfiguration = testedBuilder
            .setSlowFramesConfiguration(slowFramesConfiguration)
            .build()

        // Then
        assertThat(rumConfiguration.featureConfiguration.slowFramesConfiguration)
            .isSameAs(slowFramesConfiguration)
    }

    @Test
    fun `M use a custom ActionTrackingStrategy W setComposeActionTrackingStrategy()`() {
        // Given
        val mockActionTrackingStrategy = mock<ActionTrackingStrategy>()

        // When
        val rumConfiguration = testedBuilder
            .setComposeActionTrackingStrategy(mockActionTrackingStrategy)
            .build()

        // Then
        assertThat(rumConfiguration.featureConfiguration.composeActionTrackingStrategy)
            .isSameAs(mockActionTrackingStrategy)
    }

    @Test
    fun `M set rumSessionTypeOverride W setRumSessionTypeOverride()`(
        forge: Forge
    ) {
        // Given
        val rumSessionTypeOverride = forge.aValueFrom(RumSessionType::class.java)

        // When
        _RumInternalProxy.setRumSessionTypeOverride(testedBuilder, rumSessionTypeOverride)
        val rumConfiguration = testedBuilder.build()

        // Then
        assertThat(rumConfiguration.featureConfiguration.rumSessionTypeOverride)
            .isEqualTo(rumSessionTypeOverride)
    }

    @Test
    fun `M enable accessibility settings collection W collectAccessibility`() {
        // When
        val rumConfiguration = testedBuilder
            .collectAccessibility(enabled = true)
            .build()

        // Then
        assertThat(rumConfiguration.featureConfiguration).isEqualTo(
            RumFeature.DEFAULT_RUM_CONFIG.copy(
                collectAccessibility = true
            )
        )
    }

    @Test
    fun `M set DefaultInsightsCollector W setInsightsCollector()`() {
        // Given
        val mockInsightsCollector: InsightsCollector = mock()

        // When
        val rumConfiguration = testedBuilder
            .setInsightsCollector(mockInsightsCollector)
            .build()

        // Then
        assertThat(rumConfiguration.featureConfiguration.insightsCollector)
            .isSameAs(mockInsightsCollector)
    }

    @Test
    fun `M use NoOpInsightsCollector W build() { default configuration }`() {
        // When
        val rumConfiguration = testedBuilder
            .build()

        // Then
        assertThat(rumConfiguration.featureConfiguration.insightsCollector)
            .isInstanceOf(NoOpInsightsCollector::class.java)
    }
}
