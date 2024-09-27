/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.configuration

import com.datadog.android.DatadogSite
import com.datadog.android.core.persistence.PersistenceStrategy
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
import fr.xgouchet.elmyr.annotation.IntForgery
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
    fun `M use sensible defaults W build()`() {
        // When
        val config = testedBuilder.build()

        // Then
        assertThat(config.coreConfig.needsClearTextHttp).isFalse()
        assertThat(config.coreConfig.enableDeveloperModeWhenDebuggable).isFalse()
        assertThat(config.coreConfig.firstPartyHostsWithHeaderTypes).isEmpty()
        assertThat(config.coreConfig.batchSize).isEqualTo(BatchSize.MEDIUM)
        assertThat(config.coreConfig.uploadFrequency).isEqualTo(UploadFrequency.AVERAGE)
        assertThat(config.coreConfig.proxy).isNull()
        assertThat(config.coreConfig.proxyAuth).isEqualTo(Authenticator.NONE)
        assertThat(config.coreConfig.encryption).isNull()
        assertThat(config.coreConfig.site).isEqualTo(DatadogSite.US1)
        assertThat(config.coreConfig.batchProcessingLevel).isEqualTo(BatchProcessingLevel.MEDIUM)
        assertThat(config.coreConfig.persistenceStrategyFactory).isNull()
        assertThat(config.coreConfig.backpressureStrategy.backpressureMitigation)
            .isEqualTo(BackPressureMitigation.IGNORE_NEWEST)
        assertThat(config.coreConfig.backpressureStrategy.capacity).isEqualTo(1024)
        assertThat(config.crashReportsEnabled).isTrue
        assertThat(config.additionalConfig).isEmpty()
    }

    @Test
    fun `M build config without crashReportConfig W build() { crashReports disabled }`(
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
    fun `M build config with custom site W useSite() and build()`(
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
    fun `M build config with first party hosts W setFirstPartyHosts() { ip addresses }`(
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
    fun `M build config with first party hosts W setFirstPartyHosts() { host names }`(
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
    fun `M sanitize hosts W setFirstPartyHosts()`(
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
    fun `M build config with first party hosts and header types W setFirstPartyHostsWithHeaderType() { host names }`(
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
    fun `M use batch size W setBatchSize()`(
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
    fun `M use upload frequency W setUploadFrequency()`(
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
    fun `M build with additionalConfig W setAdditionalConfiguration()`(forge: Forge) {
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
    fun `M build config with Proxy and Auth configuration W setProxy() and build()`() {
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
    fun `M build config with Proxy configuration W setProxy() and build()`() {
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
    fun `M build config with security configuration W setEncryption() and build()`() {
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

    @Test
    fun `M build config with persistence strategy W setPersistenceStrategyFactory() and build()`() {
        // Given
        val mockFactory = mock<PersistenceStrategy.Factory>()

        // When
        val config = testedBuilder
            .setPersistenceStrategyFactory(mockFactory)
            .build()

        // Then
        assertThat(config.coreConfig).isEqualTo(
            Configuration.DEFAULT_CORE_CONFIG.copy(
                persistenceStrategyFactory = mockFactory
            )
        )
    }

    @Test
    fun `M build config with BackPressure strategy W setBackpressureStrategy() and build()`(
        @IntForgery capacity: Int,
        @Forgery mitigation: BackPressureMitigation
    ) {
        // Given
        val fakeBackpressureStrategy = BackPressureStrategy(
            capacity,
            mock<() -> Unit>(),
            mock<(Any) -> Unit>(),
            mitigation
        )

        // When
        val config = testedBuilder
            .setBackpressureStrategy(fakeBackpressureStrategy)
            .build()

        // Then
        assertThat(config.coreConfig).isEqualTo(
            Configuration.DEFAULT_CORE_CONFIG.copy(
                backpressureStrategy = fakeBackpressureStrategy
            )
        )
    }

    @Test
    fun `M build config with UploadScheduler strategy W setUploadSchedulerStrategy() and build()`() {
        // Given
        val mockUploadSchedulerStrategy: UploadSchedulerStrategy = mock()

        // When
        val config = testedBuilder
            .setUploadSchedulerStrategy(mockUploadSchedulerStrategy)
            .build()

        // Then
        assertThat(config.coreConfig).isEqualTo(
            Configuration.DEFAULT_CORE_CONFIG.copy(
                uploadSchedulerStrategy = mockUploadSchedulerStrategy
            )
        )
    }

    @Test
    fun `M build config with allowClearTextHttp W allowClearTextHttp() and build()`() {
        // When
        val config = testedBuilder
            .allowClearTextHttp()
            .build()

        // Then
        assertThat(config.coreConfig).isEqualTo(
            Configuration.DEFAULT_CORE_CONFIG.copy(
                needsClearTextHttp = true
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
