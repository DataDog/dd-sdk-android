/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android

import android.os.Build
import com.datadog.android.plugin.DatadogPlugin
import com.datadog.android.plugin.Feature
import com.datadog.android.rum.assertj.RumConfigAssert
import com.datadog.android.rum.tracking.ActivityViewTrackingStrategy
import com.datadog.android.rum.tracking.ViewAttributesProvider
import com.datadog.android.utils.forge.Configurator
import com.datadog.tools.unit.annotations.TestTargetApi
import com.datadog.tools.unit.extensions.ApiLevelExtension
import com.nhaarman.mockitokotlin2.mock
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.FloatForgery
import fr.xgouchet.elmyr.annotation.Forgery
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
@ForgeConfiguration(Configurator::class)
class DatadogConfigBuilderTest {

    lateinit var fakeClientToken: String
    lateinit var fakeEnvName: String

    @Forgery
    lateinit var fakeApplicationId: UUID

    @BeforeEach
    fun `set up`(forge: Forge) {
        fakeEnvName = forge.anAlphabeticalString()
        fakeClientToken = forge.anHexadecimalString()
    }

    @Test
    fun `builder returns sensible defaults without applicationId`() {
        val config = DatadogConfig.Builder(fakeClientToken, fakeEnvName)
            .build()

        assertThat(config.coreConfig)
            .isEqualTo(
                DatadogConfig.CoreConfig(
                    needsClearTextHttp = false
                )
            )
        assertThat(config.logsConfig)
            .isEqualTo(
                DatadogConfig.LogsConfig(
                    fakeClientToken,
                    DatadogEndpoint.LOGS_US,
                    fakeEnvName
                )
            )
        assertThat(config.tracesConfig)
            .isEqualTo(
                DatadogConfig.TracesConfig(
                    fakeClientToken,
                    DatadogEndpoint.TRACES_US,
                    fakeEnvName
                )
            )
        assertThat(config.crashReportConfig)
            .isEqualTo(
                DatadogConfig.CrashReportsConfig(
                    fakeClientToken,
                    DatadogEndpoint.LOGS_US,
                    fakeEnvName
                )
            )
        assertThat(config.rumConfig)
            .isNull()
    }

