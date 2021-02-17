/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.configuration

import android.os.Build
import android.util.Log
import com.datadog.android.DatadogEndpoint
import com.datadog.android.core.internal.event.NoOpEventMapper
import com.datadog.android.event.EventMapper
import com.datadog.android.log.internal.logger.LogHandler
import com.datadog.android.plugin.DatadogPlugin
import com.datadog.android.plugin.Feature
import com.datadog.android.rum.assertj.ConfigurationRumAssert.Companion.assertThat
import com.datadog.android.rum.internal.domain.event.RumEventMapper
import com.datadog.android.rum.model.ActionEvent
import com.datadog.android.rum.model.ErrorEvent
import com.datadog.android.rum.model.ResourceEvent
import com.datadog.android.rum.model.ViewEvent
import com.datadog.android.rum.tracking.ViewAttributesProvider
import com.datadog.android.rum.tracking.ViewTrackingStrategy
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.utils.mockDevLogHandler
import com.datadog.tools.unit.annotations.TestTargetApi
import com.datadog.tools.unit.extensions.ApiLevelExtension
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.anyOrNull
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.FloatForgery
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import java.net.MalformedURLException
import java.net.URL
import java.util.Locale
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class),
    ExtendWith(ApiLevelExtension::class)
)
@MockitoSettings()
@ForgeConfiguration(value = Configurator::class)
internal class ConfigurationBuilderTest {

    lateinit var testedBuilder: Configuration.Builder

    @StringForgery
    lateinit var fakeEnvName: String

    lateinit var mockDevLogHandler: LogHandler

