/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

@file:Suppress("DEPRECATION")

package com.datadog.android.core.configuration

import android.os.Build
import com.datadog.android.DatadogSite
import com.datadog.android._InternalProxy
import com.datadog.android.core.internal.event.NoOpEventMapper
import com.datadog.android.event.EventMapper
import com.datadog.android.event.NoOpSpanEventMapper
import com.datadog.android.event.SpanEventMapper
import com.datadog.android.event.ViewEventMapper
import com.datadog.android.log.model.LogEvent
import com.datadog.android.plugin.DatadogPlugin
import com.datadog.android.plugin.Feature
import com.datadog.android.rum.assertj.ConfigurationRumAssert.Companion.assertThat
import com.datadog.android.rum.internal.domain.event.RumEventMapper
import com.datadog.android.rum.internal.instrumentation.MainLooperLongTaskStrategy
import com.datadog.android.rum.internal.instrumentation.UserActionTrackingStrategyLegacy
import com.datadog.android.rum.internal.instrumentation.gestures.DatadogGesturesTracker
import com.datadog.android.rum.internal.tracking.JetpackViewAttributesProvider
import com.datadog.android.rum.model.ActionEvent
import com.datadog.android.rum.model.ErrorEvent
import com.datadog.android.rum.model.LongTaskEvent
import com.datadog.android.rum.model.ResourceEvent
import com.datadog.android.rum.tracking.ActivityViewTrackingStrategy
import com.datadog.android.rum.tracking.InteractionPredicate
import com.datadog.android.rum.tracking.NoOpInteractionPredicate
import com.datadog.android.rum.tracking.ViewAttributesProvider
import com.datadog.android.rum.tracking.ViewTrackingStrategy
import com.datadog.android.security.Encryption
import com.datadog.android.telemetry.model.TelemetryConfigurationEvent
import com.datadog.android.tracing.TracingHeaderType
import com.datadog.android.utils.config.InternalLoggerTestConfiguration
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.v2.api.InternalLogger
import com.datadog.tools.unit.annotations.TestConfigurationsProvider
import com.datadog.tools.unit.annotations.TestTargetApi
import com.datadog.tools.unit.extensions.ApiLevelExtension
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import com.datadog.tools.unit.extensions.config.TestConfiguration
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.BoolForgery
import fr.xgouchet.elmyr.annotation.FloatForgery
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.annotation.LongForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import okhttp3.Authenticator
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import java.net.Proxy
import java.net.URL
import java.util.Locale

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class),
    ExtendWith(ApiLevelExtension::class),
    ExtendWith(TestConfigurationExtension::class)
)
@MockitoSettings
@ForgeConfiguration(value = Configurator::class)
internal class ConfigurationBuilderTest {

    lateinit var testedBuilder: Configuration.Builder

    @BeforeEach
    fun `set up`() {
        testedBuilder = Configuration.Builder(
            logsEnabled = true,
            tracesEnabled = true,
            crashReportsEnabled = true,
            rumEnabled = true
        )
    }

    @Test
    fun `𝕄 use sensible defaults 𝕎 build()`() {
        // When
        val config = testedBuilder.build()

        // Then
        assertThat(config.coreConfig).isEqualTo(
            Configuration.Core(
                needsClearTextHttp = false,
                enableDeveloperModeWhenDebuggable = false,
                firstPartyHostsWithHeaderTypes = emptyMap(),
                batchSize = BatchSize.MEDIUM,
                uploadFrequency = UploadFrequency.AVERAGE,
                proxy = null,
                proxyAuth = Authenticator.NONE,
                encryption = null,
                webViewTrackingHosts = emptyList(),
                site = DatadogSite.US1
            )
        )
        assertThat(config.logsConfig).isEqualTo(
            Configuration.Feature.Logs(
                endpointUrl = DatadogSite.US1.intakeEndpoint,
                plugins = emptyList(),
                logsEventMapper = NoOpEventMapper()
            )
        )
        assertThat(config.tracesConfig)
            .usingRecursiveComparison()
            .ignoringFields("spanEventMapper")
            .isEqualTo(
                Configuration.Feature.Tracing(
                    endpointUrl = DatadogSite.US1.intakeEndpoint,
                    plugins = emptyList(),
                    spanEventMapper = NoOpSpanEventMapper()
                )
            )
        assertThat(config.tracesConfig?.spanEventMapper)
            .isInstanceOf(NoOpSpanEventMapper::class.java)
        assertThat(config.crashReportConfig).isEqualTo(
            Configuration.Feature.CrashReport(
                endpointUrl = DatadogSite.US1.intakeEndpoint,
                plugins = emptyList()
            )
        )
        assertThat(config.rumConfig).isEqualTo(
            Configuration.Feature.RUM(
                endpointUrl = DatadogSite.US1.intakeEndpoint,
                plugins = emptyList(),
                samplingRate = Configuration.DEFAULT_SAMPLING_RATE,
                telemetrySamplingRate = Configuration.DEFAULT_TELEMETRY_SAMPLING_RATE,
                telemetryConfigurationSamplingRate = Configuration.DEFAULT_TELEMETRY_CONFIGURATION_SAMPLING_RATE,
                userActionTrackingStrategy = UserActionTrackingStrategyLegacy(
                    DatadogGesturesTracker(
                        arrayOf(JetpackViewAttributesProvider()),
                        NoOpInteractionPredicate()
                    )
                ),
                viewTrackingStrategy = ActivityViewTrackingStrategy(false),
                rumEventMapper = NoOpEventMapper(),
                longTaskTrackingStrategy = MainLooperLongTaskStrategy(100L),
                backgroundEventTracking = false,
                trackFrustrations = true,
                vitalsMonitorUpdateFrequency = VitalsUpdateFrequency.AVERAGE
            )
        )
        assertThat(config.additionalConfig).isEmpty()
    }

    @Test
    fun `𝕄 build config without logsConfig 𝕎 build() { logs disabled }`() {
        // Given
        testedBuilder = Configuration.Builder(
            logsEnabled = false,
            tracesEnabled = true,
            crashReportsEnabled = true,
            rumEnabled = true
        )

        // When
        val config = testedBuilder.build()

        // Then
        assertThat(config.logsConfig).isNull()
        assertThat(config.tracesConfig).isNotNull
        assertThat(config.crashReportConfig).isNotNull
        assertThat(config.rumConfig).isNotNull
        assertThat(config.additionalConfig).isEmpty()
    }

    @Test
    fun `𝕄 build config without tracesConfig 𝕎 build() { traces disabled }`() {
        // Given
        testedBuilder = Configuration.Builder(
            logsEnabled = true,
            tracesEnabled = false,
            crashReportsEnabled = true,
            rumEnabled = true
        )

        // When
        val config = testedBuilder.build()

        // Then
        assertThat(config.logsConfig).isNotNull
        assertThat(config.tracesConfig).isNull()
        assertThat(config.crashReportConfig).isNotNull
        assertThat(config.rumConfig).isNotNull
        assertThat(config.additionalConfig).isEmpty()
    }

    @Test
    fun `𝕄 build config without crashReportConfig 𝕎 build() { crashReports disabled }`() {
        // Given
        testedBuilder = Configuration.Builder(
            logsEnabled = true,
            tracesEnabled = true,
            crashReportsEnabled = false,
            rumEnabled = true
        )

        // When
        val config = testedBuilder.build()

        // Then
        assertThat(config.logsConfig).isNotNull
        assertThat(config.tracesConfig).isNotNull
        assertThat(config.crashReportConfig).isNull()
        assertThat(config.rumConfig).isNotNull
        assertThat(config.additionalConfig).isEmpty()
    }

    @Test
    fun `𝕄 build config without rumConfig 𝕎 build() { RUM disabled }`() {
        // Given
        testedBuilder = Configuration.Builder(
            logsEnabled = true,
            tracesEnabled = true,
            crashReportsEnabled = true,
            rumEnabled = false
        )

        // When
        val config = testedBuilder.build()

        // Then
        assertThat(config.logsConfig).isNotNull
        assertThat(config.tracesConfig).isNotNull
        assertThat(config.crashReportConfig).isNotNull
        assertThat(config.rumConfig).isNull()
        assertThat(config.additionalConfig).isEmpty()
    }