    @Test
    fun `builder returns sensible defaults with String applicationId`() {
        val config =
            DatadogConfig.Builder(fakeClientToken, fakeEnvName, fakeApplicationId.toString())
                .build()

        assertThat(config.coreConfig)
            .isEqualTo(
                DatadogConfig.CoreConfig(
                    needsClearTextHttp = false
                )
            )
        assertThat(config.logsConfig)
            .isEqualTo(
                DatadogConfig.LogsConfig(
                    fakeClientToken,
                    DatadogEndpoint.LOGS_US,
                    fakeEnvName
                )
            )
        assertThat(config.tracesConfig)
            .isEqualTo(
                DatadogConfig.TracesConfig(
                    fakeClientToken,
                    DatadogEndpoint.TRACES_US,
                    fakeEnvName
                )
            )
        assertThat(config.crashReportConfig)
            .isEqualTo(
                DatadogConfig.CrashReportsConfig(
                    fakeClientToken,
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
    fun `builder returns sensible defaults with UUID applicationId`() {
        val config = DatadogConfig.Builder(fakeClientToken, fakeEnvName, fakeApplicationId)
            .build()

        assertThat(config.coreConfig)
        assertThat(config.logsConfig)
            .isEqualTo(
                DatadogConfig.LogsConfig(
                    fakeClientToken,
                    DatadogEndpoint.LOGS_US,
                    fakeEnvName
                )
            )
        assertThat(config.tracesConfig)
            .isEqualTo(
                DatadogConfig.TracesConfig(
                    fakeClientToken,
                    DatadogEndpoint.TRACES_US,
                    fakeEnvName
                )
            )
        assertThat(config.crashReportConfig)
            .isEqualTo(
                DatadogConfig.CrashReportsConfig(
                    fakeClientToken,
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
    fun `builder with custom service name`(
        forge: Forge
    ) {
        val serviceName = forge.anAlphabeticalString()
        val config = DatadogConfig.Builder(fakeClientToken, fakeEnvName, fakeApplicationId)
            .setLogsEnabled(true)
            .setTracesEnabled(true)
            .setCrashReportsEnabled(true)
            .setRumEnabled(true)
            .setServiceName(serviceName)
            .build()

        assertThat(config.coreConfig)
            .isEqualTo(
                DatadogConfig.CoreConfig(
                    serviceName = serviceName
                )
            )
    }

    @Test
    fun `builder with custom env name`(
        forge: Forge
    ) {
        val envName = forge.anAlphabeticalString()
        val config = DatadogConfig.Builder(fakeClientToken, fakeEnvName, fakeApplicationId)
            .setLogsEnabled(true)
            .setTracesEnabled(true)
            .setCrashReportsEnabled(true)
            .setRumEnabled(true)
            .setEnvironmentName(envName)
            .build()

        assertThat(config.coreConfig)
            .isEqualTo(
                DatadogConfig.CoreConfig(
                    needsClearTextHttp = false
                )
            )
        assertThat(config.logsConfig)
            .isEqualTo(
                DatadogConfig.LogsConfig(
                    fakeClientToken,
                    DatadogEndpoint.LOGS_US,
                    envName
                )
            )
        assertThat(config.tracesConfig)
            .isEqualTo(
                DatadogConfig.TracesConfig(
                    fakeClientToken,
                    DatadogEndpoint.TRACES_US,
                    envName
                )
            )
        assertThat(config.crashReportConfig)
            .isEqualTo(
                DatadogConfig.CrashReportsConfig(
                    fakeClientToken,
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
    fun `builder with invalid custom env name`(
        forge: Forge
    ) {
        val envName = forge.anAlphabeticalString()
        val config = DatadogConfig.Builder(fakeClientToken, fakeEnvName, fakeApplicationId)
            .setLogsEnabled(true)
            .setTracesEnabled(true)
            .setCrashReportsEnabled(true)
            .setRumEnabled(true)
            .setEnvironmentName("\"'$envName'\"")
            .build()

        assertThat(config.coreConfig)
            .isEqualTo(
                DatadogConfig.CoreConfig(
                    needsClearTextHttp = false
                )
            )
        assertThat(config.logsConfig)
            .isEqualTo(
                DatadogConfig.LogsConfig(
                    fakeClientToken,
                    DatadogEndpoint.LOGS_US,
                    envName
                )
            )
        assertThat(config.tracesConfig)
            .isEqualTo(
                DatadogConfig.TracesConfig(
                    fakeClientToken,
                    DatadogEndpoint.TRACES_US,
                    envName
                )
            )
        assertThat(config.crashReportConfig)
            .isEqualTo(
                DatadogConfig.CrashReportsConfig(
                    fakeClientToken,
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
    fun `builder with all features disabled`() {
        val config = DatadogConfig.Builder(fakeClientToken, fakeEnvName, fakeApplicationId)
            .setLogsEnabled(false)
            .setTracesEnabled(false)
            .setCrashReportsEnabled(false)
            .setRumEnabled(false)
            .build()

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
    fun `builder with US endpoints all features enabled`() {
        val config = DatadogConfig.Builder(fakeClientToken, fakeEnvName, fakeApplicationId)
            .useUSEndpoints()
            .setLogsEnabled(true)
            .setTracesEnabled(true)
            .setCrashReportsEnabled(true)
            .setRumEnabled(true)
            .build()

        assertThat(config.coreConfig)
            .isEqualTo(
                DatadogConfig.CoreConfig(
                    needsClearTextHttp = false
                )
            )
        assertThat(config.logsConfig)
            .isEqualTo(
                DatadogConfig.LogsConfig(
                    fakeClientToken,
                    DatadogEndpoint.LOGS_US,
                    fakeEnvName
                )
            )
        assertThat(config.tracesConfig)
            .isEqualTo(
                DatadogConfig.TracesConfig(
                    fakeClientToken,
                    DatadogEndpoint.TRACES_US,
                    fakeEnvName
                )
            )
        assertThat(config.crashReportConfig)
            .isEqualTo(
                DatadogConfig.CrashReportsConfig(
                    fakeClientToken,
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
    fun `builder with EU endpoints all features enabled`() {
        val config = DatadogConfig.Builder(fakeClientToken, fakeEnvName, fakeApplicationId)
            .useEUEndpoints()
            .setLogsEnabled(true)
            .setTracesEnabled(true)
            .setCrashReportsEnabled(true)
            .setRumEnabled(true)
            .build()

        assertThat(config.coreConfig)
            .isEqualTo(
                DatadogConfig.CoreConfig(
                    needsClearTextHttp = false
                )
            )
        assertThat(config.logsConfig)
            .isEqualTo(
                DatadogConfig.LogsConfig(
                    fakeClientToken,
                    DatadogEndpoint.LOGS_EU,
                    fakeEnvName
                )
            )
        assertThat(config.tracesConfig)
            .isEqualTo(
                DatadogConfig.TracesConfig(
                    fakeClientToken,
                    DatadogEndpoint.TRACES_EU,
                    fakeEnvName
                )
            )
        assertThat(config.crashReportConfig)
            .isEqualTo(
                DatadogConfig.CrashReportsConfig(
                    fakeClientToken,
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
    fun `builder with custom secure endpoints all features enabled`(
        forge: Forge
    ) {
        val logsUrl = forge.aStringMatching("https://[a-z]+\\.com")
        val tracesUrl = forge.aStringMatching("https://[a-z]+\\.com")
        val crashReportsUrl = forge.aStringMatching("https://[a-z]+\\.com")
        val rumUrl = forge.aStringMatching("https://[a-z]+\\.com")
        val config = DatadogConfig.Builder(fakeClientToken, fakeEnvName, fakeApplicationId)
            .useCustomLogsEndpoint(logsUrl)
            .useCustomTracesEndpoint(tracesUrl)
            .useCustomCrashReportsEndpoint(crashReportsUrl)
            .useCustomRumEndpoint(rumUrl)
            .setLogsEnabled(true)
            .setTracesEnabled(true)
            .setCrashReportsEnabled(true)
            .setRumEnabled(true)
            .build()

        assertThat(config.coreConfig)
            .isEqualTo(
                DatadogConfig.CoreConfig(
                    needsClearTextHttp = false
                )
            )
        assertThat(config.logsConfig)
            .isEqualTo(
                DatadogConfig.LogsConfig(
                    fakeClientToken,
                    logsUrl,
                    fakeEnvName
                )
            )
        assertThat(config.tracesConfig)
            .isEqualTo(
                DatadogConfig.TracesConfig(
                    fakeClientToken,
                    tracesUrl,
                    fakeEnvName
                )
            )
        assertThat(config.crashReportConfig)
            .isEqualTo(
                DatadogConfig.CrashReportsConfig(
                    fakeClientToken,
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
    fun `builder with custom cleartext endpoints all features enabled`(
        forge: Forge
    ) {
        val logsUrl = forge.aStringMatching("http://[a-z]+\\.com")
        val tracesUrl = forge.aStringMatching("http://[a-z]+\\.com")
        val crashReportsUrl = forge.aStringMatching("http://[a-z]+\\.com")
        val rumUrl = forge.aStringMatching("http://[a-z]+\\.com")
        val config = DatadogConfig.Builder(fakeClientToken, fakeEnvName, fakeApplicationId)
            .useCustomLogsEndpoint(logsUrl)
            .useCustomTracesEndpoint(tracesUrl)
            .useCustomCrashReportsEndpoint(crashReportsUrl)
            .useCustomRumEndpoint(rumUrl)
            .setLogsEnabled(true)
            .setTracesEnabled(true)
            .setCrashReportsEnabled(true)
            .setRumEnabled(true)
            .build()

        assertThat(config.coreConfig)
            .isEqualTo(
                DatadogConfig.CoreConfig(
                    needsClearTextHttp = true
                )
            )
        assertThat(config.logsConfig)
            .isEqualTo(
                DatadogConfig.LogsConfig(
                    fakeClientToken,
                    logsUrl,
                    fakeEnvName
                )
            )
        assertThat(config.tracesConfig)
            .isEqualTo(
                DatadogConfig.TracesConfig(
                    fakeClientToken,
                    tracesUrl,
                    fakeEnvName
                )
            )
        assertThat(config.crashReportConfig)
            .isEqualTo(
                DatadogConfig.CrashReportsConfig(
                    fakeClientToken,
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
    fun `builder with track gestures enabled`(forge: Forge) {
        val rumUrl = forge.aStringMatching("http://[a-z]+\\.com")
        val touchTargetExtraAttributesProviders =
            Array<ViewAttributesProvider>(forge.anInt(min = 0, max = 10)) {
                mock()
            }
        val config = DatadogConfig.Builder(fakeClientToken, fakeEnvName, fakeApplicationId)
            .useCustomRumEndpoint(rumUrl)
            .trackInteractions(touchTargetExtraAttributesProviders)
            .build()
        val rumConfig: DatadogConfig.RumConfig? = config.rumConfig
        assertThat(rumConfig).isNotNull()
        RumConfigAssert.assertThat(rumConfig!!)
            .hasClientToken(fakeClientToken)
            .hasApplicationId(fakeApplicationId)
            .hasEndpointUrl(rumUrl)
            .hasEnvName(fakeEnvName)
            .hasGesturesTrackingStrategy(touchTargetExtraAttributesProviders)
            .doesNotHaveViewTrackingStrategy()
    }

    @TestTargetApi(value = Build.VERSION_CODES.Q)
    @Test
    fun `builder with track gestures enabled for Android Q`(forge: Forge) {
        val rumUrl = forge.aStringMatching("http://[a-z]+\\.com")
        val touchTargetExtraAttributesProviders =
            Array<ViewAttributesProvider>(forge.anInt(min = 0, max = 10)) {
                mock()
            }
        val config = DatadogConfig.Builder(fakeClientToken, fakeEnvName, fakeApplicationId)
            .useCustomRumEndpoint(rumUrl)
            .trackInteractions(touchTargetExtraAttributesProviders)
            .build()
        val rumConfig: DatadogConfig.RumConfig? = config.rumConfig
        assertThat(rumConfig).isNotNull()
        RumConfigAssert.assertThat(rumConfig!!)
            .hasClientToken(fakeClientToken)
            .hasApplicationId(fakeApplicationId)
            .hasEndpointUrl(rumUrl)
            .hasEnvName(fakeEnvName)
            .hasGesturesTrackingStrategyApi29(touchTargetExtraAttributesProviders)
            .doesNotHaveViewTrackingStrategy()
    }

    @Test
    fun `builder with track activities as views tracking strategy`(forge: Forge) {
        val rumUrl = forge.aStringMatching("http://[a-z]+\\.com")
        val strategy =
            ActivityViewTrackingStrategy(true)
        val config = DatadogConfig.Builder(fakeClientToken, fakeEnvName, fakeApplicationId)
            .useCustomRumEndpoint(rumUrl)
            .useViewTrackingStrategy(strategy)
            .build()
        val rumConfig: DatadogConfig.RumConfig? = config.rumConfig
        assertThat(rumConfig).isNotNull()
        RumConfigAssert.assertThat(rumConfig!!)
            .hasClientToken(fakeClientToken)
            .hasApplicationId(fakeApplicationId)
            .hasEndpointUrl(rumUrl)
            .hasEnvName(fakeEnvName)
            .doesNotHaveGesturesTrackingStrategy()
            .hasViewTrackingStrategy(strategy)
    }

    @Test
    fun `builder with RUM sampling`(
        @FloatForgery(min = 0f, max = 100f) sampling: Float
    ) {
        val config = DatadogConfig.Builder(fakeClientToken, fakeEnvName, fakeApplicationId)
            .setRumEnabled(true)
            .sampleRumSessions(sampling)
            .build()

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
    fun `adding a plugin will register it to the specific feature`(forge: Forge) {
        val logsPlugin: DatadogPlugin = mock()
        val tracesPlugin: DatadogPlugin = mock()
        val rumPlugin: DatadogPlugin = mock()
        val crashPlugin: DatadogPlugin = mock()
        val config = DatadogConfig.Builder(fakeClientToken, fakeEnvName, fakeApplicationId)
            .setLogsEnabled(true)
            .setTracesEnabled(true)
            .setCrashReportsEnabled(true)
            .setRumEnabled(true)
            .addPlugin(logsPlugin, Feature.LOG)
            .addPlugin(tracesPlugin, Feature.TRACE)
            .addPlugin(rumPlugin, Feature.RUM)
            .addPlugin(crashPlugin, Feature.CRASH)
            .build()

        assertThat(config.logsConfig)
            .isEqualTo(
                DatadogConfig.LogsConfig(
                    fakeClientToken,
                    DatadogEndpoint.LOGS_US,
                    fakeEnvName,
                    plugins = listOf(logsPlugin)
                )
            )
        assertThat(config.tracesConfig)
            .isEqualTo(
                DatadogConfig.TracesConfig(
                    fakeClientToken,
                    DatadogEndpoint.TRACES_US,
                    fakeEnvName,
                    plugins = listOf(tracesPlugin)
                )
            )
        assertThat(config.crashReportConfig)
            .isEqualTo(
                DatadogConfig.CrashReportsConfig(
                    fakeClientToken,
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
