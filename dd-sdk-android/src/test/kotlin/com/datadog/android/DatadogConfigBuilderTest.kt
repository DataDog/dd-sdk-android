/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2020 Datadog, Inc.
 */

package com.datadog.android

import com.datadog.android.utils.forge.Configurator
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
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

    @BeforeEach
    fun `set up`(forge: Forge) {
        fakeClientToken = forge.anHexadecimalString()
    }

    @Test
    fun `builder returns sensible defaults`() {
        val config = DatadogConfig.Builder(fakeClientToken)
            .build()

        assertThat(config.needsClearTextHttp).isFalse()
        assertThat(config.logsConfig)
            .isEqualTo(
                DatadogConfig.FeatureConfig(
                    fakeClientToken,
                    DatadogEndpoint.LOGS_US,
                    DatadogConfig.DEFAULT_SERVICE_NAME
                )
            )
        assertThat(config.tracesConfig)
            .isEqualTo(
                DatadogConfig.FeatureConfig(
                    fakeClientToken,
                    DatadogEndpoint.TRACES_US,
                    DatadogConfig.DEFAULT_SERVICE_NAME
                )
            )
        assertThat(config.crashReportConfig)
            .isEqualTo(
                DatadogConfig.FeatureConfig(
                    fakeClientToken,
                    DatadogEndpoint.LOGS_US,
                    DatadogConfig.DEFAULT_SERVICE_NAME
                )
            )
    }

    @Test
    fun `builder with custom service name`(
        forge: Forge
    ) {
        val serviceName = forge.anAlphabeticalString()
        val config = DatadogConfig.Builder(fakeClientToken)
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
                    DatadogEndpoint.LOGS_US,
                    serviceName
                )
            )
        assertThat(config.tracesConfig)
            .isEqualTo(
                DatadogConfig.FeatureConfig(
                    fakeClientToken,
                    DatadogEndpoint.TRACES_US,
                    serviceName
                )
            )
        assertThat(config.crashReportConfig)
            .isEqualTo(
                DatadogConfig.FeatureConfig(
                    fakeClientToken,
                    DatadogEndpoint.LOGS_US,
                    serviceName
                )
            )
    }

    @Test
    fun `builder with all features disabled`() {
        val config = DatadogConfig.Builder(fakeClientToken)
            .setLogsEnabled(false)
            .setTracesEnabled(false)
            .setCrashReportsEnabled(false)
            .build()

        assertThat(config.needsClearTextHttp).isFalse()
        assertThat(config.logsConfig).isNull()
        assertThat(config.tracesConfig).isNull()
        assertThat(config.crashReportConfig).isNull()
    }

    @Test
    fun `builder with US endpoints all features enabled`() {
        val config = DatadogConfig.Builder(fakeClientToken)
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
                    DatadogEndpoint.LOGS_US,
                    DatadogConfig.DEFAULT_SERVICE_NAME
                )
            )
        assertThat(config.tracesConfig)
            .isEqualTo(
                DatadogConfig.FeatureConfig(
                    fakeClientToken,
                    DatadogEndpoint.TRACES_US,
                    DatadogConfig.DEFAULT_SERVICE_NAME
                )
            )
        assertThat(config.crashReportConfig)
            .isEqualTo(
                DatadogConfig.FeatureConfig(
                    fakeClientToken,
                    DatadogEndpoint.LOGS_US,
                    DatadogConfig.DEFAULT_SERVICE_NAME
                )
            )
    }

    @Test
    fun `builder with EU endpoints all features enabled`() {
        val config = DatadogConfig.Builder(fakeClientToken)
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
                    DatadogEndpoint.LOGS_EU,
                    DatadogConfig.DEFAULT_SERVICE_NAME
                )
            )
        assertThat(config.tracesConfig)
            .isEqualTo(
                DatadogConfig.FeatureConfig(
                    fakeClientToken,
                    DatadogEndpoint.TRACES_EU,
                    DatadogConfig.DEFAULT_SERVICE_NAME
                )
            )
        assertThat(config.crashReportConfig)
            .isEqualTo(
                DatadogConfig.FeatureConfig(
                    fakeClientToken,
                    DatadogEndpoint.LOGS_EU,
                    DatadogConfig.DEFAULT_SERVICE_NAME
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
        val config = DatadogConfig.Builder(fakeClientToken)
            .useCustomLogsEndpoint(logsUrl)
            .useCustomTracesEndpoint(tracesUrl)
            .useCustomCrashReportsEndpoint(crashReportsUrl)
            .setLogsEnabled(true)
            .setTracesEnabled(true)
            .setCrashReportsEnabled(true)
            .build()

        assertThat(config.needsClearTextHttp).isFalse()
        assertThat(config.logsConfig)
            .isEqualTo(
                DatadogConfig.FeatureConfig(
                    fakeClientToken,
                    logsUrl,
                    DatadogConfig.DEFAULT_SERVICE_NAME
                )
            )
        assertThat(config.tracesConfig)
            .isEqualTo(
                DatadogConfig.FeatureConfig(
                    fakeClientToken,
                    tracesUrl,
                    DatadogConfig.DEFAULT_SERVICE_NAME
                )
            )
        assertThat(config.crashReportConfig)
            .isEqualTo(
                DatadogConfig.FeatureConfig(
                    fakeClientToken,
                    crashReportsUrl,
                    DatadogConfig.DEFAULT_SERVICE_NAME
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
        val config = DatadogConfig.Builder(fakeClientToken)
            .useCustomLogsEndpoint(logsUrl)
            .useCustomTracesEndpoint(tracesUrl)
            .useCustomCrashReportsEndpoint(crashReportsUrl)
            .setLogsEnabled(true)
            .setTracesEnabled(true)
            .setCrashReportsEnabled(true)
            .build()

        assertThat(config.needsClearTextHttp).isTrue()
        assertThat(config.logsConfig)
            .isEqualTo(
                DatadogConfig.FeatureConfig(
                    fakeClientToken,
                    logsUrl,
                    DatadogConfig.DEFAULT_SERVICE_NAME
                )
            )
        assertThat(config.tracesConfig)
            .isEqualTo(
                DatadogConfig.FeatureConfig(
                    fakeClientToken,
                    tracesUrl,
                    DatadogConfig.DEFAULT_SERVICE_NAME
                )
            )
        assertThat(config.crashReportConfig)
            .isEqualTo(
                DatadogConfig.FeatureConfig(
                    fakeClientToken,
                    crashReportsUrl,
                    DatadogConfig.DEFAULT_SERVICE_NAME
                )
            )
    }
}