    @Test
    fun `𝕄 build config with custom site 𝕎 useSite() and build()`(
        @Forgery site: DatadogSite
    ) {
        // When
        val config = testedBuilder.useSite(site).build()

        // Then
        assertThat(config.coreConfig).isEqualTo(
            Configuration.DEFAULT_CORE_CONFIG.copy(site = site)
        )
        assertThat(config.logsConfig).isEqualTo(
            Configuration.DEFAULT_LOGS_CONFIG.copy(endpointUrl = site.intakeEndpoint)
        )
        assertThat(config.tracesConfig).isEqualTo(
            Configuration.DEFAULT_TRACING_CONFIG.copy(endpointUrl = site.intakeEndpoint)
        )
        assertThat(config.crashReportConfig).isEqualTo(
            Configuration.DEFAULT_CRASH_CONFIG.copy(endpointUrl = site.intakeEndpoint)
        )
        assertThat(config.rumConfig).isEqualTo(
            Configuration.DEFAULT_RUM_CONFIG.copy(endpointUrl = site.intakeEndpoint)
        )
        assertThat(config.additionalConfig).isEmpty()
    }

    @Test
    fun `𝕄 build config with custom endpoints 𝕎 useCustomXXXEndpoint() and build()`(
        @StringForgery(regex = "https://[a-z]+\\.com") logsUrl: String,
        @StringForgery(regex = "https://[a-z]+\\.com") tracesUrl: String,
        @StringForgery(regex = "https://[a-z]+\\.com") crashReportsUrl: String,
        @StringForgery(regex = "https://[a-z]+\\.com") rumUrl: String
    ) {
        // When
        val config = testedBuilder
            .useCustomLogsEndpoint(logsUrl)
            .useCustomTracesEndpoint(tracesUrl)
            .useCustomCrashReportsEndpoint(crashReportsUrl)
            .useCustomRumEndpoint(rumUrl)
            .build()

        // Then
        assertThat(config.coreConfig).isEqualTo(
            Configuration.DEFAULT_CORE_CONFIG.copy(
                needsClearTextHttp = false
            )
        )
        assertThat(config.logsConfig).isEqualTo(
            Configuration.DEFAULT_LOGS_CONFIG.copy(endpointUrl = logsUrl)
        )
        assertThat(config.tracesConfig).isEqualTo(
            Configuration.DEFAULT_TRACING_CONFIG.copy(endpointUrl = tracesUrl)
        )
        assertThat(config.crashReportConfig).isEqualTo(
            Configuration.DEFAULT_CRASH_CONFIG.copy(endpointUrl = crashReportsUrl)
        )
        assertThat(config.rumConfig).isEqualTo(
            Configuration.DEFAULT_RUM_CONFIG.copy(endpointUrl = rumUrl)
        )
        assertThat(config.additionalConfig).isEmpty()
    }

    @Test
    fun `𝕄 build config with custom cleartext endpoints 𝕎 useCustomXXXEndpoint() and build()`(
        @StringForgery(regex = "http://[a-z]+\\.com") logsUrl: String,
        @StringForgery(regex = "http://[a-z]+\\.com") tracesUrl: String,
        @StringForgery(regex = "http://[a-z]+\\.com") crashReportsUrl: String,
        @StringForgery(regex = "http://[a-z]+\\.com") rumUrl: String
    ) {
        // When
        val config = testedBuilder
            .useCustomLogsEndpoint(logsUrl)
            .useCustomTracesEndpoint(tracesUrl)
            .useCustomCrashReportsEndpoint(crashReportsUrl)
            .useCustomRumEndpoint(rumUrl)
            .build()

        // Then
        assertThat(config.coreConfig).isEqualTo(
            Configuration.DEFAULT_CORE_CONFIG.copy(
                needsClearTextHttp = true
            )
        )
        assertThat(config.logsConfig).isEqualTo(
            Configuration.DEFAULT_LOGS_CONFIG.copy(endpointUrl = logsUrl)
        )
        assertThat(config.tracesConfig).isEqualTo(
            Configuration.DEFAULT_TRACING_CONFIG.copy(endpointUrl = tracesUrl)
        )
        assertThat(config.crashReportConfig).isEqualTo(
            Configuration.DEFAULT_CRASH_CONFIG.copy(endpointUrl = crashReportsUrl)
        )
        assertThat(config.rumConfig).isEqualTo(
            Configuration.DEFAULT_RUM_CONFIG.copy(endpointUrl = rumUrl)
        )
        assertThat(config.additionalConfig).isEmpty()
    }

    @Test
    fun `M set the NoOpUserActionTrackingStrategy W disableInteractionTracking()`() {
        // Given

        // When
        val config = testedBuilder
            .disableInteractionTracking()
            .build()

        // Then
        assertThat(config.coreConfig).isEqualTo(Configuration.DEFAULT_CORE_CONFIG)
        assertThat(config.logsConfig).isEqualTo(Configuration.DEFAULT_LOGS_CONFIG)
        assertThat(config.tracesConfig).isEqualTo(Configuration.DEFAULT_TRACING_CONFIG)
        assertThat(config.crashReportConfig).isEqualTo(Configuration.DEFAULT_CRASH_CONFIG)
        assertThat(config.rumConfig!!)
            .hasNoOpUserActionTrackingStrategy()
            .hasViewTrackingStrategy(Configuration.DEFAULT_RUM_CONFIG.viewTrackingStrategy!!)
            .hasLongTaskTrackingEnabled(Configuration.DEFAULT_LONG_TASK_THRESHOLD_MS)
        assertThat(config.additionalConfig).isEmpty()
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
        val config = testedBuilder
            .trackInteractions(mockProviders)
            .build()

        // Then

        assertThat(config.coreConfig).isEqualTo(Configuration.DEFAULT_CORE_CONFIG)
        assertThat(config.logsConfig).isEqualTo(Configuration.DEFAULT_LOGS_CONFIG)
        assertThat(config.tracesConfig).isEqualTo(Configuration.DEFAULT_TRACING_CONFIG)
        assertThat(config.crashReportConfig).isEqualTo(Configuration.DEFAULT_CRASH_CONFIG)
        assertThat(config.rumConfig!!)
            .hasUserActionTrackingStrategyLegacy()
            .hasActionTargetAttributeProviders(mockProviders)
            .hasViewTrackingStrategy(Configuration.DEFAULT_RUM_CONFIG.viewTrackingStrategy!!)
            .hasLongTaskTrackingEnabled(Configuration.DEFAULT_LONG_TASK_THRESHOLD_MS)
        assertThat(config.additionalConfig).isEmpty()
    }

    @Test
    fun `𝕄 bundle only the default providers W trackInteractions { providers not provided }`() {
        // When
        val config = testedBuilder
            .trackInteractions()
            .build()

        // Then

        assertThat(config.coreConfig).isEqualTo(Configuration.DEFAULT_CORE_CONFIG)
        assertThat(config.logsConfig).isEqualTo(Configuration.DEFAULT_LOGS_CONFIG)
        assertThat(config.tracesConfig).isEqualTo(Configuration.DEFAULT_TRACING_CONFIG)
        assertThat(config.crashReportConfig).isEqualTo(Configuration.DEFAULT_CRASH_CONFIG)
        assertThat(config.rumConfig!!)
            .hasUserActionTrackingStrategyLegacy()
            .hasDefaultActionTargetAttributeProviders()
            .hasViewTrackingStrategy(Configuration.DEFAULT_RUM_CONFIG.viewTrackingStrategy!!)
            .hasLongTaskTrackingEnabled(Configuration.DEFAULT_LONG_TASK_THRESHOLD_MS)
        assertThat(config.additionalConfig).isEmpty()
    }

    @Test
    fun `𝕄 use the custom predicate 𝕎 trackInteractions()`() {
        // Given
        val mockInteractionPredicate: InteractionPredicate = mock()

        // When
        val config = testedBuilder
            .trackInteractions(interactionPredicate = mockInteractionPredicate)
            .build()

        // Then

        assertThat(config.coreConfig).isEqualTo(Configuration.DEFAULT_CORE_CONFIG)
        assertThat(config.logsConfig).isEqualTo(Configuration.DEFAULT_LOGS_CONFIG)
        assertThat(config.tracesConfig).isEqualTo(Configuration.DEFAULT_TRACING_CONFIG)
        assertThat(config.crashReportConfig).isEqualTo(Configuration.DEFAULT_CRASH_CONFIG)
        assertThat(config.rumConfig!!)
            .hasUserActionTrackingStrategyLegacy()
            .hasInteractionPredicate(mockInteractionPredicate)
            .hasViewTrackingStrategy(Configuration.DEFAULT_RUM_CONFIG.viewTrackingStrategy!!)
            .hasLongTaskTrackingEnabled(Configuration.DEFAULT_LONG_TASK_THRESHOLD_MS)
        assertThat(config.additionalConfig).isEmpty()
    }

