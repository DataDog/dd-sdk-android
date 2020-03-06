/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2020 Datadog, Inc.
 */

package com.datadog.android

import com.datadog.android.rum.ActivityViewTrackingStrategy
import com.datadog.android.rum.assertj.RumConfigAssert
import com.datadog.android.utils.forge.Configurator
import fr.xgouchet.elmyr.Forge
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
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings()
@ForgeConfiguration(Configurator::class)
class DatadogConfigBuilderTest {

    lateinit var fakeClientToken: String

    @Forgery
    lateinit var fakeApplicationId: UUID

    @BeforeEach
    fun `set up`(forge: Forge) {
        fakeClientToken = forge.anHexadecimalString()
    }

    @Test
    fun `builder returns sensible defaults without applicationId`() {
        val config = DatadogConfig.Builder(fakeClientToken)
            .build()

        assertThat(config.needsClearTextHttp).isFalse()
        assertThat(config.logsConfig)
            .isEqualTo(
                DatadogConfig.FeatureConfig(
                    fakeClientToken,
                    UUID(0, 0),
                    DatadogEndpoint.LOGS_US,
                    DatadogConfig.DEFAULT_SERVICE_NAME,
                    DatadogConfig.DEFAULT_ENV_NAME
                )
            )
        assertThat(config.tracesConfig)
            .isEqualTo(
                DatadogConfig.FeatureConfig(
                    fakeClientToken,
                    UUID(0, 0),
                    DatadogEndpoint.TRACES_US,
                    DatadogConfig.DEFAULT_SERVICE_NAME,
                    DatadogConfig.DEFAULT_ENV_NAME
                )
            )
        assertThat(config.crashReportConfig)
            .isEqualTo(
                DatadogConfig.FeatureConfig(
                    fakeClientToken,
                    UUID(0, 0),
                    DatadogEndpoint.LOGS_US,
                    DatadogConfig.DEFAULT_SERVICE_NAME,
                    DatadogConfig.DEFAULT_ENV_NAME
                )
            )
        assertThat(config.rumConfig)
            .isNull()
    }

    @Test
    fun `builder returns sensible defaults with String applicationId`() {
        val config = DatadogConfig.Builder(fakeClientToken, fakeApplicationId.toString())
            .build()

        assertThat(config.needsClearTextHttp).isFalse()
        assertThat(config.logsConfig)
            .isEqualTo(
                DatadogConfig.FeatureConfig(
                    fakeClientToken,
                    fakeApplicationId,
                    DatadogEndpoint.LOGS_US,
                    DatadogConfig.DEFAULT_SERVICE_NAME,
                    DatadogConfig.DEFAULT_ENV_NAME
                )
            )
        assertThat(config.tracesConfig)
            .isEqualTo(
                DatadogConfig.FeatureConfig(
                    fakeClientToken,
                    fakeApplicationId,
                    DatadogEndpoint.TRACES_US,
                    DatadogConfig.DEFAULT_SERVICE_NAME,
                    DatadogConfig.DEFAULT_ENV_NAME
                )
            )
        assertThat(config.crashReportConfig)
            .isEqualTo(
                DatadogConfig.FeatureConfig(
                    fakeClientToken,
                    fakeApplicationId,
                    DatadogEndpoint.LOGS_US,
                    DatadogConfig.DEFAULT_SERVICE_NAME,
                    DatadogConfig.DEFAULT_ENV_NAME
                )
            )
        assertThat(config.rumConfig)
            .isEqualTo(
                DatadogConfig.RumConfig(
                    fakeClientToken,
                    fakeApplicationId,
                    DatadogEndpoint.RUM_US,
                    DatadogConfig.DEFAULT_SERVICE_NAME,
                    DatadogConfig.DEFAULT_ENV_NAME
                )
            )
    }

