/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android

import android.os.Build
import android.util.Log
import com.datadog.android.core.configuration.Configuration
import com.datadog.android.event.EventMapper
import com.datadog.android.event.ViewEventMapper
import com.datadog.android.log.internal.logger.LogHandler
import com.datadog.android.plugin.DatadogPlugin
import com.datadog.android.plugin.Feature
import com.datadog.android.rum.assertj.RumConfigAssert.Companion.assertThat
import com.datadog.android.rum.model.ActionEvent
import com.datadog.android.rum.model.ErrorEvent
import com.datadog.android.rum.model.ResourceEvent
import com.datadog.android.rum.tracking.ActivityViewTrackingStrategy
import com.datadog.android.rum.tracking.ViewAttributesProvider
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.utils.mockDevLogHandler
import com.datadog.tools.unit.annotations.TestTargetApi
import com.datadog.tools.unit.extensions.ApiLevelExtension
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.anyOrNull
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.reset
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyZeroInteractions
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.FloatForgery
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.annotation.RegexForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.annotation.StringForgeryType
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import java.net.MalformedURLException
import java.net.URL
import java.util.Locale
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
internal class DatadogConfigBuilderTest {

    lateinit var testedBuilder: DatadogConfig.Builder

    @StringForgery(type = StringForgeryType.HEXADECIMAL)
    lateinit var fakeClientToken: String

    @StringForgery
    lateinit var fakeEnvName: String

    @Forgery
    lateinit var fakeApplicationId: UUID

    lateinit var mockDevLogHandler: LogHandler

    // region Unit Tests

    @BeforeEach
    fun `set up`() {
        mockDevLogHandler = mockDevLogHandler()
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
                    needsClearTextHttp = false,
                    envName = fakeEnvName
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
                    needsClearTextHttp = false,
                    envName = fakeEnvName
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
        assertThat(config.rumConfig!!)
            .hasClientToken(fakeClientToken)
            .hasApplicationId(fakeApplicationId)
            .hasEndpointUrl(DatadogEndpoint.RUM_US)
            .hasEnvName(fakeEnvName)
    }

    @Test
    fun `𝕄 use sensible defaults 𝕎 build() {UUID applicationId}`() {
        // When
        val config = testedBuilder.build()

        // Then
        assertThat(config.coreConfig)
            .isEqualTo(
                DatadogConfig.CoreConfig(
                    needsClearTextHttp = false,
                    envName = fakeEnvName
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
        assertThat(config.rumConfig!!)
            .hasClientToken(fakeClientToken)
            .hasApplicationId(fakeApplicationId)
            .hasEndpointUrl(DatadogEndpoint.RUM_US)
            .hasEnvName(fakeEnvName)
    }

    @Test
    fun `𝕄 build config with serviceName 𝕎 setServiceName() and build()`(
        @StringForgery serviceName: String
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
                    envName = fakeEnvName,
                    serviceName = serviceName
                )
            )
    }

    @Test
    fun `𝕄 build config with envName 𝕎 setEnvironmentName() and build()`(
        @StringForgery envName: String
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
                    needsClearTextHttp = false,
                    envName = envName
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

        assertThat(config.rumConfig!!)
            .hasClientToken(fakeClientToken)
            .hasApplicationId(fakeApplicationId)
            .hasEndpointUrl(DatadogEndpoint.RUM_US)
            .hasEnvName(envName)
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
                    needsClearTextHttp = false,
                    envName = fakeEnvName
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
                    needsClearTextHttp = false,
                    envName = fakeEnvName
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

        assertThat(config.rumConfig!!)
            .hasClientToken(fakeClientToken)
            .hasApplicationId(fakeApplicationId)
            .hasEndpointUrl(DatadogEndpoint.RUM_US)
            .hasEnvName(fakeEnvName)
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
                    needsClearTextHttp = false,
                    envName = fakeEnvName
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
        assertThat(config.rumConfig!!)
            .hasClientToken(fakeClientToken)
            .hasApplicationId(fakeApplicationId)
            .hasEndpointUrl(DatadogEndpoint.RUM_EU)
            .hasEnvName(fakeEnvName)
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
                    needsClearTextHttp = false,
                    envName = fakeEnvName
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
        assertThat(config.rumConfig!!)
            .hasClientToken(fakeClientToken)
            .hasApplicationId(fakeApplicationId)
            .hasEndpointUrl(DatadogEndpoint.RUM_GOV)
            .hasEnvName(fakeEnvName)
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
                    needsClearTextHttp = false,
                    envName = fakeEnvName
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

        assertThat(config.rumConfig!!)
            .hasClientToken(fakeClientToken)
            .hasApplicationId(fakeApplicationId)
            .hasEndpointUrl(rumUrl)
            .hasEnvName(fakeEnvName)
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
                    needsClearTextHttp = true,
                    envName = fakeEnvName
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
        assertThat(config.rumConfig!!)
            .hasClientToken(fakeClientToken)
            .hasApplicationId(fakeApplicationId)
            .hasEndpointUrl(rumUrl)
            .hasEnvName(fakeEnvName)
    }

