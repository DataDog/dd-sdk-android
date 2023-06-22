/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.configuration

import com.datadog.android.utils.config.InternalLoggerTestConfiguration
import com.datadog.android.v2.api.InternalLogger
import com.datadog.tools.unit.annotations.TestConfigurationsProvider
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import com.datadog.tools.unit.extensions.config.TestConfiguration
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.annotation.StringForgeryType
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import java.net.MalformedURLException
import java.net.URL
import java.util.Locale

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class),
    ExtendWith(TestConfigurationExtension::class)
)
internal class HostsSanitizerTest {

    lateinit var testedSanitizer: HostsSanitizer

    @StringForgery(type = StringForgeryType.ALPHABETICAL)
    lateinit var fakeFeature: String

    @BeforeEach
    fun `set up`() {
        testedSanitizer = HostsSanitizer()
    }

    @Test
    fun `M return the whole hosts list W valid IPs list provided`(
        @StringForgery(
            regex = "(([0-9]|[1-9][0-9]|1[0-9]{2}|2[0-4][0-9]|25[0-5])\\.){3}" +
                "([0-9]|[1-9][0-9]|1[0-9]{2}|2[0-4][0-9]|25[0-5])"
        ) hosts: List<String>
    ) {
        // When
        val validHosts = testedSanitizer.sanitizeHosts(hosts, fakeFeature)

        // Then
        assertThat(validHosts).isEqualTo(hosts)
    }

    @Test
    fun `M return the whole hosts list W sanitizeHosts { valid hosts used }`(
        @StringForgery(
            regex = "(([a-zA-Z0-9]|[a-zA-Z0-9][a-zA-Z0-9-]*[a-zA-Z0-9])\\.)+" +
                "([A-Za-z]|[A-Za-z][A-Za-z0-9-]*[A-Za-z0-9])"
        ) hosts: List<String>
    ) {
        // When
        val validHosts = testedSanitizer.sanitizeHosts(hosts, fakeFeature)

        // Then
        assertThat(validHosts).isEqualTo(hosts)
    }

    @Test
    fun `𝕄 filter out everything W sanitizeHosts { using top level domains only }`(
        @StringForgery(
            regex = "([A-Za-z]|[A-Za-z][A-Za-z0-9-]*[A-Za-z0-9])"
        ) hosts: List<String>
    ) {
        // When
        val filtered = testedSanitizer.sanitizeHosts(hosts, fakeFeature)

        // Then
        assertThat(filtered).isEmpty()
    }

    @Test
    fun `𝕄 return only localhost W sanitizeHosts { using top level domains and localhost }`(
        @StringForgery(
            regex = "([A-Za-z]|[A-Za-z][A-Za-z0-9-]*[A-Za-z0-9])"
        ) hosts: List<String>,
        forge: Forge
    ) {
        // Given
        val fakeLocalHost = forge.aStringMatching("localhost|LOCALHOST")

        // When
        val sanitizedHosts = testedSanitizer.sanitizeHosts(
            hosts + fakeLocalHost,
            fakeFeature
        )

        // Then
        assertThat(sanitizedHosts).containsOnly(fakeLocalHost)
    }

    @Test
    fun `M log error W sanitizeHosts { malformed hostname }`(
        @StringForgery(
            regex = "(([-+=~><?][a-zA-Z0-9-]*[a-zA-Z0-9])\\.)+([-+=~><?][A-Za-z0-9]*)" +
                "|(([a-zA-Z0-9-]*[a-zA-Z0-9])\\.)+([A-Za-z0-9]*[-+=~><?])"
        ) hosts: List<String>
    ) {
        // When
        testedSanitizer.sanitizeHosts(
            hosts,
            fakeFeature
        )

        // Then
        hosts.forEach {
            verify(logger.mockInternalLogger).log(
                InternalLogger.Level.ERROR,
                InternalLogger.Target.USER,
                HostsSanitizer.ERROR_MALFORMED_HOST_IP_ADDRESS.format(
                    Locale.US,
                    it,
                    fakeFeature
                )
            )
        }
    }