    @BeforeEach
    fun `set up`() {
        mockDevLogHandler = mockDevLogHandler()
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
                firstPartyHosts = emptyList(),
                batchSize = BatchSize.MEDIUM,
                uploadFrequency = UploadFrequency.AVERAGE
            )
        )
        assertThat(config.logsConfig).isEqualTo(
            Configuration.Feature.Logs(
                endpointUrl = DatadogEndpoint.LOGS_US,
                plugins = emptyList()
            )
        )
        assertThat(config.tracesConfig).isEqualTo(
            Configuration.Feature.Tracing(
                endpointUrl = DatadogEndpoint.TRACES_US,
                plugins = emptyList()
            )
        )
        assertThat(config.crashReportConfig).isEqualTo(
            Configuration.Feature.CrashReport(
                endpointUrl = DatadogEndpoint.LOGS_US,
                plugins = emptyList()
            )
        )
        assertThat(config.rumConfig).isEqualTo(
            Configuration.Feature.RUM(
                endpointUrl = DatadogEndpoint.RUM_US,
                plugins = emptyList(),
                samplingRate = Configuration.DEFAULT_SAMPLING_RATE,
                gesturesTracker = null,
                userActionTrackingStrategy = null,
                viewTrackingStrategy = null,
                rumEventMapper = NoOpEventMapper()
            )
        )
    }

    @Test
    fun `𝕄 build config with US endpoints 𝕎 useUSEndpoints() and build()`() {
        // When
        val config = testedBuilder.useUSEndpoints().build()

        // Then
        assertThat(config.coreConfig).isEqualTo(Configuration.DEFAULT_CORE_CONFIG)
        assertThat(config.logsConfig).isEqualTo(
            Configuration.DEFAULT_LOGS_CONFIG.copy(endpointUrl = DatadogEndpoint.LOGS_US)
        )
        assertThat(config.tracesConfig).isEqualTo(
            Configuration.DEFAULT_TRACING_CONFIG.copy(endpointUrl = DatadogEndpoint.TRACES_US)
        )
        assertThat(config.crashReportConfig).isEqualTo(
            Configuration.DEFAULT_CRASH_CONFIG.copy(endpointUrl = DatadogEndpoint.LOGS_US)
        )
        assertThat(config.rumConfig).isEqualTo(
            Configuration.DEFAULT_RUM_CONFIG.copy(endpointUrl = DatadogEndpoint.RUM_US)
        )
    }

    @Test
    fun `𝕄 build config with EU endpoints 𝕎 useEUEndpoints() and build()`() {
        // When
        val config = testedBuilder.useEUEndpoints().build()

        // Then
        assertThat(config.coreConfig).isEqualTo(Configuration.DEFAULT_CORE_CONFIG)
        assertThat(config.logsConfig).isEqualTo(
            Configuration.DEFAULT_LOGS_CONFIG.copy(endpointUrl = DatadogEndpoint.LOGS_EU)
        )
        assertThat(config.tracesConfig).isEqualTo(
            Configuration.DEFAULT_TRACING_CONFIG.copy(endpointUrl = DatadogEndpoint.TRACES_EU)
        )
        assertThat(config.crashReportConfig).isEqualTo(
            Configuration.DEFAULT_CRASH_CONFIG.copy(endpointUrl = DatadogEndpoint.LOGS_EU)
        )
        assertThat(config.rumConfig).isEqualTo(
            Configuration.DEFAULT_RUM_CONFIG.copy(endpointUrl = DatadogEndpoint.RUM_EU)
        )
    }

    @Test
    fun `𝕄 build config with GOV endpoints 𝕎 useGOVEndpoints() and build()`() {
        // When
        val config = testedBuilder.useGovEndpoints().build()

        // Then
        assertThat(config.coreConfig).isEqualTo(Configuration.DEFAULT_CORE_CONFIG)
        assertThat(config.logsConfig).isEqualTo(
            Configuration.DEFAULT_LOGS_CONFIG.copy(endpointUrl = DatadogEndpoint.LOGS_GOV)
        )
        assertThat(config.tracesConfig).isEqualTo(
            Configuration.DEFAULT_TRACING_CONFIG.copy(endpointUrl = DatadogEndpoint.TRACES_GOV)
        )
        assertThat(config.crashReportConfig).isEqualTo(
            Configuration.DEFAULT_CRASH_CONFIG.copy(endpointUrl = DatadogEndpoint.LOGS_GOV)
        )
        assertThat(config.rumConfig).isEqualTo(
            Configuration.DEFAULT_RUM_CONFIG.copy(endpointUrl = DatadogEndpoint.RUM_GOV)
        )
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
    }

    @Test
    fun `𝕄 build config with gestures enabled 𝕎 trackInteractions() and build()`(
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
            .hasGesturesTrackingStrategy()
            .hasViewAttributeProviders(mockProviders)
            .doesNotHaveViewTrackingStrategy()
    }

    @TestTargetApi(value = Build.VERSION_CODES.Q)
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
            .hasGesturesTrackingStrategyApi29()
            .hasViewAttributeProviders(mockProviders)
            .doesNotHaveViewTrackingStrategy()
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
        assertThat(config.rumConfig!!)
            .doesNotHaveGesturesTrackingStrategy()
            .hasViewTrackingStrategy(strategy)
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
        assertThat(config.rumConfig!!).isEqualTo(
            Configuration.DEFAULT_RUM_CONFIG.copy(
                samplingRate = sampling
            )
        )
    }

    @Test
    fun `𝕄 build config with RUM View eventMapper 𝕎 setRumViewEventMapper() and build()`() {
        // Given
        val eventMapper: EventMapper<ViewEvent> = mock()

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
        assertThat(config.rumConfig!!).isEqualTo(
            Configuration.DEFAULT_RUM_CONFIG.copy(
                rumEventMapper = expectedRumEventMapper
            )
        )
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
        assertThat(config.rumConfig!!).isEqualTo(
            Configuration.DEFAULT_RUM_CONFIG.copy(
                plugins = listOf(rumPlugin)
            )
        )
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
        verify(mockDevLogHandler).handleLog(
            Log.ERROR,
            Configuration.ERROR_FEATURE_DISABLED.format(
                Locale.US,
                Feature.RUM.featureName,
                "trackInteractions"
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
        verify(mockDevLogHandler).handleLog(
            Log.ERROR,
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
        verify(mockDevLogHandler).handleLog(
            Log.ERROR,
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
        val eventMapper: EventMapper<ViewEvent> = mock()

        // When
        testedBuilder.setRumViewEventMapper(eventMapper)

        // Then
        verify(mockDevLogHandler).handleLog(
            Log.ERROR,
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
        verify(mockDevLogHandler).handleLog(
            Log.ERROR,
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
        verify(mockDevLogHandler).handleLog(
            Log.ERROR,
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
        verify(mockDevLogHandler).handleLog(
            Log.ERROR,
            Configuration.ERROR_FEATURE_DISABLED.format(
                Locale.US,
                Feature.RUM.featureName,
                "setRumErrorEventMapper"
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
        verify(mockDevLogHandler).handleLog(
            Log.ERROR,
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
        verify(mockDevLogHandler).handleLog(
            Log.ERROR,
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
        verify(mockDevLogHandler).handleLog(
            Log.ERROR,
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
        verify(mockDevLogHandler).handleLog(
            Log.ERROR,
            Configuration.ERROR_FEATURE_DISABLED.format(
                Locale.US,
                Feature.RUM.featureName,
                "addPlugin"
            )
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
        verify(mockDevLogHandler).handleLog(
            Log.ERROR,
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
        verify(mockDevLogHandler).handleLog(
            Log.ERROR,
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
        verify(mockDevLogHandler).handleLog(
            Log.ERROR,
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
        verify(mockDevLogHandler).handleLog(
            Log.ERROR,
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
            Configuration.DEFAULT_CORE_CONFIG.copy(firstPartyHosts = hosts)
        )
        assertThat(config.logsConfig).isEqualTo(Configuration.DEFAULT_LOGS_CONFIG)
        assertThat(config.tracesConfig).isEqualTo(Configuration.DEFAULT_TRACING_CONFIG)
        assertThat(config.crashReportConfig).isEqualTo(Configuration.DEFAULT_CRASH_CONFIG)
        assertThat(config.rumConfig).isEqualTo(Configuration.DEFAULT_RUM_CONFIG)
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
            Configuration.DEFAULT_CORE_CONFIG.copy(firstPartyHosts = hosts)
        )
        assertThat(config.logsConfig).isEqualTo(Configuration.DEFAULT_LOGS_CONFIG)
        assertThat(config.tracesConfig).isEqualTo(Configuration.DEFAULT_TRACING_CONFIG)
        assertThat(config.crashReportConfig).isEqualTo(Configuration.DEFAULT_CRASH_CONFIG)
        assertThat(config.rumConfig).isEqualTo(Configuration.DEFAULT_RUM_CONFIG)
    }

    @Test
    fun `𝕄 drop everything 𝕎 setFirstPartyHosts { using top level domain hosts only}`(
        @StringForgery(
            regex = "([A-Za-z]|[A-Za-z][A-Za-z0-9-]*[A-Za-z0-9])"
        ) hosts: List<String>
    ) {
        // When
        val config = testedBuilder
            .setFirstPartyHosts(hosts)
            .build()

        // Then
        assertThat(config.coreConfig).isEqualTo(Configuration.DEFAULT_CORE_CONFIG)
    }

    @Test
    fun `𝕄 only accept the localhost 𝕎 setFirstPartyHosts { using top level domain hosts only}`(
        @StringForgery(
            regex = "([A-Za-z]|[A-Za-z][A-Za-z0-9-]*[A-Za-z0-9])"
        ) hosts: List<String>,
        forge: Forge
    ) {
        // When
        val fakeLocalHost = forge.aStringMatching("localhost|LOCALHOST")
        val hostsWithLocalHost =
            hosts.toMutableList().apply { add(fakeLocalHost) }
        val config = testedBuilder
            .setFirstPartyHosts(hostsWithLocalHost)
            .build()

        // Then
        assertThat(config.coreConfig).isEqualTo(
            Configuration.DEFAULT_CORE_CONFIG.copy(firstPartyHosts = listOf(fakeLocalHost))
        )
        assertThat(config.logsConfig).isEqualTo(Configuration.DEFAULT_LOGS_CONFIG)
        assertThat(config.tracesConfig).isEqualTo(Configuration.DEFAULT_TRACING_CONFIG)
        assertThat(config.crashReportConfig).isEqualTo(Configuration.DEFAULT_CRASH_CONFIG)
        assertThat(config.rumConfig).isEqualTo(Configuration.DEFAULT_RUM_CONFIG)
    }

    @Test
    fun `M log error W setFirstPartyHosts() { malformed hostname }`(
        @StringForgery(
            regex = "(([-+=~><?][a-zA-Z0-9-]*[a-zA-Z0-9])\\.)+([-+=~><?][A-Za-z0-9]*)" +
                "|(([a-zA-Z0-9-]*[a-zA-Z0-9])\\.)+([A-Za-z0-9]*[-+=~><?])"
        ) hosts: List<String>
    ) {

        // WHEN
        testedBuilder
            .setFirstPartyHosts(hosts)
            .build()

        // THEN
        hosts.forEach {
            verify(mockDevLogHandler).handleLog(
                Log.ERROR,
                Configuration.ERROR_MALFORMED_HOST_IP_ADDRESS.format(it)
            )
        }
    }

    @Test
    fun `M log error W setFirstPartyHosts() { malformed ip address }`(
        @StringForgery(
            regex = "(([0-9]{3}\\.){3}[0.9]{4})" +
                "|(([0-9]{4,9}\\.)[0.9]{4})" +
                "|(25[6-9]\\.([0-9]{3}\\.){2}[0.9]{3})"
        ) hosts: List<String>
    ) {

        // WHEN
        testedBuilder
            .setFirstPartyHosts(hosts)
            .build()

        // THEN
        hosts.forEach {
            verify(mockDevLogHandler).handleLog(
                Log.ERROR,
                Configuration.ERROR_MALFORMED_HOST_IP_ADDRESS.format(it)
            )
        }
    }

    @Test
    fun `M drop all malformed hosts W setFirstPartyHosts() { malformed hostname }`(
        @StringForgery(
            regex = "(([-+=~><?][a-zA-Z0-9-]*[a-zA-Z0-9])\\.)+([-+=~><?][A-Za-z0-9]*) " +
                "| (([a-zA-Z0-9-]*[a-zA-Z0-9])\\.)+([A-Za-z0-9]*[-+=~><?])"
        ) hosts: List<String>
    ) {

        // WHEN
        val config = testedBuilder
            .setFirstPartyHosts(hosts)
            .build()

        // THEN
        assertThat(config.coreConfig).isEqualTo(
            Configuration.DEFAULT_CORE_CONFIG.copy(firstPartyHosts = emptyList())
        )
        assertThat(config.logsConfig).isEqualTo(Configuration.DEFAULT_LOGS_CONFIG)
        assertThat(config.tracesConfig).isEqualTo(Configuration.DEFAULT_TRACING_CONFIG)
        assertThat(config.crashReportConfig).isEqualTo(Configuration.DEFAULT_CRASH_CONFIG)
        assertThat(config.rumConfig).isEqualTo(Configuration.DEFAULT_RUM_CONFIG)
    }

    @Test
    fun `M drop all malformed ip addresses W setFirstPartyHosts() { malformed ip address }`(
        @StringForgery(
            regex = "(([0-9]{3}\\.){3}[0.9]{4})" +
                "|(([0-9]{4,9}\\.)[0.9]{4})" +
                "|(25[6-9]\\.([0-9]{3}\\.){2}[0.9]{3})"
        ) hosts: List<String>
    ) {

        // WHEN
        val config = testedBuilder
            .setFirstPartyHosts(hosts)
            .build()

        // THEN
        assertThat(config.coreConfig).isEqualTo(
            Configuration.DEFAULT_CORE_CONFIG.copy(firstPartyHosts = emptyList())
        )
        assertThat(config.logsConfig).isEqualTo(Configuration.DEFAULT_LOGS_CONFIG)
        assertThat(config.tracesConfig).isEqualTo(Configuration.DEFAULT_TRACING_CONFIG)
        assertThat(config.crashReportConfig).isEqualTo(Configuration.DEFAULT_CRASH_CONFIG)
        assertThat(config.rumConfig).isEqualTo(Configuration.DEFAULT_RUM_CONFIG)
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
            Configuration.DEFAULT_CORE_CONFIG.copy(firstPartyHosts = hosts.map { URL(it).host })
        )
        assertThat(config.logsConfig).isEqualTo(Configuration.DEFAULT_LOGS_CONFIG)
        assertThat(config.tracesConfig).isEqualTo(Configuration.DEFAULT_TRACING_CONFIG)
        assertThat(config.crashReportConfig).isEqualTo(Configuration.DEFAULT_CRASH_CONFIG)
        assertThat(config.rumConfig).isEqualTo(Configuration.DEFAULT_RUM_CONFIG)
    }

    @Test
    fun `M warn W setFirstPartyHosts() { url }`(
        @StringForgery(
            regex = "(https|http)://([a-z][a-z0-9-]{3,9}\\.){1,4}[a-z][a-z0-9]{2,3}"
        ) hosts: List<String>
    ) {
        // WHEN
        testedBuilder
            .setFirstPartyHosts(hosts)
            .build()

        // THEN
        hosts.forEach {
            verify(mockDevLogHandler).handleLog(
                Log.WARN,
                Configuration.WARNING_USING_URL_FOR_HOST.format(it, URL(it).host)
            )
        }
    }

    @Test
    fun `M warn W setFirstPartyHosts() { malformed url }`(
        @StringForgery(
            regex = "(https|http)://([a-z][a-z0-9-]{3,9}\\.){1,4}[a-z][a-z0-9]{2,3}:-8[0-9]{1}"
        ) hosts: List<String>
    ) {

        // WHEN
        testedBuilder
            .setFirstPartyHosts(hosts)
            .build()

        // THEN
        hosts.forEach {
            verify(mockDevLogHandler).handleLog(
                eq(Log.ERROR),
                eq(Configuration.ERROR_MALFORMED_URL.format(it)),
                any<MalformedURLException>(),
                anyOrNull(),
                anyOrNull(),
                anyOrNull()
            )
        }
    }

    @Test
    fun `M drop all malformed urls W setFirstPartyHosts() { malformed url }`(
        @StringForgery(
            regex = "(https|http)://([a-z][a-z0-9-]{3,9}\\.){1,4}[a-z][a-z0-9]{2,3}:-8[0-9]{1}"
        ) hosts: List<String>
    ) {
        // WHEN
        val config = testedBuilder
            .setFirstPartyHosts(hosts)
            .build()

        // THEN
        assertThat(config.coreConfig).isEqualTo(
            Configuration.DEFAULT_CORE_CONFIG.copy(firstPartyHosts = emptyList())
        )
        assertThat(config.logsConfig).isEqualTo(Configuration.DEFAULT_LOGS_CONFIG)
        assertThat(config.tracesConfig).isEqualTo(Configuration.DEFAULT_TRACING_CONFIG)
        assertThat(config.crashReportConfig).isEqualTo(Configuration.DEFAULT_CRASH_CONFIG)
        assertThat(config.rumConfig).isEqualTo(Configuration.DEFAULT_RUM_CONFIG)
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
    }
}