    @Test
    fun `builder returns sensible defaults with UUID applicationId`() {
        val config = DatadogConfig.Builder(fakeClientToken, fakeApplicationId)
            .build()

        assertThat(config.needsClearTextHttp).isFalse()
        assertThat(config.logsConfig)
            .isEqualTo(
                DatadogConfig.FeatureConfig(
                    fakeClientToken,
                    fakeApplicationId,
                    DatadogEndpoint.LOGS_US,
                    DatadogConfig.DEFAULT_SERVICE_NAME,
                    DatadogConfig.DEFAULT_ENV_NAME
                )
            )
        assertThat(config.tracesConfig)
            .isEqualTo(
                DatadogConfig.FeatureConfig(
                    fakeClientToken,
                    fakeApplicationId,
                    DatadogEndpoint.TRACES_US,
                    DatadogConfig.DEFAULT_SERVICE_NAME,
                    DatadogConfig.DEFAULT_ENV_NAME
                )
            )
        assertThat(config.crashReportConfig)
            .isEqualTo(
                DatadogConfig.FeatureConfig(
                    fakeClientToken,
                    fakeApplicationId,
                    DatadogEndpoint.LOGS_US,
                    DatadogConfig.DEFAULT_SERVICE_NAME,
                    DatadogConfig.DEFAULT_ENV_NAME
                )
            )
        assertThat(config.rumConfig)
            .isEqualTo(
                DatadogConfig.RumConfig(
                    fakeClientToken,
                    fakeApplicationId,
                    DatadogEndpoint.RUM_US,
                    DatadogConfig.DEFAULT_SERVICE_NAME,
                    DatadogConfig.DEFAULT_ENV_NAME
                )
            )
    }

    @Test
    fun `builder with custom service name`(
        forge: Forge
    ) {
        val serviceName = forge.anAlphabeticalString()
        val config = DatadogConfig.Builder(fakeClientToken, fakeApplicationId)
            .setLogsEnabled(true)
            .setTracesEnabled(true)
            .setCrashReportsEnabled(true)
            .setServiceName(serviceName)
            .build()

        assertThat(config.needsClearTextHttp).isFalse()
        assertThat(config.logsConfig)
            .isEqualTo(
                DatadogConfig.FeatureConfig(
                    fakeClientToken,
                    fakeApplicationId,
                    DatadogEndpoint.LOGS_US,
                    serviceName,
                    DatadogConfig.DEFAULT_ENV_NAME
                )
            )
        assertThat(config.tracesConfig)
            .isEqualTo(
                DatadogConfig.FeatureConfig(
                    fakeClientToken,
                    fakeApplicationId,
                    DatadogEndpoint.TRACES_US,
                    serviceName,
                    DatadogConfig.DEFAULT_ENV_NAME
                )
            )
        assertThat(config.crashReportConfig)
            .isEqualTo(
                DatadogConfig.FeatureConfig(
                    fakeClientToken,
                    fakeApplicationId,
                    DatadogEndpoint.LOGS_US,
                    serviceName,
                    DatadogConfig.DEFAULT_ENV_NAME
                )
            )
        assertThat(config.rumConfig)
            .isEqualTo(
                DatadogConfig.RumConfig(
                    fakeClientToken,
                    fakeApplicationId,
                    DatadogEndpoint.RUM_US,
                    serviceName,
                    DatadogConfig.DEFAULT_ENV_NAME
                )
            )
    }

    @Test
    fun `builder with custom env name`(
        forge: Forge
    ) {
        val envName = forge.anAlphabeticalString()
        val config = DatadogConfig.Builder(fakeClientToken, fakeApplicationId)
            .setLogsEnabled(true)
            .setTracesEnabled(true)
            .setCrashReportsEnabled(true)
            .setEnvironmentName(envName)
            .build()

        assertThat(config.needsClearTextHttp).isFalse()
        assertThat(config.logsConfig)
            .isEqualTo(
                DatadogConfig.FeatureConfig(
                    fakeClientToken,
                    fakeApplicationId,
                    DatadogEndpoint.LOGS_US,
                    DatadogConfig.DEFAULT_SERVICE_NAME,
                    envName
                )
            )
        assertThat(config.tracesConfig)
            .isEqualTo(
                DatadogConfig.FeatureConfig(
                    fakeClientToken,
                    fakeApplicationId,
                    DatadogEndpoint.TRACES_US,
                    DatadogConfig.DEFAULT_SERVICE_NAME,
                    envName
                )
            )
        assertThat(config.crashReportConfig)
            .isEqualTo(
                DatadogConfig.FeatureConfig(
                    fakeClientToken,
                    fakeApplicationId,
                    DatadogEndpoint.LOGS_US,
                    DatadogConfig.DEFAULT_SERVICE_NAME,
                    envName
                )
            )

        assertThat(config.rumConfig)
            .isEqualTo(
                DatadogConfig.RumConfig(
                    fakeClientToken,
                    fakeApplicationId,
                    DatadogEndpoint.RUM_US,
                    DatadogConfig.DEFAULT_SERVICE_NAME,
                    envName
                )
            )
    }

