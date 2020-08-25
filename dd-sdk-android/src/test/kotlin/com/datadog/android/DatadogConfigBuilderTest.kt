/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android

import android.os.Build
import com.datadog.android.plugin.DatadogPlugin
import com.datadog.android.plugin.Feature
import com.datadog.android.rum.assertj.RumConfigAssert.Companion.assertThat
import com.datadog.android.rum.tracking.ActivityViewTrackingStrategy
import com.datadog.android.rum.tracking.ViewAttributesProvider
import com.datadog.android.utils.forge.Configurator
import com.datadog.tools.unit.annotations.TestTargetApi
import com.datadog.tools.unit.extensions.ApiLevelExtension
import com.nhaarman.mockitokotlin2.mock
import fr.xgouchet.elmyr.annotation.FloatForgery
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.annotation.RegexForgery
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
class DatadogConfigBuilderTest {

    lateinit var testedBuilder: DatadogConfig.Builder

    @StringForgery(StringForgeryType.HEXADECIMAL)
    lateinit var fakeClientToken: String

    @StringForgery(StringForgeryType.ALPHABETICAL)
    lateinit var fakeEnvName: String

    @Forgery
    lateinit var fakeApplicationId: UUID

    @BeforeEach
    fun `set up`() {
        testedBuilder = DatadogConfig.Builder(fakeClientToken, fakeEnvName, fakeApplicationId)
    }

    @Test
    fun `𝕄 use sensible defaults 𝕎 build() {no applicationId}`() {
        // Given
        val builder = DatadogConfig.Builder(fakeClientToken, fakeEnvName)

        // When
        val config = builder.build()

        // Then
        assertThat(config.coreConfig)
            .isEqualTo(
                DatadogConfig.CoreConfig(
                    needsClearTextHttp = false
                )
            )
        assertThat(config.logsConfig)
            .isEqualTo(
                DatadogConfig.FeatureConfig(
                    fakeClientToken,
                    UUID(0, 0),
                    DatadogEndpoint.LOGS_US,
                    fakeEnvName
                )
            )
        assertThat(config.tracesConfig)
            .isEqualTo(
                DatadogConfig.FeatureConfig(
                    fakeClientToken,
                    UUID(0, 0),
                    DatadogEndpoint.TRACES_US,
                    fakeEnvName
                )
            )
        assertThat(config.crashReportConfig)
            .isEqualTo(
                DatadogConfig.FeatureConfig(
                    fakeClientToken,
                    UUID(0, 0),
                    DatadogEndpoint.LOGS_US,
                    fakeEnvName
                )
            )
        assertThat(config.rumConfig)
            .isNull()
    }

    @Test
    fun `𝕄 use sensible defaults 𝕎 build() {String applicationId}`() {
        // Given
        val builder = DatadogConfig.Builder(
            fakeClientToken,
            fakeEnvName,
            fakeApplicationId.toString()
        )

        // When
        val config = builder.build()

        // Then
        assertThat(config.coreConfig)
            .isEqualTo(
                DatadogConfig.CoreConfig(
                    needsClearTextHttp = false
                )
            )
        assertThat(config.logsConfig)
            .isEqualTo(
                DatadogConfig.FeatureConfig(
                    fakeClientToken,
                    fakeApplicationId,
                    DatadogEndpoint.LOGS_US,
                    fakeEnvName
                )
            )
        assertThat(config.tracesConfig)
            .isEqualTo(
                DatadogConfig.FeatureConfig(
                    fakeClientToken,
                    fakeApplicationId,
                    DatadogEndpoint.TRACES_US,
                    fakeEnvName
                )
            )
        assertThat(config.crashReportConfig)
            .isEqualTo(
                DatadogConfig.FeatureConfig(
                    fakeClientToken,
                    fakeApplicationId,
                    DatadogEndpoint.LOGS_US,
                    fakeEnvName
                )
            )
        assertThat(config.rumConfig)
            .isEqualTo(
                DatadogConfig.RumConfig(
                    fakeClientToken,
                    fakeApplicationId,
                    DatadogEndpoint.RUM_US,
                    fakeEnvName
                )
            )
    }

