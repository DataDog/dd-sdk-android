/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal

import com.datadog.android.DatadogSite
import com.datadog.android.api.InternalLogger
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.quality.Strictness

@ExtendWith(MockitoExtension::class, ForgeExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
internal class DatadogSiteExtensionsTest {

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    // region getFlagsEndpoint - With Custom Domain

    @ParameterizedTest
    @MethodSource("supportedSitesWithCustomDomain")
    fun `M build flags endpoint W getFlagsEndpoint() { supported sites with custom domain }`(
        site: DatadogSite,
        expectedHostSuffix: String,
        @StringForgery customerDomain: String
    ) {
        // When
        val result = site.getFlagsEndpoint(customerDomain, mockInternalLogger)

        // Then
        assertThat(result).isEqualTo("https://$customerDomain.$expectedHostSuffix/precompute-assignments")
    }

    // endregion

    // region getFlagsEndpoint - With Default Domain

    @ParameterizedTest
    @MethodSource("supportedSitesWithDefaultDomain")
    fun `M build flags endpoint W getFlagsEndpoint() { supported sites with default domain }`(
        site: DatadogSite,
        expectedHost: String
    ) {
        // When
        val result = site.getFlagsEndpoint(internalLogger = mockInternalLogger)

        // Then
        assertThat(result).isEqualTo("https://$expectedHost/precompute-assignments")
    }

    // endregion

    // region getFlagsEndpoint - Error Case

    @Test
    fun `M return null and log error W getFlagsEndpoint() { unsupported site }`(@StringForgery customerDomain: String) {
        // When
        val result =
            DatadogSite.US1_FED.getFlagsEndpoint(customerDomain, mockInternalLogger)

        // Then
        assertThat(result).isNull()
        verify(mockInternalLogger).log(
            eq(InternalLogger.Level.ERROR),
            eq(InternalLogger.Target.MAINTAINER),
            org.mockito.kotlin.any(),
            org.mockito.kotlin.anyOrNull(),
            org.mockito.kotlin.any(),
            org.mockito.kotlin.anyOrNull()
        )
    }

    // endregion

    // region getFlagsEndpoint - Edge Cases

    @ParameterizedTest
    @MethodSource("edgeCaseCustomerDomains")
    fun `M handle edge case customer domains W getFlagsEndpoint() { various edge cases }`(
        site: DatadogSite,
        customerDomain: String,
        expectedHost: String
    ) {
        // When
        val result = site.getFlagsEndpoint(customerDomain, mockInternalLogger)

        // Then
        assertThat(result).isEqualTo("https://$expectedHost/precompute-assignments")
    }

    // endregion

    companion object {
        @Suppress("unused")
        @JvmStatic
        fun supportedSitesWithCustomDomain(): List<Arguments> = listOf(
            Arguments.of(DatadogSite.US1, "ff-cdn.datadoghq.com"),
            Arguments.of(DatadogSite.US3, "ff-cdn.us3.datadoghq.com"),
            Arguments.of(DatadogSite.US5, "ff-cdn.us5.datadoghq.com"),
            Arguments.of(DatadogSite.AP1, "ff-cdn.ap1.datadoghq.com"),
            Arguments.of(DatadogSite.AP2, "ff-cdn.ap2.datadoghq.com"),
            Arguments.of(DatadogSite.EU1, "ff-cdn.datadoghq.eu"),
            Arguments.of(DatadogSite.STAGING, "ff-cdn.datad0g.com")
        )

        @Suppress("unused")
        @JvmStatic
        fun supportedSitesWithDefaultDomain(): List<Arguments> = listOf(
            Arguments.of(DatadogSite.US1, "preview.ff-cdn.datadoghq.com"),
            Arguments.of(DatadogSite.EU1, "preview.ff-cdn.datadoghq.eu"),
            Arguments.of(DatadogSite.STAGING, "preview.ff-cdn.datad0g.com")
        )

        @Suppress("unused")
        @JvmStatic
        fun edgeCaseCustomerDomains(): List<Arguments> = listOf(
            // Domain with hyphens and underscores (special characters)
            Arguments.of(DatadogSite.US1, "test-domain_123", "test-domain_123.ff-cdn.datadoghq.com"),
            // Numeric-only domain
            Arguments.of(DatadogSite.US3, "12345", "12345.ff-cdn.us3.datadoghq.com"),
            // Domain with dots (subdomain-like)
            Arguments.of(DatadogSite.EU1, "my.customer.domain", "my.customer.domain.ff-cdn.datadoghq.eu")
        )
    }
}
