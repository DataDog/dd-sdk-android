/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android

import android.os.Build
import com.datadog.android.core.internal.event.NoOpEventMapper
import com.datadog.android.event.EventMapper
import com.datadog.android.log.internal.logger.LogHandler
import com.datadog.android.plugin.DatadogPlugin
import com.datadog.android.plugin.Feature
import com.datadog.android.rum.assertj.ConfigurationRumAssert.Companion.assertThat
import com.datadog.android.rum.internal.domain.event.RumEventMapper
import com.datadog.android.rum.model.ViewEvent
import com.datadog.android.rum.tracking.ViewAttributesProvider
import com.datadog.android.rum.tracking.ViewTrackingStrategy
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.utils.mockDevLogHandler
import com.datadog.tools.unit.annotations.TestTargetApi
import com.datadog.tools.unit.extensions.ApiLevelExtension
import com.nhaarman.mockitokotlin2.mock
import fr.xgouchet.elmyr.annotation.FloatForgery
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.annotation.StringForgeryType
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import java.util.UUID
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

    @StringForgery(type = StringForgeryType.HEXADECIMAL)
    lateinit var fakeClientToken: String

    @StringForgery
    lateinit var fakeEnvName: String

    @Forgery
    lateinit var fakeApplicationId: UUID

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
    fun `𝕄 disable all features by default 𝕎 build()`() {
        // Given
        val builder = Configuration.Builder()

        // When
        val config = builder.build()

        // Then
        assertThat(config.coreConfig).isEqualTo(
            Configuration.Core(
                needsClearTextHttp = false,
                hosts = emptyList()
            )
        )
        assertThat(config.logsConfig).isNull()
        assertThat(config.tracesConfig).isNull()
        assertThat(config.crashReportConfig).isNull()
        assertThat(config.rumConfig).isNull()
    }

    @Test
    fun `𝕄 use sensible defaults 𝕎 build()`() {
        // When
        val config = testedBuilder.build()

        // Then
        assertThat(config.coreConfig).isEqualTo(
            Configuration.Core(
                needsClearTextHttp = false,
                hosts = emptyList()
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
    fun `𝕄 build config with first party hosts 𝕎 setFirstPartyHosts() and build()`(
        @StringForgery(regex = "([a-zA-Z0-9]{3,9}\\.){1,4}[a-z]{3}") hosts: List<String>
    ) {
        // When
        val config = testedBuilder
            .setFirstPartyHosts(hosts)
            .build()

        // Then
        assertThat(config.coreConfig).isEqualTo(
            Configuration.DEFAULT_CORE_CONFIG.copy(hosts = hosts)
        )
        assertThat(config.logsConfig).isEqualTo(Configuration.DEFAULT_LOGS_CONFIG)
        assertThat(config.tracesConfig).isEqualTo(Configuration.DEFAULT_TRACING_CONFIG)
        assertThat(config.crashReportConfig).isEqualTo(Configuration.DEFAULT_CRASH_CONFIG)
        assertThat(config.rumConfig).isEqualTo(Configuration.DEFAULT_RUM_CONFIG)
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
}