    @Test
    fun `𝕄 build config with first party hosts 𝕎 setFirstPartyHosts { using ip addresses }`(
        @StringForgery(
            regex = "(([0-9]|[1-9][0-9]|1[0-9]{2}|2[0-4][0-9]|25[0-5])\\.){3}" +
                "([0-9]|[1-9][0-9]|1[0-9]{2}|2[0-4][0-9]|25[0-5])"
        ) hosts: List<String>
    ) {
        // When
        val config = testedBuilder
            .setLogsEnabled(true)
            .setTracesEnabled(true)
            .setCrashReportsEnabled(true)
            .setRumEnabled(true)
            .setFirstPartyHosts(hosts)
            .build()

        // Then
        assertThat(config.coreConfig)
            .isEqualTo(
                DatadogConfig.CoreConfig(
                    needsClearTextHttp = false,
                    envName = fakeEnvName,
                    hosts = hosts
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

        assertThat(config.rumConfig!!)
            .hasClientToken(fakeClientToken)
            .hasApplicationId(fakeApplicationId)
            .hasEndpointUrl(DatadogEndpoint.RUM_US)
            .hasEnvName(fakeEnvName)
    }

    @Test
    fun `𝕄 build config with first party hosts 𝕎 setFirstPartyHosts { using host names }`(
        @StringForgery(
            regex = "(([a-zA-Z0-9]|[a-zA-Z0-9][a-zA-Z0-9-]*[a-zA-Z0-9])\\.)+" +
                "([A-Za-z]|[A-Za-z][A-Za-z0-9-]*[A-Za-z0-9])"
        ) hosts: List<String>
    ) {
        // When
        val config = testedBuilder
            .setLogsEnabled(true)
            .setTracesEnabled(true)
            .setCrashReportsEnabled(true)
            .setRumEnabled(true)
            .setFirstPartyHosts(hosts)
            .build()

        // Then
        assertThat(config.coreConfig)
            .isEqualTo(
                DatadogConfig.CoreConfig(
                    needsClearTextHttp = false,
                    envName = fakeEnvName,
                    hosts = hosts
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

        assertThat(config.rumConfig!!)
            .hasClientToken(fakeClientToken)
            .hasApplicationId(fakeApplicationId)
            .hasEndpointUrl(DatadogEndpoint.RUM_US)
            .hasEnvName(fakeEnvName)
    }

    @Test
    fun `𝕄 drop everything 𝕎 setFirstPartyHosts { using top level domain hosts only}`(
        @StringForgery(
            regex = "([A-Za-z]|[A-Za-z][A-Za-z0-9-]*[A-Za-z0-9])"
        ) hosts: List<String>
    ) {
        // When
        val config = testedBuilder
            .setLogsEnabled(true)
            .setTracesEnabled(true)
            .setCrashReportsEnabled(true)
            .setRumEnabled(true)
            .setFirstPartyHosts(hosts)
            .build()

        // Then
        assertThat(config.coreConfig)
            .isEqualTo(
                DatadogConfig.CoreConfig(
                    needsClearTextHttp = false,
                    envName = fakeEnvName,
                    hosts = emptyList()
                )
            )
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
            .setLogsEnabled(true)
            .setTracesEnabled(true)
            .setCrashReportsEnabled(true)
            .setRumEnabled(true)
            .setFirstPartyHosts(hostsWithLocalHost)
            .build()

        // Then
        assertThat(config.coreConfig)
            .isEqualTo(
                DatadogConfig.CoreConfig(
                    needsClearTextHttp = false,
                    envName = fakeEnvName,
                    hosts = listOf(fakeLocalHost)
                )
            )
    }

    @Test
    fun `M log error W setFirstPartyHosts { using malformed hostname }`(
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
                Configuration.ERROR_MALFORMED_HOST_IP_ADDRESS.format(Locale.US, it)
            )
        }
    }

    @Test
    fun `M log error W setFirstPartyHosts { using malformed ip address }`(
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
                Configuration.ERROR_MALFORMED_HOST_IP_ADDRESS.format(Locale.US, it)
            )
        }
    }