    @Test
    fun `𝕄 use the NoOpInteractionPredicate 𝕎 trackInteractions() { predicate not provided }`() {
        // When
        val config = testedBuilder
            .trackInteractions()
            .build()

        // Then

        assertThat(config.coreConfig).isEqualTo(Configuration.DEFAULT_CORE_CONFIG)
        assertThat(config.logsConfig).isEqualTo(Configuration.DEFAULT_LOGS_CONFIG)
        assertThat(config.tracesConfig).isEqualTo(Configuration.DEFAULT_TRACING_CONFIG)
        assertThat(config.crashReportConfig).isEqualTo(Configuration.DEFAULT_CRASH_CONFIG)
        assertThat(config.rumConfig!!)
            .hasUserActionTrackingStrategyLegacy()
            .hasInteractionPredicateOfType(NoOpInteractionPredicate::class.java)
            .hasViewTrackingStrategy(Configuration.DEFAULT_RUM_CONFIG.viewTrackingStrategy!!)
            .hasLongTaskTrackingEnabled(Configuration.DEFAULT_LONG_TASK_THRESHOLD_MS)
        assertThat(config.additionalConfig).isEmpty()
    }

    @TestTargetApi(Build.VERSION_CODES.Q)
    @Test
    fun `𝕄 build config with gestures enabled 𝕎 trackInteractions() and build() {Android Q}`(
        @IntForgery(0, 10) attributesCount: Int
    ) {
        // Given
        val mockProviders = Array<ViewAttributesProvider>(attributesCount) {
            mock()
        }

        // When
        val config = testedBuilder
            .trackInteractions(mockProviders)
            .build()

        // Then

        assertThat(config.coreConfig).isEqualTo(Configuration.DEFAULT_CORE_CONFIG)
        assertThat(config.logsConfig).isEqualTo(Configuration.DEFAULT_LOGS_CONFIG)
        assertThat(config.tracesConfig).isEqualTo(Configuration.DEFAULT_TRACING_CONFIG)
        assertThat(config.crashReportConfig).isEqualTo(Configuration.DEFAULT_CRASH_CONFIG)
        assertThat(config.rumConfig!!)
            .hasUserActionTrackingStrategyApi29()
            .hasActionTargetAttributeProviders(mockProviders)
            .hasViewTrackingStrategy(Configuration.DEFAULT_RUM_CONFIG.viewTrackingStrategy!!)
            .hasLongTaskTrackingEnabled(Configuration.DEFAULT_LONG_TASK_THRESHOLD_MS)
        assertThat(config.additionalConfig).isEmpty()
    }

    @Test
    fun `𝕄 build config with long tasks enabled 𝕎 trackLongTasks() and build()`(
        @LongForgery(1L, 65536L) durationMs: Long
    ) {
        // Given

        // When
        val config = testedBuilder
            .trackLongTasks(durationMs)
            .build()

        // Then
        assertThat(config.coreConfig).isEqualTo(Configuration.DEFAULT_CORE_CONFIG)
        assertThat(config.logsConfig).isEqualTo(Configuration.DEFAULT_LOGS_CONFIG)
        assertThat(config.tracesConfig).isEqualTo(Configuration.DEFAULT_TRACING_CONFIG)
        assertThat(config.crashReportConfig).isEqualTo(Configuration.DEFAULT_CRASH_CONFIG)
        assertThat(config.rumConfig).isEqualTo(
            Configuration.DEFAULT_RUM_CONFIG.copy(
                longTaskTrackingStrategy = MainLooperLongTaskStrategy(durationMs)
            )
        )
        assertThat(config.additionalConfig).isEmpty()
    }

    @Test
    fun `𝕄 build config with long tasks disabled 𝕎 trackLongTasks() and build()`(
        @LongForgery(0L, 65536L) durationMs: Long
    ) {
        // Given

        // When
        val config = testedBuilder
            .trackLongTasks(-durationMs)
            .build()

        // Then
        assertThat(config.coreConfig).isEqualTo(Configuration.DEFAULT_CORE_CONFIG)
        assertThat(config.logsConfig).isEqualTo(Configuration.DEFAULT_LOGS_CONFIG)
        assertThat(config.tracesConfig).isEqualTo(Configuration.DEFAULT_TRACING_CONFIG)
        assertThat(config.crashReportConfig).isEqualTo(Configuration.DEFAULT_CRASH_CONFIG)
        assertThat(config.rumConfig).isEqualTo(
            Configuration.DEFAULT_RUM_CONFIG.copy(
                longTaskTrackingStrategy = null
            )
        )
        assertThat(config.additionalConfig).isEmpty()
    }

    @Test
    fun `𝕄 build config with view strategy enabled 𝕎 useViewTrackingStrategy() and build()`() {
        // Given
        val strategy: ViewTrackingStrategy = mock()

        // When
        val config = testedBuilder
            .useViewTrackingStrategy(strategy)
            .build()

        // Then
        assertThat(config.coreConfig).isEqualTo(Configuration.DEFAULT_CORE_CONFIG)
        assertThat(config.logsConfig).isEqualTo(Configuration.DEFAULT_LOGS_CONFIG)
        assertThat(config.tracesConfig).isEqualTo(Configuration.DEFAULT_TRACING_CONFIG)
        assertThat(config.crashReportConfig).isEqualTo(Configuration.DEFAULT_CRASH_CONFIG)
        assertThat(config.rumConfig).isEqualTo(
            Configuration.DEFAULT_RUM_CONFIG.copy(
                userActionTrackingStrategy = UserActionTrackingStrategyLegacy(
                    DatadogGesturesTracker(
                        arrayOf(JetpackViewAttributesProvider()),
                        NoOpInteractionPredicate()
                    )
                ),
                viewTrackingStrategy = strategy
            )
        )
        assertThat(config.additionalConfig).isEmpty()
    }

    @Test
    fun `𝕄 build config without view strategy 𝕎 useViewTrackingStrategy(null) and build()`() {
        // When
        val config = testedBuilder
            .useViewTrackingStrategy(null)
            .build()

        // Then
        assertThat(config.coreConfig).isEqualTo(Configuration.DEFAULT_CORE_CONFIG)
        assertThat(config.logsConfig).isEqualTo(Configuration.DEFAULT_LOGS_CONFIG)
        assertThat(config.tracesConfig).isEqualTo(Configuration.DEFAULT_TRACING_CONFIG)
        assertThat(config.crashReportConfig).isEqualTo(Configuration.DEFAULT_CRASH_CONFIG)
        assertThat(config.rumConfig).isEqualTo(
            Configuration.DEFAULT_RUM_CONFIG.copy(
                viewTrackingStrategy = null
            )
        )
        assertThat(config.additionalConfig).isEmpty()
    }

    @Test
    fun `𝕄 build config with sampling rate 𝕎 sampleRumSessions() and build()`(
        @FloatForgery(min = 0f, max = 100f) sampling: Float
    ) {
        // When
        val config = testedBuilder
            .sampleRumSessions(sampling)
            .build()

        // Then
        assertThat(config.coreConfig).isEqualTo(Configuration.DEFAULT_CORE_CONFIG)
        assertThat(config.logsConfig).isEqualTo(Configuration.DEFAULT_LOGS_CONFIG)
        assertThat(config.tracesConfig).isEqualTo(Configuration.DEFAULT_TRACING_CONFIG)
        assertThat(config.crashReportConfig).isEqualTo(Configuration.DEFAULT_CRASH_CONFIG)
        assertThat(config.rumConfig).isEqualTo(
            Configuration.DEFAULT_RUM_CONFIG.copy(
                samplingRate = sampling
            )
        )
        assertThat(config.additionalConfig).isEmpty()
    }

    @Test
    fun `𝕄 build config with sampling rate 𝕎 sampleTelemetry() and build()`(
        @FloatForgery(min = 0f, max = 100f) sampling: Float
    ) {
        // When
        val config = testedBuilder
            .sampleTelemetry(sampling)
            .build()

        // Then
        assertThat(config.coreConfig).isEqualTo(Configuration.DEFAULT_CORE_CONFIG)
        assertThat(config.logsConfig).isEqualTo(Configuration.DEFAULT_LOGS_CONFIG)
        assertThat(config.tracesConfig).isEqualTo(Configuration.DEFAULT_TRACING_CONFIG)
        assertThat(config.crashReportConfig).isEqualTo(Configuration.DEFAULT_CRASH_CONFIG)
        assertThat(config.rumConfig).isEqualTo(
            Configuration.DEFAULT_RUM_CONFIG.copy(
                telemetrySamplingRate = sampling
            )
        )

        assertThat(config.additionalConfig).isEmpty()
    }

