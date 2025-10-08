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
internal class DatadogSiteExtensionTest {

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    // region getFlagsEndpoint - With Custom Domain

    @ParameterizedTest
    @MethodSource("supportedSitesWithCustomDomain")
    fun `M build flags endpoint W getFlagsEndpoint() { supported sites with custom domain }`(
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

    // region getFlagsEndpoint - Error Cases

    @ParameterizedTest
    @MethodSource("unsupportedSitesTestCases")
    fun `M return null and log error W getFlagsEndpoint() { unsupported site }`(
        site: DatadogSite,
        customerDomain: String?
    ) {
        // When
        val result = if (customerDomain != null) {
            site.getFlagsEndpoint(customerDomain, mockInternalLogger)
        } else {
            site.getFlagsEndpoint(internalLogger = mockInternalLogger)
        }

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

    @Test
    fun `M handle empty customer domain W getFlagsEndpoint() { empty customer domain }`() {
        // When
        val result = DatadogSite.US1.getFlagsEndpoint("", mockInternalLogger)

        // Then
        assertThat(result).isEqualTo("https://.ff-cdn.datadoghq.com/precompute-assignments")
    }

    @Test
    fun `M handle special characters in customer domain W getFlagsEndpoint() { special characters }`() {
        // When
        val result = DatadogSite.US1.getFlagsEndpoint("test-domain_123", mockInternalLogger)

        // Then
        assertThat(result).isEqualTo("https://test-domain_123.ff-cdn.datadoghq.com/precompute-assignments")
    }

    @Test
    fun `M handle numeric customer domain W getFlagsEndpoint() { numeric domain }`() {
        // When
        val result = DatadogSite.US3.getFlagsEndpoint("12345", mockInternalLogger)

        // Then
        assertThat(result).isEqualTo("https://12345.ff-cdn.us3.datadoghq.com/precompute-assignments")
    }

    // endregion

    companion object {
        @Suppress("unused")
        @JvmStatic
        fun supportedSitesWithCustomDomain(): List<Arguments> = listOf(
            Arguments.of(DatadogSite.US1, "test", "test.ff-cdn.datadoghq.com"),
            Arguments.of(DatadogSite.US3, "test", "test.ff-cdn.us3.datadoghq.com"),
            Arguments.of(DatadogSite.US5, "test", "test.ff-cdn.us5.datadoghq.com"),
            Arguments.of(DatadogSite.AP1, "test", "test.ff-cdn.ap1.datadoghq.com"),
            Arguments.of(DatadogSite.AP2, "test", "test.ff-cdn.ap2.datadoghq.com"),
            Arguments.of(DatadogSite.EU1, "test", "test.ff-cdn.datadoghq.eu"),
            Arguments.of(DatadogSite.STAGING, "test", "test.ff-cdn.datad0g.com")
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
        fun unsupportedSitesTestCases(): List<Arguments> = listOf(
            Arguments.of(DatadogSite.US1_FED, "test"),
            Arguments.of(DatadogSite.US1_FED, null)
        )
    }
}