    @Test
    fun `builder with invalid custom env name`(
        forge: Forge
    ) {
        val envName = forge.anAlphabeticalString()
        val config = DatadogConfig.Builder(fakeClientToken, fakeApplicationId)
            .setLogsEnabled(true)
            .setTracesEnabled(true)
            .setCrashReportsEnabled(true)
            .setEnvironmentName("\"'$envName'\"")
            .build()

        assertThat(config.needsClearTextHttp).isFalse()
        assertThat(config.logsConfig)
            .isEqualTo(
                DatadogConfig.FeatureConfig(
                    fakeClientToken,
                    fakeApplicationId,
                    DatadogEndpoint.LOGS_US,
                    DatadogConfig.DEFAULT_SERVICE_NAME,
                    envName
                )
            )
        assertThat(config.tracesConfig)
            .isEqualTo(
                DatadogConfig.FeatureConfig(
                    fakeClientToken,
                    fakeApplicationId,
                    DatadogEndpoint.TRACES_US,
                    DatadogConfig.DEFAULT_SERVICE_NAME,
                    envName
                )
            )
        assertThat(config.crashReportConfig)
            .isEqualTo(
                DatadogConfig.FeatureConfig(
                    fakeClientToken,
                    fakeApplicationId,
                    DatadogEndpoint.LOGS_US,
                    DatadogConfig.DEFAULT_SERVICE_NAME,
                    envName
                )
            )

        assertThat(config.rumConfig)
            .isEqualTo(
                DatadogConfig.RumConfig(
                    fakeClientToken,
                    fakeApplicationId,
                    DatadogEndpoint.RUM_US,
                    DatadogConfig.DEFAULT_SERVICE_NAME,
                    envName
                )
            )
    }

    @Test
    fun `builder with all features disabled`() {
        val config = DatadogConfig.Builder(fakeClientToken, fakeApplicationId)
            .setLogsEnabled(false)
            .setTracesEnabled(false)
            .setCrashReportsEnabled(false)
            .setRumEnabled(false)
            .build()

        assertThat(config.needsClearTextHttp).isFalse()
        assertThat(config.logsConfig).isNull()
        assertThat(config.tracesConfig).isNull()
        assertThat(config.crashReportConfig).isNull()
        assertThat(config.rumConfig).isNull()
    }

    @Test
    fun `builder with US endpoints all features enabled`() {
        val config = DatadogConfig.Builder(fakeClientToken, fakeApplicationId)
            .useUSEndpoints()
            .setLogsEnabled(true)
            .setTracesEnabled(true)
            .setCrashReportsEnabled(true)
            .build()

        assertThat(config.needsClearTextHttp).isFalse()
        assertThat(config.logsConfig)
            .isEqualTo(
                DatadogConfig.FeatureConfig(
                    fakeClientToken,
                    fakeApplicationId,
                    DatadogEndpoint.LOGS_US,
                    DatadogConfig.DEFAULT_SERVICE_NAME,
                    DatadogConfig.DEFAULT_ENV_NAME
                )
            )
        assertThat(config.tracesConfig)
            .isEqualTo(
                DatadogConfig.FeatureConfig(
                    fakeClientToken,
                    fakeApplicationId,
                    DatadogEndpoint.TRACES_US,
                    DatadogConfig.DEFAULT_SERVICE_NAME,
                    DatadogConfig.DEFAULT_ENV_NAME
                )
            )
        assertThat(config.crashReportConfig)
            .isEqualTo(
                DatadogConfig.FeatureConfig(
                    fakeClientToken,
                    fakeApplicationId,
                    DatadogEndpoint.LOGS_US,
                    DatadogConfig.DEFAULT_SERVICE_NAME,
                    DatadogConfig.DEFAULT_ENV_NAME
                )
            )

        assertThat(config.rumConfig)
            .isEqualTo(
                DatadogConfig.RumConfig(
                    fakeClientToken,
                    fakeApplicationId,
                    DatadogEndpoint.RUM_US,
                    DatadogConfig.DEFAULT_SERVICE_NAME,
                    DatadogConfig.DEFAULT_ENV_NAME
                )
            )
    }

