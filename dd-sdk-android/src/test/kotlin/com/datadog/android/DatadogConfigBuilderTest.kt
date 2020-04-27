/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android

import android.os.Build
import com.datadog.android.rum.assertj.RumConfigAssert
import com.datadog.android.rum.tracking.ActivityViewTrackingStrategy
import com.datadog.android.rum.tracking.ViewAttributesProvider
import com.datadog.android.utils.forge.Configurator
import com.datadog.tools.unit.annotations.TestTargetApi
import com.datadog.tools.unit.extensions.ApiLevelExtension
import com.nhaarman.mockitokotlin2.mock
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

        assertThat(config.needsClearTextHttp).isFalse()
        assertThat(config.logsConfig)
            .isEqualTo(
                DatadogConfig.FeatureConfig(
                    fakeClientToken,
                    UUID(0, 0),
                    DatadogEndpoint.LOGS_US,
                    DatadogConfig.DEFAULT_SERVICE_NAME,
                    fakeEnvName
                )
            )
        assertThat(config.tracesConfig)
            .isEqualTo(
                DatadogConfig.FeatureConfig(
                    fakeClientToken,
                    UUID(0, 0),
                    DatadogEndpoint.TRACES_US,
                    DatadogConfig.DEFAULT_SERVICE_NAME,
                    fakeEnvName
                )
            )
        assertThat(config.crashReportConfig)
            .isEqualTo(
                DatadogConfig.FeatureConfig(
                    fakeClientToken,
                    UUID(0, 0),
                    DatadogEndpoint.LOGS_US,
                    DatadogConfig.DEFAULT_SERVICE_NAME,
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

        assertThat(config.needsClearTextHttp).isFalse()
        assertThat(config.logsConfig)
            .isEqualTo(
                DatadogConfig.FeatureConfig(
                    fakeClientToken,
                    fakeApplicationId,
                    DatadogEndpoint.LOGS_US,
                    DatadogConfig.DEFAULT_SERVICE_NAME,
                    fakeEnvName
                )
            )
        assertThat(config.tracesConfig)
            .isEqualTo(
                DatadogConfig.FeatureConfig(
                    fakeClientToken,
                    fakeApplicationId,
                    DatadogEndpoint.TRACES_US,
                    DatadogConfig.DEFAULT_SERVICE_NAME,
                    fakeEnvName
                )
            )
        assertThat(config.crashReportConfig)
            .isEqualTo(
                DatadogConfig.FeatureConfig(
                    fakeClientToken,
                    fakeApplicationId,
                    DatadogEndpoint.LOGS_US,
                    DatadogConfig.DEFAULT_SERVICE_NAME,
                    fakeEnvName
                )
            )
        assertThat(config.rumConfig)
            .isEqualTo(
                DatadogConfig.RumConfig(
                    fakeClientToken,
                    fakeApplicationId,
                    DatadogEndpoint.RUM_US,
                    DatadogConfig.DEFAULT_SERVICE_NAME,
                    fakeEnvName
                )
            )
    }

    @Test
    fun `builder returns sensible defaults with UUID applicationId`() {
        val config = DatadogConfig.Builder(fakeClientToken, fakeEnvName, fakeApplicationId)
            .build()

        assertThat(config.needsClearTextHttp).isFalse()
        assertThat(config.logsConfig)
            .isEqualTo(
                DatadogConfig.FeatureConfig(
                    fakeClientToken,
                    fakeApplicationId,
                    DatadogEndpoint.LOGS_US,
                    DatadogConfig.DEFAULT_SERVICE_NAME,
                    fakeEnvName
                )
            )
        assertThat(config.tracesConfig)
            .isEqualTo(
                DatadogConfig.FeatureConfig(
                    fakeClientToken,
                    fakeApplicationId,
                    DatadogEndpoint.TRACES_US,
                    DatadogConfig.DEFAULT_SERVICE_NAME,
                    fakeEnvName
                )
            )
        assertThat(config.crashReportConfig)
            .isEqualTo(
                DatadogConfig.FeatureConfig(
                    fakeClientToken,
                    fakeApplicationId,
                    DatadogEndpoint.LOGS_US,
                    DatadogConfig.DEFAULT_SERVICE_NAME,
                    fakeEnvName
                )
            )
        assertThat(config.rumConfig)
            .isEqualTo(
                DatadogConfig.RumConfig(
                    fakeClientToken,
                    fakeApplicationId,
                    DatadogEndpoint.RUM_US,
                    DatadogConfig.DEFAULT_SERVICE_NAME,
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
                    fakeEnvName
                )
            )
        assertThat(config.tracesConfig)
            .isEqualTo(
                DatadogConfig.FeatureConfig(
                    fakeClientToken,
                    fakeApplicationId,
                    DatadogEndpoint.TRACES_US,
                    serviceName,
                    fakeEnvName
                )
            )
        assertThat(config.crashReportConfig)
            .isEqualTo(
                DatadogConfig.FeatureConfig(
                    fakeClientToken,
                    fakeApplicationId,
                    DatadogEndpoint.LOGS_US,
                    serviceName,
                    fakeEnvName
                )
            )
        assertThat(config.rumConfig)
            .isEqualTo(
                DatadogConfig.RumConfig(
                    fakeClientToken,
                    fakeApplicationId,
                    DatadogEndpoint.RUM_US,
                    serviceName,
                    fakeEnvName
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
        val config = DatadogConfig.Builder(fakeClientToken, fakeEnvName, fakeApplicationId)
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
        val config = DatadogConfig.Builder(fakeClientToken, fakeEnvName, fakeApplicationId)
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
        val config = DatadogConfig.Builder(fakeClientToken, fakeEnvName, fakeApplicationId)
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
                    fakeEnvName
                )
            )
        assertThat(config.tracesConfig)
            .isEqualTo(
                DatadogConfig.FeatureConfig(
                    fakeClientToken,
                    fakeApplicationId,
                    DatadogEndpoint.TRACES_US,
                    DatadogConfig.DEFAULT_SERVICE_NAME,
                    fakeEnvName
                )
            )
        assertThat(config.crashReportConfig)
            .isEqualTo(
                DatadogConfig.FeatureConfig(
                    fakeClientToken,
                    fakeApplicationId,
                    DatadogEndpoint.LOGS_US,
                    DatadogConfig.DEFAULT_SERVICE_NAME,
                    fakeEnvName
                )
            )

        assertThat(config.rumConfig)
            .isEqualTo(
                DatadogConfig.RumConfig(
                    fakeClientToken,
                    fakeApplicationId,
                    DatadogEndpoint.RUM_US,
                    DatadogConfig.DEFAULT_SERVICE_NAME,
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
            .build()

        assertThat(config.needsClearTextHttp).isFalse()
        assertThat(config.logsConfig)
            .isEqualTo(
                DatadogConfig.FeatureConfig(
                    fakeClientToken,
                    fakeApplicationId,
                    DatadogEndpoint.LOGS_EU,
                    DatadogConfig.DEFAULT_SERVICE_NAME,
                    fakeEnvName
                )
            )
        assertThat(config.tracesConfig)
            .isEqualTo(
                DatadogConfig.FeatureConfig(
                    fakeClientToken,
                    fakeApplicationId,
                    DatadogEndpoint.TRACES_EU,
                    DatadogConfig.DEFAULT_SERVICE_NAME,
                    fakeEnvName
                )
            )
        assertThat(config.crashReportConfig)
            .isEqualTo(
                DatadogConfig.FeatureConfig(
                    fakeClientToken,
                    fakeApplicationId,
                    DatadogEndpoint.LOGS_EU,
                    DatadogConfig.DEFAULT_SERVICE_NAME,
                    fakeEnvName
                )
            )
        assertThat(config.rumConfig)
            .isEqualTo(
                DatadogConfig.RumConfig(
                    fakeClientToken,
                    fakeApplicationId,
                    DatadogEndpoint.RUM_EU,
                    DatadogConfig.DEFAULT_SERVICE_NAME,
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
            .build()

        assertThat(config.needsClearTextHttp).isFalse()
        assertThat(config.logsConfig)
            .isEqualTo(
                DatadogConfig.FeatureConfig(
                    fakeClientToken,
                    fakeApplicationId,
                    logsUrl,
                    DatadogConfig.DEFAULT_SERVICE_NAME,
                    fakeEnvName
                )
            )
        assertThat(config.tracesConfig)
            .isEqualTo(
                DatadogConfig.FeatureConfig(
                    fakeClientToken,
                    fakeApplicationId,
                    tracesUrl,
                    DatadogConfig.DEFAULT_SERVICE_NAME,
                    fakeEnvName
                )
            )
        assertThat(config.crashReportConfig)
            .isEqualTo(
                DatadogConfig.FeatureConfig(
                    fakeClientToken,
                    fakeApplicationId,
                    crashReportsUrl,
                    DatadogConfig.DEFAULT_SERVICE_NAME,
                    fakeEnvName
                )
            )

        assertThat(config.rumConfig)
            .isEqualTo(
                DatadogConfig.RumConfig(
                    fakeClientToken,
                    fakeApplicationId,
                    rumUrl,
                    DatadogConfig.DEFAULT_SERVICE_NAME,
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
            .build()

        assertThat(config.needsClearTextHttp).isTrue()
        assertThat(config.logsConfig)
            .isEqualTo(
                DatadogConfig.FeatureConfig(
                    fakeClientToken,
                    fakeApplicationId,
                    logsUrl,
                    DatadogConfig.DEFAULT_SERVICE_NAME,
                    fakeEnvName
                )
            )
        assertThat(config.tracesConfig)
            .isEqualTo(
                DatadogConfig.FeatureConfig(
                    fakeClientToken,
                    fakeApplicationId,
                    tracesUrl,
                    DatadogConfig.DEFAULT_SERVICE_NAME,
                    fakeEnvName
                )
            )
        assertThat(config.crashReportConfig)
            .isEqualTo(
                DatadogConfig.FeatureConfig(
                    fakeClientToken,
                    fakeApplicationId,
                    crashReportsUrl,
                    DatadogConfig.DEFAULT_SERVICE_NAME,
                    fakeEnvName
                )
            )
        assertThat(config.rumConfig)
            .isEqualTo(
                DatadogConfig.RumConfig(
                    fakeClientToken,
                    fakeApplicationId,
                    rumUrl,
                    DatadogConfig.DEFAULT_SERVICE_NAME,
                    fakeEnvName
                )
            )
    }

    @Test
    fun `builder with all tracking instrumentation disabled`(forge: Forge) {
        val rumUrl = forge.aStringMatching("http://[a-z]+\\.com")
        val config = DatadogConfig.Builder(fakeClientToken, fakeEnvName, fakeApplicationId)
            .useCustomRumEndpoint(rumUrl)
            .build()

        val rumConfig: DatadogConfig.RumConfig? = config.rumConfig
        assertThat(rumConfig).isNotNull()
        RumConfigAssert.assertThat(rumConfig!!)
            .hasClientToken(fakeClientToken)
            .hasApplicationId(fakeApplicationId)
            .hasEndpointUrl(rumUrl)
            .hasServiceName(DatadogConfig.DEFAULT_SERVICE_NAME)
            .hasEnvName(fakeEnvName)
            .doesNotHaveGesturesTrackingStrategy()
            .doesNotHaveViewTrackingStrategy()
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
            .trackGestures(touchTargetExtraAttributesProviders)
            .build()
        val rumConfig: DatadogConfig.RumConfig? = config.rumConfig
        assertThat(rumConfig).isNotNull()
        RumConfigAssert.assertThat(rumConfig!!)
            .hasClientToken(fakeClientToken)
            .hasApplicationId(fakeApplicationId)
            .hasEndpointUrl(rumUrl)
            .hasServiceName(DatadogConfig.DEFAULT_SERVICE_NAME)
            .hasEnvName(fakeEnvName)
            .hasGesturesTrackingStrategy(
                touchTargetExtraAttributesProviders
            )
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
            .trackGestures(touchTargetExtraAttributesProviders)
            .build()
        val rumConfig: DatadogConfig.RumConfig? = config.rumConfig
        assertThat(rumConfig).isNotNull()
        RumConfigAssert.assertThat(rumConfig!!)
            .hasClientToken(fakeClientToken)
            .hasApplicationId(fakeApplicationId)
            .hasEndpointUrl(rumUrl)
            .hasServiceName(DatadogConfig.DEFAULT_SERVICE_NAME)
            .hasEnvName(fakeEnvName)
            .hasGesturesTrackingStrategyApi29(
                touchTargetExtraAttributesProviders
            )
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
            .hasServiceName(DatadogConfig.DEFAULT_SERVICE_NAME)
            .hasEnvName(fakeEnvName)
            .doesNotHaveGesturesTrackingStrategy()
            .hasViewTrackingStrategy(strategy)
    }
}