    @Test
    fun `𝕄 use sensible defaults 𝕎 build() {UUID applicationId}`() {
        // When
        val config = testedBuilder.build()

        // Then
        assertThat(config.coreConfig)
        assertThat(config.logsConfig)
            .isEqualTo(
                DatadogConfig.FeatureConfig(
                    fakeClientToken,
                    fakeApplicationId,
                    DatadogEndpoint.LOGS_US,
                    fakeEnvName
                )
            )
        assertThat(config.tracesConfig)
            .isEqualTo(
                DatadogConfig.FeatureConfig(
                    fakeClientToken,
                    fakeApplicationId,
                    DatadogEndpoint.TRACES_US,
                    fakeEnvName
                )
            )
        assertThat(config.crashReportConfig)
            .isEqualTo(
                DatadogConfig.FeatureConfig(
                    fakeClientToken,
                    fakeApplicationId,
                    DatadogEndpoint.LOGS_US,
                    fakeEnvName
                )
            )
        assertThat(config.rumConfig)
            .isEqualTo(
                DatadogConfig.RumConfig(
                    fakeClientToken,
                    fakeApplicationId,
                    DatadogEndpoint.RUM_US,
                    fakeEnvName
                )
            )
    }

    @Test
    fun `𝕄 build config with serviceName 𝕎 setServiceName() and build()`(
        @StringForgery(StringForgeryType.ALPHABETICAL) serviceName: String
    ) {
        // When
        val config = testedBuilder
            .setLogsEnabled(true)
            .setTracesEnabled(true)
            .setCrashReportsEnabled(true)
            .setRumEnabled(true)
            .setServiceName(serviceName)
            .build()

        // Then
        assertThat(config.coreConfig)
            .isEqualTo(
                DatadogConfig.CoreConfig(
                    serviceName = serviceName
                )
            )
    }

    @Test
    fun `𝕄 build config with envName 𝕎 setEnvironmentName() and build()`(
        @StringForgery(StringForgeryType.ALPHABETICAL) envName: String
    ) {
        // When
        val config = testedBuilder
            .setLogsEnabled(true)
            .setTracesEnabled(true)
            .setCrashReportsEnabled(true)
            .setRumEnabled(true)
            .setEnvironmentName(envName)
            .build()

        // Then
        assertThat(config.coreConfig)
            .isEqualTo(
                DatadogConfig.CoreConfig(
                    needsClearTextHttp = false
                )
            )
        assertThat(config.logsConfig)
            .isEqualTo(
                DatadogConfig.FeatureConfig(
                    fakeClientToken,
                    fakeApplicationId,
                    DatadogEndpoint.LOGS_US,
                    envName
                )
            )
        assertThat(config.tracesConfig)
            .isEqualTo(
                DatadogConfig.FeatureConfig(
                    fakeClientToken,
                    fakeApplicationId,
                    DatadogEndpoint.TRACES_US,
                    envName
                )
            )
        assertThat(config.crashReportConfig)
            .isEqualTo(
                DatadogConfig.FeatureConfig(
                    fakeClientToken,
                    fakeApplicationId,
                    DatadogEndpoint.LOGS_US,
                    envName
                )
            )

        assertThat(config.rumConfig)
            .isEqualTo(
                DatadogConfig.RumConfig(
                    fakeClientToken,
                    fakeApplicationId,
                    DatadogEndpoint.RUM_US,
                    envName = envName
                )
            )
    }