    @Test
    fun `builder with EU endpoints all features enabled`() {
        val config = DatadogConfig.Builder(fakeClientToken, fakeApplicationId)
            .useEUEndpoints()
            .setLogsEnabled(true)
            .setTracesEnabled(true)
            .setCrashReportsEnabled(true)
            .build()

        assertThat(config.needsClearTextHttp).isFalse()
        assertThat(config.logsConfig)
            .isEqualTo(
                DatadogConfig.FeatureConfig(
                    fakeClientToken,
                    fakeApplicationId,
                    DatadogEndpoint.LOGS_EU,
                    DatadogConfig.DEFAULT_SERVICE_NAME,
                    DatadogConfig.DEFAULT_ENV_NAME
                )
            )
        assertThat(config.tracesConfig)
            .isEqualTo(
                DatadogConfig.FeatureConfig(
                    fakeClientToken,
                    fakeApplicationId,
                    DatadogEndpoint.TRACES_EU,
                    DatadogConfig.DEFAULT_SERVICE_NAME,
                    DatadogConfig.DEFAULT_ENV_NAME
                )
            )
        assertThat(config.crashReportConfig)
            .isEqualTo(
                DatadogConfig.FeatureConfig(
                    fakeClientToken,
                    fakeApplicationId,
                    DatadogEndpoint.LOGS_EU,
                    DatadogConfig.DEFAULT_SERVICE_NAME,
                    DatadogConfig.DEFAULT_ENV_NAME
                )
            )
        assertThat(config.rumConfig)
            .isEqualTo(
                DatadogConfig.RumConfig(
                    fakeClientToken,
                    fakeApplicationId,
                    DatadogEndpoint.RUM_EU,
                    DatadogConfig.DEFAULT_SERVICE_NAME,
                    DatadogConfig.DEFAULT_ENV_NAME
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
        val config = DatadogConfig.Builder(fakeClientToken, fakeApplicationId)
            .useCustomLogsEndpoint(logsUrl)
            .useCustomTracesEndpoint(tracesUrl)
            .useCustomCrashReportsEndpoint(crashReportsUrl)
            .useCustomRumEndpoint(rumUrl)
            .setLogsEnabled(true)
            .setTracesEnabled(true)
            .setCrashReportsEnabled(true)
            .build()

        assertThat(config.needsClearTextHttp).isFalse()
        assertThat(config.logsConfig)
            .isEqualTo(
                DatadogConfig.FeatureConfig(
                    fakeClientToken,
                    fakeApplicationId,
                    logsUrl,
                    DatadogConfig.DEFAULT_SERVICE_NAME,
                    DatadogConfig.DEFAULT_ENV_NAME
                )
            )
        assertThat(config.tracesConfig)
            .isEqualTo(
                DatadogConfig.FeatureConfig(
                    fakeClientToken,
                    fakeApplicationId,
                    tracesUrl,
                    DatadogConfig.DEFAULT_SERVICE_NAME,
                    DatadogConfig.DEFAULT_ENV_NAME
                )
            )
        assertThat(config.crashReportConfig)
            .isEqualTo(
                DatadogConfig.FeatureConfig(
                    fakeClientToken,
                    fakeApplicationId,
                    crashReportsUrl,
                    DatadogConfig.DEFAULT_SERVICE_NAME,
                    DatadogConfig.DEFAULT_ENV_NAME
                )
            )

        assertThat(config.rumConfig)
            .isEqualTo(
                DatadogConfig.RumConfig(
                    fakeClientToken,
                    fakeApplicationId,
                    rumUrl,
                    DatadogConfig.DEFAULT_SERVICE_NAME,
                    DatadogConfig.DEFAULT_ENV_NAME
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
        val config = DatadogConfig.Builder(fakeClientToken, fakeApplicationId)
            .useCustomLogsEndpoint(logsUrl)
            .useCustomTracesEndpoint(tracesUrl)
            .useCustomCrashReportsEndpoint(crashReportsUrl)
            .useCustomRumEndpoint(rumUrl)
            .setLogsEnabled(true)
            .setTracesEnabled(true)
            .setCrashReportsEnabled(true)
            .build()

        assertThat(config.needsClearTextHttp).isTrue()
        assertThat(config.logsConfig)
            .isEqualTo(
                DatadogConfig.FeatureConfig(
                    fakeClientToken,
                    fakeApplicationId,
                    logsUrl,
                    DatadogConfig.DEFAULT_SERVICE_NAME,
                    DatadogConfig.DEFAULT_ENV_NAME
                )
            )
        assertThat(config.tracesConfig)
            .isEqualTo(
                DatadogConfig.FeatureConfig(
                    fakeClientToken,
                    fakeApplicationId,
                    tracesUrl,
                    DatadogConfig.DEFAULT_SERVICE_NAME,
                    DatadogConfig.DEFAULT_ENV_NAME
                )
            )
        assertThat(config.crashReportConfig)
            .isEqualTo(
                DatadogConfig.FeatureConfig(
                    fakeClientToken,
                    fakeApplicationId,
                    crashReportsUrl,
                    DatadogConfig.DEFAULT_SERVICE_NAME,
                    DatadogConfig.DEFAULT_ENV_NAME
                )
            )
        assertThat(config.rumConfig)
            .isEqualTo(
                DatadogConfig.RumConfig(
                    fakeClientToken,
                    fakeApplicationId,
                    rumUrl,
                    DatadogConfig.DEFAULT_SERVICE_NAME,
                    DatadogConfig.DEFAULT_ENV_NAME
                )
            )
    }

    @Test
    fun `builder with all tracking instrumentation disabled`(forge: Forge) {
        val rumUrl = forge.aStringMatching("http://[a-z]+\\.com")
        val config = DatadogConfig.Builder(fakeClientToken, fakeApplicationId)
            .useCustomRumEndpoint(rumUrl)
            .build()

        val rumConfig: DatadogConfig.RumConfig? = config.rumConfig
        assertThat(rumConfig).isNotNull()
        RumConfigAssert.assertThat(rumConfig!!)
            .hasClientToken(fakeClientToken)
            .hasApplicationId(fakeApplicationId)
            .hasEndpointUrl(rumUrl)
            .hasServiceName(DatadogConfig.DEFAULT_SERVICE_NAME)
            .hasEnvName(DatadogConfig.DEFAULT_ENV_NAME)
            .doesNotHaveGesturesTrackingStrategy()
            .doesNotHaveViewTrackingStrategy()
    }

    @Test
    fun `builder with track gestures enabled`(forge: Forge) {
        val rumUrl = forge.aStringMatching("http://[a-z]+\\.com")
        val config = DatadogConfig.Builder(fakeClientToken, fakeApplicationId)
            .useCustomRumEndpoint(rumUrl)
            .trackGestures()
            .build()

        val rumConfig: DatadogConfig.RumConfig? = config.rumConfig
        assertThat(rumConfig).isNotNull()
        RumConfigAssert.assertThat(rumConfig!!)
            .hasClientToken(fakeClientToken)
            .hasApplicationId(fakeApplicationId)
            .hasEndpointUrl(rumUrl)
            .hasServiceName(DatadogConfig.DEFAULT_SERVICE_NAME)
            .hasEnvName(DatadogConfig.DEFAULT_ENV_NAME)
            .hasGesturesTrackingStrategy()
            .doesNotHaveViewTrackingStrategy()
    }

    @Test
    fun `builder with track activities as views tracking strategy`(forge: Forge) {
        val rumUrl = forge.aStringMatching("http://[a-z]+\\.com")
        val strategy = ActivityViewTrackingStrategy()
        val config = DatadogConfig.Builder(fakeClientToken, fakeApplicationId)
            .useCustomRumEndpoint(rumUrl)
            .useViewTrackingStrategy(strategy)
            .build()
        val rumConfig: DatadogConfig.RumConfig? = config.rumConfig
        assertThat(rumConfig).isNotNull()
        RumConfigAssert.assertThat(rumConfig!!)
            .hasClientToken(fakeClientToken)
            .hasApplicationId(fakeApplicationId)
            .hasEndpointUrl(rumUrl)
            .hasServiceName(DatadogConfig.DEFAULT_SERVICE_NAME)
            .hasEnvName(DatadogConfig.DEFAULT_ENV_NAME)
            .doesNotHaveGesturesTrackingStrategy()
            .hasViewTrackingStrategy(strategy)
    }
}