    @Test
    fun `M drop all malformed hosts W setFirstPartyHosts { using malformed hostname }`(
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
        assertThat(config.coreConfig)
            .isEqualTo(
                DatadogConfig.CoreConfig(
                    needsClearTextHttp = false,
                    envName = fakeEnvName,
                    hosts = emptyList()
                )
            )
    }

    @Test
    fun `M drop all malformed ip addresses W setFirstPartyHosts { using malformed ip address }`(
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
        assertThat(config.coreConfig)
            .isEqualTo(
                DatadogConfig.CoreConfig(
                    needsClearTextHttp = false,
                    envName = fakeEnvName,
                    hosts = emptyList()
                )
            )
    }

    @Test
    fun `M get host name W setFirstPartyHosts { using url instead of host name as argument }`(
        @StringForgery(
            regex = "(https|http)://([a-z][a-z0-9-]{3,9}\\.){1,4}[a-z][a-z0-9]{2,3}"
        ) hosts: List<String>
    ) {
        // WHEN
        val config = testedBuilder
            .setFirstPartyHosts(hosts)
            .build()

        // THEN
        assertThat(config.coreConfig)
            .isEqualTo(
                DatadogConfig.CoreConfig(
                    needsClearTextHttp = false,
                    envName = fakeEnvName,
                    hosts = hosts.map { URL(it).host }
                )
            )
    }