    @Test
    fun `M log error W sanitizeHosts { malformed ip address }`(
        @StringForgery(
            regex = "(([0-9]{3}\\.){3}[0.9]{4})" +
                "|(([0-9]{4,9}\\.)[0.9]{4})" +
                "|(25[6-9]\\.([0-9]{3}\\.){2}[0.9]{3})"
        ) hosts: List<String>
    ) {
        // When
        testedSanitizer.sanitizeHosts(
            hosts,
            fakeFeature
        )

        // THEN
        hosts.forEach {
            verify(logger.mockInternalLogger).log(
                InternalLogger.Level.ERROR,
                InternalLogger.Target.USER,
                HostsSanitizer.ERROR_MALFORMED_HOST_IP_ADDRESS.format(
                    Locale.US,
                    it,
                    fakeFeature
                )
            )
        }
    }

    @Test
    fun `M drop all malformed hosts W sanitizeHosts { malformed hostname }`(
        @StringForgery(
            regex = "(([-+=~><?][a-zA-Z0-9-]*[a-zA-Z0-9])\\.)+([-+=~><?][A-Za-z0-9]*) " +
                "| (([a-zA-Z0-9-]*[a-zA-Z0-9])\\.)+([A-Za-z0-9]*[-+=~><?])"
        ) hosts: List<String>
    ) {
        // When
        val sanitizedHosts = testedSanitizer.sanitizeHosts(
            hosts,
            fakeFeature
        )

        // Then
        assertThat(sanitizedHosts).isEmpty()
    }

    @Test
    fun `M drop all malformed ip addresses W sanitizeHosts { malformed ip address }`(
        @StringForgery(
            regex = "(([0-9]{3}\\.){3}[0.9]{4})" +
                "|(([0-9]{4,9}\\.)[0.9]{4})" +
                "|(25[6-9]\\.([0-9]{3}\\.){2}[0.9]{3})"
        ) hosts: List<String>
    ) {
        // When
        val sanitizedHosts = testedSanitizer.sanitizeHosts(
            hosts,
            fakeFeature
        )

        // Then
        assertThat(sanitizedHosts).isEmpty()
    }

    @Test
    fun `M use url host name W sanitizeHosts { url }`(
        @StringForgery(
            regex = "(https|http)://([a-z][a-z0-9-]{3,9}\\.){1,4}[a-z][a-z0-9]{2,3}"
        ) hosts: List<String>
    ) {
        // When
        val sanitizedHosts = testedSanitizer.sanitizeHosts(
            hosts,
            fakeFeature
        )

        // Then
        assertThat(sanitizedHosts).isEqualTo(hosts.map { URL(it).host })
    }

    @Test
    fun `M warn W sanitizeHosts { url }`(
        @StringForgery(
            regex = "(https|http)://([a-z][a-z0-9-]{3,9}\\.){1,4}[a-z][a-z0-9]{2,3}"
        ) hosts: List<String>
    ) {
        // When
        testedSanitizer.sanitizeHosts(
            hosts,
            fakeFeature
        )

        // THEN
        hosts.forEach {
            verify(logger.mockInternalLogger).log(
                InternalLogger.Level.WARN,
                InternalLogger.Target.USER,
                HostsSanitizer.WARNING_USING_URL.format(
                    Locale.US,
                    it,
                    fakeFeature,
                    URL(it).host
                )
            )
        }
    }

    @Test
    fun `M warn W sanitizeHosts { malformed url }`(
        @StringForgery(
            regex = "(https|http)://([a-z][a-z0-9-]{3,9}\\.){1,4}[a-z][a-z0-9]{2,3}:-8[0-9]{1}"
        ) hosts: List<String>
    ) {
        // When
        testedSanitizer.sanitizeHosts(
            hosts,
            fakeFeature
        )

        // Then
        hosts.forEach {
            verify(logger.mockInternalLogger).log(
                eq(InternalLogger.Level.ERROR),
                eq(InternalLogger.Target.USER),
                eq(
                    HostsSanitizer.ERROR_MALFORMED_URL.format(
                        Locale.US,
                        it,
                        fakeFeature
                    )
                ),
                any<MalformedURLException>(),
                eq(false)
            )
        }
    }

    @Test
    fun `M drop all malformed urls W sanitizeHosts`(
        @StringForgery(
            regex = "(https|http)://([a-z][a-z0-9-]{3,9}\\.){1,4}[a-z][a-z0-9]{2,3}:-8[0-9]{1}"
        ) hosts: List<String>
    ) {
        // When
        val sanitizedHosts = testedSanitizer.sanitizeHosts(
            hosts,
            fakeFeature
        )

        // Then
        assertThat(sanitizedHosts).isEmpty()
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