    @Test
    fun `𝕄 build config with background event 𝕎 trackBackgroundEvents() and build()`(
        @BoolForgery backgroundEventEnabled: Boolean
    ) {
        // When
        val config = testedBuilder
            .trackBackgroundRumEvents(backgroundEventEnabled)
            .build()

        // Then
        assertThat(config.coreConfig).isEqualTo(Configuration.DEFAULT_CORE_CONFIG)
        assertThat(config.logsConfig).isEqualTo(Configuration.DEFAULT_LOGS_CONFIG)
        assertThat(config.tracesConfig).isEqualTo(Configuration.DEFAULT_TRACING_CONFIG)
        assertThat(config.crashReportConfig).isEqualTo(Configuration.DEFAULT_CRASH_CONFIG)
        assertThat(config.rumConfig).isEqualTo(
            Configuration.DEFAULT_RUM_CONFIG.copy(
                backgroundEventTracking = backgroundEventEnabled
            )
        )
        assertThat(config.additionalConfig).isEmpty()
    }

    @Test
    fun `𝕄 build config with RUM View eventMapper 𝕎 setRumViewEventMapper() and build()`() {
        // Given
        val eventMapper: ViewEventMapper = mock()

        // When
        val config = testedBuilder
            .setRumViewEventMapper(eventMapper)
            .build()

        // Then
        assertThat(config.coreConfig).isEqualTo(Configuration.DEFAULT_CORE_CONFIG)
        assertThat(config.logsConfig).isEqualTo(Configuration.DEFAULT_LOGS_CONFIG)
        assertThat(config.tracesConfig).isEqualTo(Configuration.DEFAULT_TRACING_CONFIG)
        assertThat(config.crashReportConfig).isEqualTo(Configuration.DEFAULT_CRASH_CONFIG)
        val expectedRumEventMapper = RumEventMapper(viewEventMapper = eventMapper)
        assertThat(config.rumConfig).isEqualTo(
            Configuration.DEFAULT_RUM_CONFIG.copy(
                rumEventMapper = expectedRumEventMapper
            )
        )
        assertThat(config.additionalConfig).isEmpty()
    }

    @Test
    fun `𝕄 build config with RUM Resource eventMapper 𝕎 setRumResourceEventMapper() & build()`() {
        // Given
        val eventMapper: EventMapper<ResourceEvent> = mock()

        // When
        val config = testedBuilder
            .setRumResourceEventMapper(eventMapper)
            .build()

        // Then
        assertThat(config.coreConfig).isEqualTo(Configuration.DEFAULT_CORE_CONFIG)
        assertThat(config.logsConfig).isEqualTo(Configuration.DEFAULT_LOGS_CONFIG)
        assertThat(config.tracesConfig).isEqualTo(Configuration.DEFAULT_TRACING_CONFIG)
        assertThat(config.crashReportConfig).isEqualTo(Configuration.DEFAULT_CRASH_CONFIG)
        val expectedRumEventMapper = RumEventMapper(resourceEventMapper = eventMapper)
        assertThat(config.rumConfig).isEqualTo(
            Configuration.DEFAULT_RUM_CONFIG.copy(
                rumEventMapper = expectedRumEventMapper
            )
        )
        assertThat(config.additionalConfig).isEmpty()
    }

    @Test
    fun `𝕄 build config with RUM Action eventMapper 𝕎 setRumActionEventMapper() and build()`() {
        // Given
        val eventMapper: EventMapper<ActionEvent> = mock()

        // When
        val config = testedBuilder
            .setRumActionEventMapper(eventMapper)
            .build()

        // Then
        assertThat(config.coreConfig).isEqualTo(Configuration.DEFAULT_CORE_CONFIG)
        assertThat(config.logsConfig).isEqualTo(Configuration.DEFAULT_LOGS_CONFIG)
        assertThat(config.tracesConfig).isEqualTo(Configuration.DEFAULT_TRACING_CONFIG)
        assertThat(config.crashReportConfig).isEqualTo(Configuration.DEFAULT_CRASH_CONFIG)
        val expectedRumEventMapper = RumEventMapper(actionEventMapper = eventMapper)
        assertThat(config.rumConfig).isEqualTo(
            Configuration.DEFAULT_RUM_CONFIG.copy(
                rumEventMapper = expectedRumEventMapper
            )
        )
        assertThat(config.additionalConfig).isEmpty()
    }

    @Test
    fun `𝕄 build config with RUM Error eventMapper 𝕎 setRumErrorEventMapper() and build()`() {
        // Given
        val eventMapper: EventMapper<ErrorEvent> = mock()

        // When
        val config = testedBuilder
            .setRumErrorEventMapper(eventMapper)
            .build()

        // Then
        assertThat(config.coreConfig).isEqualTo(Configuration.DEFAULT_CORE_CONFIG)
        assertThat(config.logsConfig).isEqualTo(Configuration.DEFAULT_LOGS_CONFIG)
        assertThat(config.tracesConfig).isEqualTo(Configuration.DEFAULT_TRACING_CONFIG)
        assertThat(config.crashReportConfig).isEqualTo(Configuration.DEFAULT_CRASH_CONFIG)
        val expectedRumEventMapper = RumEventMapper(errorEventMapper = eventMapper)
        assertThat(config.rumConfig).isEqualTo(
            Configuration.DEFAULT_RUM_CONFIG.copy(
                rumEventMapper = expectedRumEventMapper
            )
        )
        assertThat(config.additionalConfig).isEmpty()
    }

    @Test
    fun `𝕄 build config with RUM LongTask eventMapper 𝕎 setRumLongTaskEventMapper() & build()`() {
        // Given
        val eventMapper: EventMapper<LongTaskEvent> = mock()

        // When
        val config = testedBuilder
            .setRumLongTaskEventMapper(eventMapper)
            .build()

        // Then
        assertThat(config.coreConfig).isEqualTo(Configuration.DEFAULT_CORE_CONFIG)
        assertThat(config.logsConfig).isEqualTo(Configuration.DEFAULT_LOGS_CONFIG)
        assertThat(config.tracesConfig).isEqualTo(Configuration.DEFAULT_TRACING_CONFIG)
        assertThat(config.crashReportConfig).isEqualTo(Configuration.DEFAULT_CRASH_CONFIG)
        val expectedRumEventMapper = RumEventMapper(longTaskEventMapper = eventMapper)
        assertThat(config.rumConfig).isEqualTo(
            Configuration.DEFAULT_RUM_CONFIG.copy(
                rumEventMapper = expectedRumEventMapper
            )
        )
        assertThat(config.additionalConfig).isEmpty()
    }

    @Test
    fun `𝕄 build config with RUM Telemetry eventMapper 𝕎 setTelemetryConfigurationEventMapper()`() {
        // Given
        val eventMapper: EventMapper<TelemetryConfigurationEvent> = mock()

        // When
        val builder = testedBuilder
        _InternalProxy.setTelemetryConfigurationEventMapper(builder, eventMapper)
        val config = builder.build()

        // Then
        assertThat(config.coreConfig).isEqualTo(Configuration.DEFAULT_CORE_CONFIG)
        assertThat(config.logsConfig).isEqualTo(Configuration.DEFAULT_LOGS_CONFIG)
        assertThat(config.tracesConfig).isEqualTo(Configuration.DEFAULT_TRACING_CONFIG)
        assertThat(config.crashReportConfig).isEqualTo(Configuration.DEFAULT_CRASH_CONFIG)
        val expectedRumEventMapper = RumEventMapper(telemetryConfigurationMapper = eventMapper)
        assertThat(config.rumConfig).isEqualTo(
            Configuration.DEFAULT_RUM_CONFIG.copy(
                rumEventMapper = expectedRumEventMapper
            )
        )
        assertThat(config.additionalConfig).isEmpty()
    }