    @Test
    fun `M warn W setFirstPartyHosts { using url instead of host name as argument }`(
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
                Configuration.WARNING_USING_URL_FOR_HOST.format(Locale.US, it, URL(it).host)
            )
        }
    }

    @Test
    fun `M warn W setFirstPartyHosts { using malformed url as argument }`(
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
                eq(Configuration.ERROR_MALFORMED_URL.format(Locale.US, it)),
                any<MalformedURLException>(),
                anyOrNull(),
                anyOrNull(),
                anyOrNull()
            )
        }
    }

    @Test
    fun `M drop all malformed urls W setFirstPartyHosts { using malformed url as argument }`(
        @StringForgery(
            regex = "(https|http)://([a-z][a-z0-9-]{3,9}\\.){1,4}[a-z][a-z0-9]{2,3}:-8[0-9]{1}"
        ) hosts: List<String>
    ) {
        // WHEN
        val config = testedBuilder
            .setFirstPartyHosts(hosts)
            .build()

        // THEN
        assertThat(config.coreConfig)
            .isEqualTo(
                DatadogConfig.CoreConfig(
                    needsClearTextHttp = false,
                    envName = fakeEnvName,
                    hosts = emptyList()
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
        assertThat(config.rumConfig!!)
            .hasClientToken(fakeClientToken)
            .hasApplicationId(fakeApplicationId)
            .hasEndpointUrl(DatadogEndpoint.RUM_US)
            .hasEnvName(fakeEnvName)
            .hasSamplingRate(sampling)
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

        assertThat(config.rumConfig!!)
            .hasClientToken(fakeClientToken)
            .hasApplicationId(fakeApplicationId)
            .hasEndpointUrl(DatadogEndpoint.RUM_US)
            .hasEnvName(fakeEnvName)
            .hasPlugins(listOf(rumPlugin))
    }

    @Test
    fun `M do nothing W enabling RUM { APP_ID not provided }`() {
        // GIVEN
        val configBuilder = DatadogConfig.Builder(fakeClientToken, fakeEnvName)
        reset(mockDevLogHandler)

        // WHEN
        val config = configBuilder.setRumEnabled(true).build()

        // THEN
        assertThat(config.rumConfig).isNull()
        verify(mockDevLogHandler).handleLog(
            Log.WARN,
            Datadog.WARNING_MESSAGE_APPLICATION_ID_IS_NULL
        )
    }

    @Test
    fun `M not send any warning W disabling RUM { APP_ID not provided }`() {
        // GIVEN
        val configBuilder = DatadogConfig.Builder(fakeClientToken, fakeEnvName)
        reset(mockDevLogHandler)

        // WHEN
        val config = configBuilder.setRumEnabled(false).build()

        // THEN
        assertThat(config.rumConfig).isNull()
        verifyZeroInteractions(mockDevLogHandler)
    }

    @Test
    fun `M provide the update mapper into RUM config W all event mappers are set`() {
        // GIVEN
        val mockActionEventMapper: EventMapper<ActionEvent> = mock()
        val mockViewEventMapper: ViewEventMapper = mock()
        val mockResourceEventMapper: EventMapper<ResourceEvent> = mock()
        val mockErrorEventMapper: EventMapper<ErrorEvent> = mock()

        // WHEN
        val config =
            DatadogConfig.Builder(fakeClientToken, fakeEnvName, fakeApplicationId)
                .setRumViewEventMapper(mockViewEventMapper)
                .setRumActionEventMapper(mockActionEventMapper)
                .setRumResourceEventMapper(mockResourceEventMapper)
                .setRumErrorEventMapper(mockErrorEventMapper)
                .build()

        // THEN
        assertThat(config.rumConfig).isNotNull()
        assertThat(config.rumConfig?.rumEventMapper?.viewEventMapper)
            .isEqualTo(mockViewEventMapper)
        assertThat(config.rumConfig?.rumEventMapper?.actionEventMapper)
            .isEqualTo(
                mockActionEventMapper
            )
        assertThat(config.rumConfig?.rumEventMapper?.errorEventMapper)
            .isEqualTo(
                mockErrorEventMapper
            )
        assertThat(config.rumConfig?.rumEventMapper?.resourceEventMapper)
            .isEqualTo(
                mockResourceEventMapper
            )
    }

    @Test
    fun `M add the event mapper into the RUM config W { setRumViewEventMapper }`() {
        // GIVEN
        val mockMapper: ViewEventMapper = mock()
        // WHEN
        val config =
            DatadogConfig.Builder(fakeClientToken, fakeEnvName, fakeApplicationId)
                .setRumViewEventMapper(mockMapper)
                .build()

        // THEN
        assertThat(config.rumConfig).isNotNull()
        assertThat(config.rumConfig?.rumEventMapper?.viewEventMapper).isEqualTo(mockMapper)
    }

    @Test
    fun `M add the event mapper into the RUM config W { setRumResourceEventMapper }`() {
        // GIVEN
        val mockMapper: EventMapper<ResourceEvent> = mock()

        // WHEN
        val config =
            DatadogConfig.Builder(fakeClientToken, fakeEnvName, fakeApplicationId)
                .setRumResourceEventMapper(mockMapper)
                .build()

        // THEN
        assertThat(config.rumConfig?.rumEventMapper?.resourceEventMapper).isEqualTo(mockMapper)
    }

    @Test
    fun `M add the event mapper into the RUM config W { setRumErrorEventMapper }`() {
        // GIVEN
        val mockMapper: EventMapper<ErrorEvent> = mock()

        // WHEN
        val config =
            DatadogConfig.Builder(fakeClientToken, fakeEnvName, fakeApplicationId)
                .setRumErrorEventMapper(mockMapper)
                .build()

        // THEN
        assertThat(config.rumConfig?.rumEventMapper?.errorEventMapper).isEqualTo(mockMapper)
    }

    @Test
    fun `M add the event mapper into the RUM config W { setRumActionEventMapper }`() {
        // GIVEN
        val mockMapper: EventMapper<ActionEvent> = mock()

        // WHEN
        val config =
            DatadogConfig.Builder(fakeClientToken, fakeEnvName, fakeApplicationId)
                .setRumActionEventMapper(mockMapper)
                .build()

        // THEN
        assertThat(config.rumConfig).isNotNull()
        assertThat(config.rumConfig?.rumEventMapper?.actionEventMapper).isEqualTo(mockMapper)
    }

    // endregion
}
