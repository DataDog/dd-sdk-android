/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.configuration

import com.datadog.android.DatadogSite
import com.datadog.android.security.Encryption
import com.datadog.android.trace.TracingHeaderType
import com.datadog.android.utils.config.InternalLoggerTestConfiguration
import com.datadog.android.utils.forge.Configurator
import com.datadog.tools.unit.annotations.TestConfigurationsProvider
import com.datadog.tools.unit.extensions.ApiLevelExtension
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import com.datadog.tools.unit.extensions.config.TestConfiguration
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.BoolForgery
import fr.xgouchet.elmyr.annotation.Forgery
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
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import java.net.Proxy
import java.net.URL

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
    fun `set up`(forge: Forge) {
        testedBuilder = Configuration.Builder(
            clientToken = forge.anHexadecimalString(),
            env = forge.aStringMatching("[a-zA-Z0-9_:./-]{0,195}[a-zA-Z0-9_./-]"),
            variant = forge.anElementFrom(forge.anAlphabeticalString(), ""),
            service = forge.aStringMatching("[a-z]+(\\.[a-z]+)+")
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
                site = DatadogSite.US1,
                batchProcessingLevel = BatchProcessingLevel.MEDIUM
            )
        )
        assertThat(config.crashReportsEnabled).isTrue
        assertThat(config.additionalConfig).isEmpty()
    }

    @Test
    fun `𝕄 build config without crashReportConfig 𝕎 build() { crashReports disabled }`(
        forge: Forge
    ) {
        // Given
        testedBuilder = Configuration.Builder(
            clientToken = forge.anHexadecimalString(),
            env = forge.aStringMatching("[a-zA-Z0-9_:./-]{0,195}[a-zA-Z0-9_./-]"),
            variant = forge.anElementFrom(forge.anAlphabeticalString(), ""),
            service = forge.aStringMatching("[a-z]+(\\.[a-z]+)+")
        )
            .setCrashReportsEnabled(false)

        // When
        val config = testedBuilder.build()

        // Then
        assertThat(config.crashReportsEnabled).isFalse
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
        assertThat(config.additionalConfig).isEmpty()
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
                hosts.associateWith {
                    setOf(
                        TracingHeaderType.DATADOG,
                        TracingHeaderType.TRACECONTEXT
                    )
                }
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
                        TracingHeaderType.DATADOG,
                        TracingHeaderType.TRACECONTEXT
                    )
                }
            )
        )
        assertThat(config.crashReportsEnabled).isTrue
        assertThat(config.additionalConfig).isEmpty()
    }

    @Test
    fun `𝕄 use url host name 𝕎 setFirstPartyHosts() { url }`(
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
                hosts.associate {
                    URL(it).host to setOf(
                        TracingHeaderType.DATADOG,
                        TracingHeaderType.TRACECONTEXT
                    )
                }
            )
        )
        assertThat(config.crashReportsEnabled).isTrue
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
        assertThat(config.crashReportsEnabled).isTrue
        assertThat(config.additionalConfig).isEmpty()
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
        assertThat(config.crashReportsEnabled).isTrue
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
        assertThat(config.crashReportsEnabled).isTrue
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
        assertThat(config.crashReportsEnabled).isTrue
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