    @Test
    fun `𝕄 build config with plugin 𝕎 addPlugin() and build()`() {
        // Given
        val logsPlugin: DatadogPlugin = mock()
        val tracesPlugin: DatadogPlugin = mock()
        val rumPlugin: DatadogPlugin = mock()
        val crashPlugin: DatadogPlugin = mock()

        // When
        val config = testedBuilder
            .addPlugin(logsPlugin, Feature.LOG)
            .addPlugin(tracesPlugin, Feature.TRACE)
            .addPlugin(rumPlugin, Feature.RUM)
            .addPlugin(crashPlugin, Feature.CRASH)
            .build()

        // Then
        assertThat(config.coreConfig).isEqualTo(Configuration.DEFAULT_CORE_CONFIG)
        assertThat(config.logsConfig).isEqualTo(
            Configuration.DEFAULT_LOGS_CONFIG.copy(
                plugins = listOf(logsPlugin)
            )
        )
        assertThat(config.tracesConfig).isEqualTo(
            Configuration.DEFAULT_TRACING_CONFIG.copy(
                plugins = listOf(tracesPlugin)
            )
        )
        assertThat(config.crashReportConfig).isEqualTo(
            Configuration.DEFAULT_CRASH_CONFIG.copy(
                plugins = listOf(crashPlugin)
            )
        )
        assertThat(config.rumConfig).isEqualTo(
            Configuration.DEFAULT_RUM_CONFIG.copy(
                plugins = listOf(rumPlugin)
            )
        )
        assertThat(config.additionalConfig).isEmpty()
    }

    @Test
    fun `𝕄 warn user 𝕎 trackInteractions() {RUM disabled}`() {
        // Given
        testedBuilder = Configuration.Builder(
            logsEnabled = true,
            tracesEnabled = true,
            crashReportsEnabled = true,
            rumEnabled = false
        )

        // When
        testedBuilder.trackInteractions()

        // Then
        verify(logger.mockInternalLogger).log(
            InternalLogger.Level.ERROR,
            InternalLogger.Target.USER,
            Configuration.ERROR_FEATURE_DISABLED.format(
                Locale.US,
                Feature.RUM.featureName,
                "trackInteractions"
            )
        )
    }

    @Test
    fun `𝕄 warn user 𝕎 trackLongTasks() {RUM disabled}`(
        @LongForgery(1L, 65536L) durationMs: Long
    ) {
        // Given
        testedBuilder = Configuration.Builder(
            logsEnabled = true,
            tracesEnabled = true,
            crashReportsEnabled = true,
            rumEnabled = false
        )

        // When
        testedBuilder.trackLongTasks(durationMs)

        // Then
        verify(logger.mockInternalLogger).log(
            InternalLogger.Level.ERROR,
            InternalLogger.Target.USER,
            Configuration.ERROR_FEATURE_DISABLED.format(
                Locale.US,
                Feature.RUM.featureName,
                "trackLongTasks"
            )
        )
    }

    @Test
    fun `𝕄 warn user 𝕎 useViewTrackingStrategy() {RUM disabled}`() {
        // Given
        testedBuilder = Configuration.Builder(
            logsEnabled = true,
            tracesEnabled = true,
            crashReportsEnabled = true,
            rumEnabled = false
        )
        val viewStrategy: ViewTrackingStrategy = mock()

        // When
        testedBuilder.useViewTrackingStrategy(viewStrategy)

        // Then
        verify(logger.mockInternalLogger).log(
            InternalLogger.Level.ERROR,
            InternalLogger.Target.USER,
            Configuration.ERROR_FEATURE_DISABLED.format(
                Locale.US,
                Feature.RUM.featureName,
                "useViewTrackingStrategy"
            )
        )
    }

    @Test
    fun `𝕄 warn user 𝕎 sampleRumSessions() {RUM disabled}`(
        @FloatForgery(0f, 100f) samplingRate: Float
    ) {
        // Given
        testedBuilder = Configuration.Builder(
            logsEnabled = true,
            tracesEnabled = true,
            crashReportsEnabled = true,
            rumEnabled = false
        )

        // When
        testedBuilder.sampleRumSessions(samplingRate)

        // Then
        verify(logger.mockInternalLogger).log(
            InternalLogger.Level.ERROR,
            InternalLogger.Target.USER,
            Configuration.ERROR_FEATURE_DISABLED.format(
                Locale.US,
                Feature.RUM.featureName,
                "sampleRumSessions"
            )
        )
    }

    @Test
    fun `𝕄 warn user 𝕎 setRumViewEventMapper() {RUM disabled}`() {
        // Given
        testedBuilder = Configuration.Builder(
            logsEnabled = true,
            tracesEnabled = true,
            crashReportsEnabled = true,
            rumEnabled = false
        )
        val eventMapper: ViewEventMapper = mock()

        // When
        testedBuilder.setRumViewEventMapper(eventMapper)

        // Then
        verify(logger.mockInternalLogger).log(
            InternalLogger.Level.ERROR,
            InternalLogger.Target.USER,
            Configuration.ERROR_FEATURE_DISABLED.format(
                Locale.US,
                Feature.RUM.featureName,
                "setRumViewEventMapper"
            )
        )
    }

    @Test
    fun `𝕄 warn user 𝕎 setRumResourceEventMapper() {RUM disabled}`() {
        // Given
        testedBuilder = Configuration.Builder(
            logsEnabled = true,
            tracesEnabled = true,
            crashReportsEnabled = true,
            rumEnabled = false
        )
        val eventMapper: EventMapper<ResourceEvent> = mock()

        // When
        testedBuilder.setRumResourceEventMapper(eventMapper)

        // Then
        verify(logger.mockInternalLogger).log(
            InternalLogger.Level.ERROR,
            InternalLogger.Target.USER,
            Configuration.ERROR_FEATURE_DISABLED.format(
                Locale.US,
                Feature.RUM.featureName,
                "setRumResourceEventMapper"
            )
        )
    }

    @Test
    fun `𝕄 warn user 𝕎 setRumActionEventMapper() {RUM disabled}`() {
        // Given
        testedBuilder = Configuration.Builder(
            logsEnabled = true,
            tracesEnabled = true,
            crashReportsEnabled = true,
            rumEnabled = false
        )
        val eventMapper: EventMapper<ActionEvent> = mock()

        // When
        testedBuilder.setRumActionEventMapper(eventMapper)

        // Then
        verify(logger.mockInternalLogger).log(
            InternalLogger.Level.ERROR,
            InternalLogger.Target.USER,
            Configuration.ERROR_FEATURE_DISABLED.format(
                Locale.US,
                Feature.RUM.featureName,
                "setRumActionEventMapper"
            )
        )
    }

    @Test
    fun `𝕄 warn user 𝕎 setRumErrorEventMapper() {RUM disabled}`() {
        // Given
        testedBuilder = Configuration.Builder(
            logsEnabled = true,
            tracesEnabled = true,
            crashReportsEnabled = true,
            rumEnabled = false
        )
        val eventMapper: EventMapper<ErrorEvent> = mock()

        // When
        testedBuilder.setRumErrorEventMapper(eventMapper)

        // Then
        verify(logger.mockInternalLogger).log(
            InternalLogger.Level.ERROR,
            InternalLogger.Target.USER,
            Configuration.ERROR_FEATURE_DISABLED.format(
                Locale.US,
                Feature.RUM.featureName,
                "setRumErrorEventMapper"
            )
        )
    }

    @Test
    fun `𝕄 warn user 𝕎 setRumLongTaskEventMapper() {RUM disabled}`() {
        // Given
        testedBuilder = Configuration.Builder(
            logsEnabled = true,
            tracesEnabled = true,
            crashReportsEnabled = true,
            rumEnabled = false
        )
        val eventMapper: EventMapper<LongTaskEvent> = mock()

        // When
        testedBuilder.setRumLongTaskEventMapper(eventMapper)

        // Then
        verify(logger.mockInternalLogger).log(
            InternalLogger.Level.ERROR,
            InternalLogger.Target.USER,
            Configuration.ERROR_FEATURE_DISABLED.format(
                Locale.US,
                Feature.RUM.featureName,
                "setRumLongTaskEventMapper"
            )
        )
    }

    @Test
    fun `𝕄 warn user 𝕎 addPlugin() {log feature disabled}`() {
        // Given
        testedBuilder = Configuration.Builder(
            logsEnabled = false,
            tracesEnabled = true,
            crashReportsEnabled = true,
            rumEnabled = true
        )
        val logsPlugin: DatadogPlugin = mock()

        // When
        testedBuilder.addPlugin(logsPlugin, Feature.LOG)

        // Then
        verify(logger.mockInternalLogger).log(
            InternalLogger.Level.ERROR,
            InternalLogger.Target.USER,
            Configuration.ERROR_FEATURE_DISABLED.format(
                Locale.US,
                Feature.LOG.featureName,
                "addPlugin"
            )
        )
    }