    @Test
    fun `𝕄 build config with envName 𝕎 setEnvironmentName() and build() {invalid envName}`(
        @StringForgery(StringForgeryType.ALPHABETICAL) envName: String
    ) {
        // When
        val config = testedBuilder
            .setLogsEnabled(true)
            .setTracesEnabled(true)
            .setCrashReportsEnabled(true)
            .setRumEnabled(true)
            .setEnvironmentName("\"'$envName'\"")
            .build()

        // Then
        assertThat(config.coreConfig)
            .isEqualTo(
                DatadogConfig.CoreConfig(
                    needsClearTextHttp = false
                )
            )
        assertThat(config.logsConfig)
            .isEqualTo(
                DatadogConfig.FeatureConfig(
                    fakeClientToken,
                    fakeApplicationId,
                    DatadogEndpoint.LOGS_US,
                    envName
                )
            )
        assertThat(config.tracesConfig)
            .isEqualTo(
                DatadogConfig.FeatureConfig(
                    fakeClientToken,
                    fakeApplicationId,
                    DatadogEndpoint.TRACES_US,
                    envName
                )
            )
        assertThat(config.crashReportConfig)
            .isEqualTo(
                DatadogConfig.FeatureConfig(
                    fakeClientToken,
                    fakeApplicationId,
                    DatadogEndpoint.LOGS_US,
                    envName
                )
            )

        assertThat(config.rumConfig)
            .isEqualTo(
                DatadogConfig.RumConfig(
                    fakeClientToken,
                    fakeApplicationId,
                    DatadogEndpoint.RUM_US,
                    envName = envName
                )
            )
    }

    @Test
    fun `𝕄 build config with all features disabled 𝕎 setXXXEnabled(false) and build()`() {
        // When
        val config = testedBuilder
            .setLogsEnabled(false)
            .setTracesEnabled(false)
            .setCrashReportsEnabled(false)
            .setRumEnabled(false)
            .build()

        // Then
        assertThat(config.coreConfig)
            .isEqualTo(
                DatadogConfig.CoreConfig(
                    needsClearTextHttp = false
                )
            )
        assertThat(config.logsConfig).isNull()
        assertThat(config.tracesConfig).isNull()
        assertThat(config.crashReportConfig).isNull()
        assertThat(config.rumConfig).isNull()
    }

    @Test
    fun `𝕄 build config with US endpoints 𝕎 useUSEndpoints() and build()`() {
        // When
        val config = testedBuilder
            .useUSEndpoints()
            .setLogsEnabled(true)
            .setTracesEnabled(true)
            .setCrashReportsEnabled(true)
            .setRumEnabled(true)
            .build()

        // Then
        assertThat(config.coreConfig)
            .isEqualTo(
                DatadogConfig.CoreConfig(
                    needsClearTextHttp = false
                )
            )
        assertThat(config.logsConfig)
            .isEqualTo(
                DatadogConfig.FeatureConfig(
                    fakeClientToken,
                    fakeApplicationId,
                    DatadogEndpoint.LOGS_US,
                    fakeEnvName
                )
            )
        assertThat(config.tracesConfig)
            .isEqualTo(
                DatadogConfig.FeatureConfig(
                    fakeClientToken,
                    fakeApplicationId,
                    DatadogEndpoint.TRACES_US,
                    fakeEnvName
                )
            )
        assertThat(config.crashReportConfig)
            .isEqualTo(
                DatadogConfig.FeatureConfig(
                    fakeClientToken,
                    fakeApplicationId,
                    DatadogEndpoint.LOGS_US,
                    fakeEnvName
                )
            )

        assertThat(config.rumConfig)
            .isEqualTo(
                DatadogConfig.RumConfig(
                    fakeClientToken,
                    fakeApplicationId,
                    DatadogEndpoint.RUM_US,
                    fakeEnvName
                )
            )
    }