    @Test
    fun `𝕄 warn user 𝕎 addPlugin() {trace feature disabled}`() {
        // Given
        testedBuilder = Configuration.Builder(
            logsEnabled = true,
            tracesEnabled = false,
            crashReportsEnabled = true,
            rumEnabled = true
        )
        val tracesPlugin: DatadogPlugin = mock()

        // When
        testedBuilder.addPlugin(tracesPlugin, Feature.TRACE)

        // Then
        verify(logger.mockInternalLogger).log(
            InternalLogger.Level.ERROR,
            InternalLogger.Target.USER,
            Configuration.ERROR_FEATURE_DISABLED.format(
                Locale.US,
                Feature.TRACE.featureName,
                "addPlugin"
            )
        )
    }

    @Test
    fun `𝕄 warn user 𝕎 addPlugin() {crash feature disabled}`() {
        // Given
        testedBuilder = Configuration.Builder(
            logsEnabled = true,
            tracesEnabled = true,
            crashReportsEnabled = false,
            rumEnabled = true
        )
        val crashPlugin: DatadogPlugin = mock()

        // When
        testedBuilder.addPlugin(crashPlugin, Feature.CRASH)

        // Then
        verify(logger.mockInternalLogger).log(
            InternalLogger.Level.ERROR,
            InternalLogger.Target.USER,
            Configuration.ERROR_FEATURE_DISABLED.format(
                Locale.US,
                Feature.CRASH.featureName,
                "addPlugin"
            )
        )
    }

    @Test
    fun `𝕄 warn user 𝕎 addPlugin() {RUM feature disabled}`() {
        // Given
        testedBuilder = Configuration.Builder(
            logsEnabled = true,
            tracesEnabled = true,
            crashReportsEnabled = true,
            rumEnabled = false
        )
        val rumPlugin: DatadogPlugin = mock()

        // When
        testedBuilder.addPlugin(rumPlugin, Feature.RUM)

        // Then
        verify(logger.mockInternalLogger).log(
            InternalLogger.Level.ERROR,
            InternalLogger.Target.USER,
            Configuration.ERROR_FEATURE_DISABLED.format(
                Locale.US,
                Feature.RUM.featureName,
                "addPlugin"
            )
        )
    }

    @Test
    fun `𝕄 warn user about deprecation 𝕎 addPlugin()`(
        forge: Forge
    ) {
        // Given
        testedBuilder = Configuration.Builder(
            logsEnabled = forge.aBool(),
            tracesEnabled = forge.aBool(),
            crashReportsEnabled = forge.aBool(),
            rumEnabled = forge.aBool()
        )
        val mockPlugin: DatadogPlugin = mock()

        // When
        testedBuilder.addPlugin(
            mockPlugin,
            forge.anElementFrom(Feature.CRASH, Feature.RUM, Feature.LOG, Feature.TRACE)
        )

        // Then
        verify(logger.mockInternalLogger).log(
            InternalLogger.Level.WARN,
            InternalLogger.Target.USER,
            "Configuration.Builder#addPlugin has been deprecated since version 1.15.0, " +
                "and will be removed in version 2.0.0."
        )
    }

    @Test
    fun `𝕄 warn user 𝕎 useCustomLogsEndpoint() {Logs feature disabled}`(
        @StringForgery(regex = "https://[a-z]+\\.com") url: String
    ) {
        // Given
        testedBuilder = Configuration.Builder(
            logsEnabled = false,
            tracesEnabled = true,
            crashReportsEnabled = true,
            rumEnabled = true
        )

        // When
        testedBuilder.useCustomLogsEndpoint(url)

        // Then
        verify(logger.mockInternalLogger).log(
            InternalLogger.Level.ERROR,
            InternalLogger.Target.USER,
            Configuration.ERROR_FEATURE_DISABLED.format(
                Locale.US,
                Feature.LOG.featureName,
                "useCustomLogsEndpoint"
            )
        )
    }

    @Test
    fun `𝕄 warn user 𝕎 useCustomTracesEndpoint() {Trace feature disabled}`(
        @StringForgery(regex = "https://[a-z]+\\.com") url: String
    ) {
        // Given
        testedBuilder = Configuration.Builder(
            logsEnabled = true,
            tracesEnabled = false,
            crashReportsEnabled = true,
            rumEnabled = true
        )

        // When
        testedBuilder.useCustomTracesEndpoint(url)

        // Then
        verify(logger.mockInternalLogger).log(
            InternalLogger.Level.ERROR,
            InternalLogger.Target.USER,
            Configuration.ERROR_FEATURE_DISABLED.format(
                Locale.US,
                Feature.TRACE.featureName,
                "useCustomTracesEndpoint"
            )
        )
    }

    @Test
    fun `𝕄 warn user 𝕎 useCustomCrashReportsEndpoint() {Crash feature disabled}`(
        @StringForgery(regex = "https://[a-z]+\\.com") url: String
    ) {
        // Given
        testedBuilder = Configuration.Builder(
            logsEnabled = true,
            tracesEnabled = true,
            crashReportsEnabled = false,
            rumEnabled = true
        )

        // When
        testedBuilder.useCustomCrashReportsEndpoint(url)

        // Then
        verify(logger.mockInternalLogger).log(
            InternalLogger.Level.ERROR,
            InternalLogger.Target.USER,
            Configuration.ERROR_FEATURE_DISABLED.format(
                Locale.US,
                Feature.CRASH.featureName,
                "useCustomCrashReportsEndpoint"
            )
        )
    }

    @Test
    fun `𝕄 warn user 𝕎 useCustomRumEndpoint() {RUM feature disabled}`(
        @StringForgery(regex = "https://[a-z]+\\.com") url: String
    ) {
        // Given
        testedBuilder = Configuration.Builder(
            logsEnabled = true,
            tracesEnabled = true,
            crashReportsEnabled = true,
            rumEnabled = false
        )

        // When
        testedBuilder.useCustomRumEndpoint(url)

        // Then
        verify(logger.mockInternalLogger).log(
            InternalLogger.Level.ERROR,
            InternalLogger.Target.USER,
            Configuration.ERROR_FEATURE_DISABLED.format(
                Locale.US,
                Feature.RUM.featureName,
                "useCustomRumEndpoint"
            )
        )
    }

    @Test
    fun `𝕄 build config with first party hosts 𝕎 setFirstPartyHosts() { ip addresses }`(
        @StringForgery(
            regex = "(([0-9]|[1-9][0-9]|1[0-9]{2}|2[0-4][0-9]|25[0-5])\\.){3}" +
                "([0-9]|[1-9][0-9]|1[0-9]{2}|2[0-4][0-9]|25[0-5])"
        ) hosts: List<String>
    ) {
        // When
        val config = testedBuilder
            .setFirstPartyHosts(hosts)
            .build()

        // Then
        assertThat(config.coreConfig).isEqualTo(
            Configuration.DEFAULT_CORE_CONFIG.copy(
                firstPartyHostsWithHeaderTypes =
                hosts.associateWith { setOf(TracingHeaderType.DATADOG) }
            )
        )
        assertThat(config.logsConfig).isEqualTo(Configuration.DEFAULT_LOGS_CONFIG)
        assertThat(config.tracesConfig).isEqualTo(Configuration.DEFAULT_TRACING_CONFIG)
        assertThat(config.crashReportConfig).isEqualTo(Configuration.DEFAULT_CRASH_CONFIG)
        assertThat(config.rumConfig).isEqualTo(
            Configuration.DEFAULT_RUM_CONFIG.copy(
                userActionTrackingStrategy = UserActionTrackingStrategyLegacy(
                    DatadogGesturesTracker(
                        arrayOf(JetpackViewAttributesProvider()),
                        NoOpInteractionPredicate()
                    )
                )
            )
        )
        assertThat(config.additionalConfig).isEmpty()
    }

    @Test
    fun `𝕄 build config with first party hosts 𝕎 setFirstPartyHosts() { host names }`(
        @StringForgery(
            regex = "(([a-zA-Z0-9]|[a-zA-Z0-9][a-zA-Z0-9-]*[a-zA-Z0-9])\\.)+" +
                "([A-Za-z]|[A-Za-z][A-Za-z0-9-]*[A-Za-z0-9])"
        ) hosts: List<String>
    ) {
        // When
        val config = testedBuilder
            .setFirstPartyHosts(hosts)
            .build()

        // Then
        assertThat(config.coreConfig).isEqualTo(
            Configuration.DEFAULT_CORE_CONFIG.copy(
                firstPartyHostsWithHeaderTypes = hosts.associateWith {
                    setOf(
                        TracingHeaderType.DATADOG
                    )
                }
            )
        )
        assertThat(config.logsConfig).isEqualTo(Configuration.DEFAULT_LOGS_CONFIG)
        assertThat(config.tracesConfig).isEqualTo(Configuration.DEFAULT_TRACING_CONFIG)
        assertThat(config.crashReportConfig).isEqualTo(Configuration.DEFAULT_CRASH_CONFIG)
        assertThat(config.rumConfig).isEqualTo(Configuration.DEFAULT_RUM_CONFIG)
        assertThat(config.additionalConfig).isEmpty()
    }