    @Test
    fun `𝕄 build config with EU endpoints 𝕎 useEUEndpoints() and build()`() {
        // When
        val config = testedBuilder
            .useEUEndpoints()
            .setLogsEnabled(true)
            .setTracesEnabled(true)
            .setCrashReportsEnabled(true)
            .setRumEnabled(true)
            .build()

        // Then
        assertThat(config.coreConfig)
            .isEqualTo(
                DatadogConfig.CoreConfig(
                    needsClearTextHttp = false
                )
            )
        assertThat(config.logsConfig)
            .isEqualTo(
                DatadogConfig.FeatureConfig(
                    fakeClientToken,
                    fakeApplicationId,
                    DatadogEndpoint.LOGS_EU,
                    fakeEnvName
                )
            )
        assertThat(config.tracesConfig)
            .isEqualTo(
                DatadogConfig.FeatureConfig(
                    fakeClientToken,
                    fakeApplicationId,
                    DatadogEndpoint.TRACES_EU,
                    fakeEnvName
                )
            )
        assertThat(config.crashReportConfig)
            .isEqualTo(
                DatadogConfig.FeatureConfig(
                    fakeClientToken,
                    fakeApplicationId,
                    DatadogEndpoint.LOGS_EU,
                    fakeEnvName
                )
            )
        assertThat(config.rumConfig)
            .isEqualTo(
                DatadogConfig.RumConfig(
                    fakeClientToken,
                    fakeApplicationId,
                    DatadogEndpoint.RUM_EU,
                    fakeEnvName
                )
            )
    }

    @Test
    fun `𝕄 build config with GOV endpoints 𝕎 useGovEndpoints() and build()`() {
        // When
        val config = testedBuilder
            .useGovEndpoints()
            .setLogsEnabled(true)
            .setTracesEnabled(true)
            .setCrashReportsEnabled(true)
            .setRumEnabled(true)
            .build()

        // Then
        assertThat(config.coreConfig)
            .isEqualTo(
                DatadogConfig.CoreConfig(
                    needsClearTextHttp = false
                )
            )
        assertThat(config.logsConfig)
            .isEqualTo(
                DatadogConfig.FeatureConfig(
                    fakeClientToken,
                    fakeApplicationId,
                    DatadogEndpoint.LOGS_GOV,
                    fakeEnvName
                )
            )
        assertThat(config.tracesConfig)
            .isEqualTo(
                DatadogConfig.FeatureConfig(
                    fakeClientToken,
                    fakeApplicationId,
                    DatadogEndpoint.TRACES_GOV,
                    fakeEnvName
                )
            )
        assertThat(config.crashReportConfig)
            .isEqualTo(
                DatadogConfig.FeatureConfig(
                    fakeClientToken,
                    fakeApplicationId,
                    DatadogEndpoint.LOGS_GOV,
                    fakeEnvName
                )
            )
        assertThat(config.rumConfig)
            .isEqualTo(
                DatadogConfig.RumConfig(
                    fakeClientToken,
                    fakeApplicationId,
                    DatadogEndpoint.RUM_GOV,
                    fakeEnvName
                )
            )
    }

    @Test
    fun `𝕄 build config with custom endpoints 𝕎 useCustomXXXEndpoint() and build()`(
        @RegexForgery("https://[a-z]+\\.com") logsUrl: String,
        @RegexForgery("https://[a-z]+\\.com") tracesUrl: String,
        @RegexForgery("https://[a-z]+\\.com") crashReportsUrl: String,
        @RegexForgery("https://[a-z]+\\.com") rumUrl: String
    ) {
        // When
        val config = testedBuilder
            .useCustomLogsEndpoint(logsUrl)
            .useCustomTracesEndpoint(tracesUrl)
            .useCustomCrashReportsEndpoint(crashReportsUrl)
            .useCustomRumEndpoint(rumUrl)
            .setLogsEnabled(true)
            .setTracesEnabled(true)
            .setCrashReportsEnabled(true)
            .setRumEnabled(true)
            .build()

        // Then
        assertThat(config.coreConfig)
            .isEqualTo(
                DatadogConfig.CoreConfig(
                    needsClearTextHttp = false
                )
            )
        assertThat(config.logsConfig)
            .isEqualTo(
                DatadogConfig.FeatureConfig(
                    fakeClientToken,
                    fakeApplicationId,
                    logsUrl,
                    fakeEnvName
                )
            )
        assertThat(config.tracesConfig)
            .isEqualTo(
                DatadogConfig.FeatureConfig(
                    fakeClientToken,
                    fakeApplicationId,
                    tracesUrl,
                    fakeEnvName
                )
            )
        assertThat(config.crashReportConfig)
            .isEqualTo(
                DatadogConfig.FeatureConfig(
                    fakeClientToken,
                    fakeApplicationId,
                    crashReportsUrl,
                    fakeEnvName
                )
            )

        assertThat(config.rumConfig)
            .isEqualTo(
                DatadogConfig.RumConfig(
                    fakeClientToken,
                    fakeApplicationId,
                    rumUrl,
                    fakeEnvName
                )
            )
    }