    @Test
    fun `M use url host name W setFirstPartyHosts() { url }`(
        @StringForgery(
            regex = "(https|http)://([a-z][a-z0-9-]{3,9}\\.){1,4}[a-z][a-z0-9]{2,3}"
        ) hosts: List<String>
    ) {
        // WHEN
        val config = testedBuilder
            .setFirstPartyHosts(hosts)
            .build()

        // THEN
        assertThat(config.coreConfig).isEqualTo(
            Configuration.DEFAULT_CORE_CONFIG.copy(
                firstPartyHostsWithHeaderTypes =
                hosts.associate { URL(it).host to setOf(TracingHeaderType.DATADOG) }
            )
        )
        assertThat(config.logsConfig).isEqualTo(Configuration.DEFAULT_LOGS_CONFIG)
        assertThat(config.tracesConfig).isEqualTo(Configuration.DEFAULT_TRACING_CONFIG)
        assertThat(config.crashReportConfig).isEqualTo(Configuration.DEFAULT_CRASH_CONFIG)
        assertThat(config.rumConfig).isEqualTo(Configuration.DEFAULT_RUM_CONFIG)
        assertThat(config.additionalConfig).isEmpty()
    }

    @Test
    fun `𝕄 sanitize hosts 𝕎 setFirstPartyHosts()`(
        @StringForgery(
            regex = "(([a-zA-Z0-9]|[a-zA-Z0-9][a-zA-Z0-9-]*[a-zA-Z0-9])\\.)+" +
                "([A-Za-z]|[A-Za-z][A-Za-z0-9-]*[A-Za-z0-9])"
        ) hosts: List<String>
    ) {
        // When
        val mockSanitizer: HostsSanitizer = mock()
        testedBuilder.hostsSanitizer = mockSanitizer
        testedBuilder
            .setFirstPartyHosts(hosts)
            .build()

        // Then
        verify(mockSanitizer)
            .sanitizeHosts(
                hosts,
                Configuration.NETWORK_REQUESTS_TRACKING_FEATURE_NAME
            )
    }

    @Test
    fun `𝕄 build config with first party hosts and header types 𝕎 setFirstPartyHostsWithHeaderType() { host names }`(
        @StringForgery(
            regex = "(([a-zA-Z0-9]|[a-zA-Z0-9][a-zA-Z0-9-]*[a-zA-Z0-9])\\.)+" +
                "([A-Za-z]|[A-Za-z][A-Za-z0-9-]*[A-Za-z0-9])"
        ) hosts: List<String>,
        forge: Forge
    ) {
        val hostWithHeaderTypes = hosts.associateWith {
            forge.aList { aValueFrom(TracingHeaderType::class.java) }.toSet()
        }

        // When
        val config = testedBuilder
            .setFirstPartyHostsWithHeaderType(hostWithHeaderTypes)
            .build()

        // Then
        assertThat(config.coreConfig).isEqualTo(
            Configuration.DEFAULT_CORE_CONFIG.copy(firstPartyHostsWithHeaderTypes = hostWithHeaderTypes)
        )
        assertThat(config.logsConfig).isEqualTo(Configuration.DEFAULT_LOGS_CONFIG)
        assertThat(config.tracesConfig).isEqualTo(Configuration.DEFAULT_TRACING_CONFIG)
        assertThat(config.crashReportConfig).isEqualTo(Configuration.DEFAULT_CRASH_CONFIG)
        assertThat(config.rumConfig).isEqualTo(Configuration.DEFAULT_RUM_CONFIG)
        assertThat(config.additionalConfig).isEmpty()
    }

    @Test
    fun `𝕄 build config with web tracking hosts 𝕎 setWebViewTrackingHosts() { ip addresses }`(
        @StringForgery(
            regex = "(([0-9]|[1-9][0-9]|1[0-9]{2}|2[0-4][0-9]|25[0-5])\\.){3}" +
                "([0-9]|[1-9][0-9]|1[0-9]{2}|2[0-4][0-9]|25[0-5])"
        ) hosts: List<String>
    ) {
        // When
        val config = testedBuilder
            .setWebViewTrackingHosts(hosts)
            .build()

        // Then
        assertThat(config.coreConfig).isEqualTo(
            Configuration.DEFAULT_CORE_CONFIG.copy(webViewTrackingHosts = hosts)
        )
        assertThat(config.logsConfig).isEqualTo(Configuration.DEFAULT_LOGS_CONFIG)
        assertThat(config.tracesConfig).isEqualTo(Configuration.DEFAULT_TRACING_CONFIG)
        assertThat(config.crashReportConfig).isEqualTo(Configuration.DEFAULT_CRASH_CONFIG)
        assertThat(config.rumConfig).isEqualTo(
            Configuration.DEFAULT_RUM_CONFIG.copy(
                userActionTrackingStrategy = UserActionTrackingStrategyLegacy(
                    DatadogGesturesTracker(
                        arrayOf(JetpackViewAttributesProvider()),
                        NoOpInteractionPredicate()
                    )
                )
            )
        )
        assertThat(config.additionalConfig).isEmpty()
    }

    @Test
    fun `𝕄 build config with web tracking hosts 𝕎 setWebViewTrackingHosts() { host names }`(
        @StringForgery(
            regex = "(([a-zA-Z0-9]|[a-zA-Z0-9][a-zA-Z0-9-]*[a-zA-Z0-9])\\.)+" +
                "([A-Za-z]|[A-Za-z][A-Za-z0-9-]*[A-Za-z0-9])"
        ) hosts: List<String>
    ) {
        // When
        val config = testedBuilder
            .setWebViewTrackingHosts(hosts)
            .build()

        // Then
        assertThat(config.coreConfig).isEqualTo(
            Configuration.DEFAULT_CORE_CONFIG.copy(webViewTrackingHosts = hosts)
        )
        assertThat(config.logsConfig).isEqualTo(Configuration.DEFAULT_LOGS_CONFIG)
        assertThat(config.tracesConfig).isEqualTo(Configuration.DEFAULT_TRACING_CONFIG)
        assertThat(config.crashReportConfig).isEqualTo(Configuration.DEFAULT_CRASH_CONFIG)
        assertThat(config.rumConfig).isEqualTo(Configuration.DEFAULT_RUM_CONFIG)
        assertThat(config.additionalConfig).isEmpty()
    }

    @Test
    fun `M use url host name W setWebViewTrackingHosts() { url }`(
        @StringForgery(
            regex = "(https|http)://([a-z][a-z0-9-]{3,9}\\.){1,4}[a-z][a-z0-9]{2,3}"
        ) hosts: List<String>
    ) {
        // WHEN
        val config = testedBuilder
            .setWebViewTrackingHosts(hosts)
            .build()

        // THEN
        assertThat(config.coreConfig).isEqualTo(
            Configuration.DEFAULT_CORE_CONFIG
                .copy(webViewTrackingHosts = hosts.map { URL(it).host })
        )
        assertThat(config.logsConfig).isEqualTo(Configuration.DEFAULT_LOGS_CONFIG)
        assertThat(config.tracesConfig).isEqualTo(Configuration.DEFAULT_TRACING_CONFIG)
        assertThat(config.crashReportConfig).isEqualTo(Configuration.DEFAULT_CRASH_CONFIG)
        assertThat(config.rumConfig).isEqualTo(Configuration.DEFAULT_RUM_CONFIG)
        assertThat(config.additionalConfig).isEmpty()
    }

    @Test
    fun `𝕄 sanitize hosts 𝕎 setWebViewTrackingHosts()`(
        @StringForgery(
            regex = "(([a-zA-Z0-9]|[a-zA-Z0-9][a-zA-Z0-9-]*[a-zA-Z0-9])\\.)+" +
                "([A-Za-z]|[A-Za-z][A-Za-z0-9-]*[A-Za-z0-9])"
        ) hosts: List<String>
    ) {
        // When
        val mockSanitizer: HostsSanitizer = mock()
        testedBuilder.hostsSanitizer = mockSanitizer
        testedBuilder
            .setWebViewTrackingHosts(hosts)
            .build()

        // Then
        verify(mockSanitizer)
            .sanitizeHosts(
                hosts,
                Configuration.WEB_VIEW_TRACKING_FEATURE_NAME
            )
    }