    @Test
    fun `𝕄 build config with custom cleartext endpoints 𝕎 useCustomXXXEndpoint() and build()`(
        @RegexForgery("http://[a-z]+\\.com") logsUrl: String,
        @RegexForgery("http://[a-z]+\\.com") tracesUrl: String,
        @RegexForgery("http://[a-z]+\\.com") crashReportsUrl: String,
        @RegexForgery("http://[a-z]+\\.com") rumUrl: String
    ) {
        // When
        val config = testedBuilder
            .useCustomLogsEndpoint(logsUrl)
            .useCustomTracesEndpoint(tracesUrl)
            .useCustomCrashReportsEndpoint(crashReportsUrl)
            .useCustomRumEndpoint(rumUrl)
            .setLogsEnabled(true)
            .setTracesEnabled(true)
            .setCrashReportsEnabled(true)
            .setRumEnabled(true)
            .build()

        // Then
        assertThat(config.coreConfig)
            .isEqualTo(
                DatadogConfig.CoreConfig(
                    needsClearTextHttp = true
                )
            )
        assertThat(config.logsConfig)
            .isEqualTo(
                DatadogConfig.FeatureConfig(
                    fakeClientToken,
                    fakeApplicationId,
                    logsUrl,
                    fakeEnvName
                )
            )
        assertThat(config.tracesConfig)
            .isEqualTo(
                DatadogConfig.FeatureConfig(
                    fakeClientToken,
                    fakeApplicationId,
                    tracesUrl,
                    fakeEnvName
                )
            )
        assertThat(config.crashReportConfig)
            .isEqualTo(
                DatadogConfig.FeatureConfig(
                    fakeClientToken,
                    fakeApplicationId,
                    crashReportsUrl,
                    fakeEnvName
                )
            )
        assertThat(config.rumConfig)
            .isEqualTo(
                DatadogConfig.RumConfig(
                    fakeClientToken,
                    fakeApplicationId,
                    rumUrl,
                    fakeEnvName
                )
            )
    }

    @Test
    fun `𝕄 build RUM config with gestures enabled 𝕎 trackInteractions() and build()`(
        @RegexForgery("http://[a-z]+\\.com") rumUrl: String,
        @IntForgery(0, 10) attributesCount: Int
    ) {
        // Given
        val touchTargetExtraAttributesProviders = Array<ViewAttributesProvider>(attributesCount) {
            mock()
        }

        // When
        val config = testedBuilder
            .useCustomRumEndpoint(rumUrl)
            .trackInteractions(touchTargetExtraAttributesProviders)
            .build()

        // Then
        val rumConfig: DatadogConfig.RumConfig? = config.rumConfig
        assertThat(rumConfig).isNotNull()
        assertThat(rumConfig!!)
            .hasClientToken(fakeClientToken)
            .hasApplicationId(fakeApplicationId)
            .hasEndpointUrl(rumUrl)
            .hasEnvName(fakeEnvName)
            .hasGesturesTrackingStrategy(touchTargetExtraAttributesProviders)
            .doesNotHaveViewTrackingStrategy()
    }