    @Test
    fun `𝕄 use batch size 𝕎 setBatchSize()`(
        @Forgery batchSize: BatchSize
    ) {
        // When
        val config = testedBuilder
            .setBatchSize(batchSize)
            .build()

        // Then
        assertThat(config.coreConfig).isEqualTo(
            Configuration.DEFAULT_CORE_CONFIG.copy(batchSize = batchSize)
        )
        assertThat(config.logsConfig).isEqualTo(Configuration.DEFAULT_LOGS_CONFIG)
        assertThat(config.tracesConfig).isEqualTo(Configuration.DEFAULT_TRACING_CONFIG)
        assertThat(config.crashReportConfig).isEqualTo(Configuration.DEFAULT_CRASH_CONFIG)
        assertThat(config.rumConfig).isEqualTo(Configuration.DEFAULT_RUM_CONFIG)
        assertThat(config.additionalConfig).isEmpty()
    }

    @Test
    fun `M developer flag set W setUseDeveloperModeWhenDebuggable()`(
        @BoolForgery enableDeveloperDebugInfo: Boolean
    ) {
        // When
        val config = testedBuilder
            .setUseDeveloperModeWhenDebuggable(enableDeveloperDebugInfo)
            .build()

        // Then
        assertThat(config.coreConfig).isEqualTo(
            Configuration.DEFAULT_CORE_CONFIG.copy(
                enableDeveloperModeWhenDebuggable = enableDeveloperDebugInfo
            )
        )
    }

    @Test
    fun `𝕄 use upload frequency 𝕎 setUploadFrequency()`(
        @Forgery uploadFrequency: UploadFrequency
    ) {
        // When
        val config = testedBuilder
            .setUploadFrequency(uploadFrequency)
            .build()

        // Then
        assertThat(config.coreConfig).isEqualTo(
            Configuration.DEFAULT_CORE_CONFIG.copy(uploadFrequency = uploadFrequency)
        )
        assertThat(config.logsConfig).isEqualTo(Configuration.DEFAULT_LOGS_CONFIG)
        assertThat(config.tracesConfig).isEqualTo(Configuration.DEFAULT_TRACING_CONFIG)
        assertThat(config.crashReportConfig).isEqualTo(Configuration.DEFAULT_CRASH_CONFIG)
        assertThat(config.rumConfig).isEqualTo(Configuration.DEFAULT_RUM_CONFIG)
        assertThat(config.additionalConfig).isEmpty()
    }

    @Test
    fun `𝕄 build with additionalConfig 𝕎 setAdditionalConfiguration()`(forge: Forge) {
        // Given
        val additionalConfig = forge.aMap {
            forge.anAsciiString() to forge.aString()
        }

        // When
        val config = testedBuilder
            .setAdditionalConfiguration(additionalConfig)
            .build()

        // Then
        assertThat(config.additionalConfig).isEqualTo(additionalConfig)

        assertThat(config.coreConfig).isEqualTo(Configuration.DEFAULT_CORE_CONFIG)
        assertThat(config.logsConfig).isEqualTo(Configuration.DEFAULT_LOGS_CONFIG)
        assertThat(config.tracesConfig).isEqualTo(Configuration.DEFAULT_TRACING_CONFIG)
        assertThat(config.crashReportConfig).isEqualTo(Configuration.DEFAULT_CRASH_CONFIG)
        assertThat(config.rumConfig).isEqualTo(Configuration.DEFAULT_RUM_CONFIG)
    }

    @Test
    fun `𝕄 build config with Span eventMapper 𝕎 setSpanEventMapper() and build()`() {
        // Given
        val mockEventMapper: SpanEventMapper = mock()

        // When
        val config = testedBuilder
            .setSpanEventMapper(mockEventMapper)
            .build()

        // Then
        assertThat(config.coreConfig).isEqualTo(Configuration.DEFAULT_CORE_CONFIG)
        assertThat(config.logsConfig).isEqualTo(Configuration.DEFAULT_LOGS_CONFIG)
        assertThat(config.rumConfig).isEqualTo(Configuration.DEFAULT_RUM_CONFIG)
        assertThat(config.tracesConfig).isEqualTo(
            Configuration.DEFAULT_TRACING_CONFIG.copy(
                spanEventMapper = mockEventMapper
            )
        )
    }

    @Test
    fun `𝕄 build config with Log eventMapper 𝕎 setLogEventMapper() and build()`() {
        // Given
        val mockEventMapper: EventMapper<LogEvent> = mock()

        // When
        val config = testedBuilder
            .setLogEventMapper(mockEventMapper)
            .build()

        // Then
        assertThat(config.coreConfig).isEqualTo(Configuration.DEFAULT_CORE_CONFIG)
        assertThat(config.rumConfig).isEqualTo(Configuration.DEFAULT_RUM_CONFIG)
        assertThat(config.tracesConfig).isEqualTo(Configuration.DEFAULT_TRACING_CONFIG)
        assertThat(config.logsConfig).isEqualTo(
            Configuration.DEFAULT_LOGS_CONFIG.copy(
                logsEventMapper = mockEventMapper
            )
        )
    }

    @Test
    fun `𝕄 build config with Proxy and Auth configuration 𝕎 setProxy() and build()`() {
        // Given
        val mockProxy: Proxy = mock()
        val mockAuthenticator: Authenticator = mock()

        // When
        val config = testedBuilder
            .setProxy(mockProxy, mockAuthenticator)
            .build()

        // Then
        assertThat(config.coreConfig).isEqualTo(
            Configuration.DEFAULT_CORE_CONFIG.copy(
                proxy = mockProxy,
                proxyAuth = mockAuthenticator
            )
        )
        assertThat(config.rumConfig).isEqualTo(Configuration.DEFAULT_RUM_CONFIG)
        assertThat(config.tracesConfig).isEqualTo(Configuration.DEFAULT_TRACING_CONFIG)
        assertThat(config.logsConfig).isEqualTo(Configuration.DEFAULT_LOGS_CONFIG)
    }

    @Test
    fun `𝕄 build config with Proxy configuration 𝕎 setProxy() and build()`() {
        // Given
        val mockProxy: Proxy = mock()

        // When
        val config = testedBuilder
            .setProxy(mockProxy, null)
            .build()

        // Then
        assertThat(config.coreConfig).isEqualTo(
            Configuration.DEFAULT_CORE_CONFIG.copy(
                proxy = mockProxy,
                proxyAuth = Authenticator.NONE
            )
        )
        assertThat(config.rumConfig).isEqualTo(Configuration.DEFAULT_RUM_CONFIG)
        assertThat(config.tracesConfig).isEqualTo(Configuration.DEFAULT_TRACING_CONFIG)
        assertThat(config.logsConfig).isEqualTo(Configuration.DEFAULT_LOGS_CONFIG)
    }

    @Test
    fun `𝕄 build config with security configuration 𝕎 setEncryption() and build()`() {
        // Given
        val mockEncryption = mock<Encryption>()

        // When
        val config = testedBuilder
            .setEncryption(mockEncryption)
            .build()

        // Then
        assertThat(config.coreConfig).isEqualTo(
            Configuration.DEFAULT_CORE_CONFIG.copy(
                encryption = mockEncryption
            )
        )
        assertThat(config.rumConfig).isEqualTo(Configuration.DEFAULT_RUM_CONFIG)
        assertThat(config.tracesConfig).isEqualTo(Configuration.DEFAULT_TRACING_CONFIG)
        assertThat(config.logsConfig).isEqualTo(Configuration.DEFAULT_LOGS_CONFIG)
    }

    @Test
    fun `M use the given frequency W setVitalsMonitorUpdateFrequency`(
        @Forgery fakeFrequency: VitalsUpdateFrequency
    ) {
        // When
        val config = testedBuilder
            .setVitalsUpdateFrequency(fakeFrequency)
            .build()

        // Then
        assertThat(config.rumConfig).isEqualTo(
            Configuration.DEFAULT_RUM_CONFIG.copy(vitalsMonitorUpdateFrequency = fakeFrequency)
        )
    }

    companion object {
        val logger = InternalLoggerTestConfiguration()

        @TestConfigurationsProvider
        @JvmStatic
        fun getTestConfigurations(): List<TestConfiguration> {
            return listOf(logger)
        }
    }
}