    @TestTargetApi(value = Build.VERSION_CODES.Q)
    @Test
    fun `𝕄 build RUM config with gestures enabled 𝕎 trackInteractions() and build() {Android Q}`(
        @RegexForgery("http://[a-z]+\\.com") rumUrl: String,
        @IntForgery(0, 10) attributesCount: Int
    ) {
        // Given
        val touchTargetExtraAttributesProviders = Array<ViewAttributesProvider>(attributesCount) {
            mock()
        }

        // When
        val config = testedBuilder
            .useCustomRumEndpoint(rumUrl)
            .trackInteractions(touchTargetExtraAttributesProviders)
            .build()
        val rumConfig: DatadogConfig.RumConfig? = config.rumConfig
        assertThat(rumConfig).isNotNull()
        assertThat(rumConfig!!)
            .hasClientToken(fakeClientToken)
            .hasApplicationId(fakeApplicationId)
            .hasEndpointUrl(rumUrl)
            .hasEnvName(fakeEnvName)
            .hasGesturesTrackingStrategyApi29(touchTargetExtraAttributesProviders)
            .doesNotHaveViewTrackingStrategy()
    }

    @Test
    fun `𝕄 build RUM config with view strategy enabled 𝕎 useViewTrackingStrategy() and build()`(
        @RegexForgery("http://[a-z]+\\.com") rumUrl: String
    ) {
        // Given
        val strategy = ActivityViewTrackingStrategy(true)

        // When
        val config = testedBuilder
            .useCustomRumEndpoint(rumUrl)
            .useViewTrackingStrategy(strategy)
            .build()

        // Then
        val rumConfig: DatadogConfig.RumConfig? = config.rumConfig
        assertThat(rumConfig).isNotNull()
        assertThat(rumConfig!!)
            .hasClientToken(fakeClientToken)
            .hasApplicationId(fakeApplicationId)
            .hasEndpointUrl(rumUrl)
            .hasEnvName(fakeEnvName)
            .doesNotHaveGesturesTrackingStrategy()
            .hasViewTrackingStrategy(strategy)
    }

    @Test
    fun `𝕄 build RUM config with sampling rate 𝕎 sampleRumSessions() and build()`(
        @FloatForgery(min = 0f, max = 100f) sampling: Float
    ) {
        // When
        val config = testedBuilder
            .setRumEnabled(true)
            .sampleRumSessions(sampling)
            .build()

        // Then
        assertThat(config.rumConfig)
            .isEqualTo(
                DatadogConfig.RumConfig(
                    fakeClientToken,
                    fakeApplicationId,
                    DatadogEndpoint.RUM_US,
                    fakeEnvName,
                    samplingRate = sampling
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
            .setLogsEnabled(true)
            .setTracesEnabled(true)
            .setCrashReportsEnabled(true)
            .setRumEnabled(true)
            .addPlugin(logsPlugin, Feature.LOG)
            .addPlugin(tracesPlugin, Feature.TRACE)
            .addPlugin(rumPlugin, Feature.RUM)
            .addPlugin(crashPlugin, Feature.CRASH)
            .build()

        // Then
        assertThat(config.logsConfig)
            .isEqualTo(
                DatadogConfig.FeatureConfig(
                    fakeClientToken,
                    fakeApplicationId,
                    DatadogEndpoint.LOGS_US,
                    fakeEnvName,
                    plugins = listOf(logsPlugin)
                )
            )
        assertThat(config.tracesConfig)
            .isEqualTo(
                DatadogConfig.FeatureConfig(
                    fakeClientToken,
                    fakeApplicationId,
                    DatadogEndpoint.TRACES_US,
                    fakeEnvName,
                    plugins = listOf(tracesPlugin)
                )
            )
        assertThat(config.crashReportConfig)
            .isEqualTo(
                DatadogConfig.FeatureConfig(
                    fakeClientToken,
                    fakeApplicationId,
                    DatadogEndpoint.LOGS_US,
                    fakeEnvName,
                    plugins = listOf(crashPlugin)

                )
            )
        assertThat(config.rumConfig)
            .isEqualTo(
                DatadogConfig.RumConfig(
                    fakeClientToken,
                    fakeApplicationId,
                    DatadogEndpoint.RUM_US,
                    fakeEnvName,
                    plugins = listOf(rumPlugin)

                )
            )
    }
}
