/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.featureflags.internal.net

import com.datadog.android.DatadogSite
import com.datadog.android.api.InternalLogger
import com.datadog.android.flags.featureflags.internal.model.FlagsContext
import com.datadog.android.flags.featureflags.internal.repository.net.EndpointsHelper
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness

@ExtendWith(MockitoExtension::class, ForgeExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
internal class EndpointsHelperTest {

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    private lateinit var testedHelper: EndpointsHelper

    @BeforeEach
    fun `set up`() {
        val mockFlagsContext = FlagsContext(
            applicationId = "test-app-id",
            clientToken = "test-token",
            site = "US1",
            env = "test"
        )
        testedHelper = EndpointsHelper(mockFlagsContext, mockInternalLogger)
    }

    // region buildEndpointHost - Supported sites

    @Test
    fun `M build US1 endpoint W buildEndpointHost() { US1 site }`(@StringForgery fakeCustomerDomain: String) {
        // When
        val result = testedHelper.buildEndpointHost(DatadogSite.US1, fakeCustomerDomain)

        // Then
        assertThat(result).isEqualTo("$fakeCustomerDomain.ff-cdn.datadoghq.com")
    }

    @Test
    fun `M build US1 endpoint W buildEndpointHost() { US1 site and no customer domain }`() {
        // When
        val result = testedHelper.buildEndpointHost(DatadogSite.US1)

        // Then
        assertThat(result).isEqualTo("preview.ff-cdn.datadoghq.com")
    }

    @Test
    fun `M build US3 endpoint W buildEndpointHost() { US3 site }`(@StringForgery fakeCustomerDomain: String) {
        // When
        val result = testedHelper.buildEndpointHost(DatadogSite.US3, fakeCustomerDomain)

        // Then
        assertThat(result).isEqualTo("$fakeCustomerDomain.ff-cdn.us3.datadoghq.com")
    }

    @Test
    fun `M build US5 endpoint W buildEndpointHost() { US5 site }`(@StringForgery fakeCustomerDomain: String) {
        // When
        val result = testedHelper.buildEndpointHost(DatadogSite.US5, fakeCustomerDomain)

        // Then
        assertThat(result).isEqualTo("$fakeCustomerDomain.ff-cdn.us5.datadoghq.com")
    }

    @Test
    fun `M build AP1 endpoint W buildEndpointHost() { AP1 site }`(@StringForgery fakeCustomerDomain: String) {
        // When
        val result = testedHelper.buildEndpointHost(DatadogSite.AP1, fakeCustomerDomain)

        // Then
        assertThat(result).isEqualTo("$fakeCustomerDomain.ff-cdn.ap1.datadoghq.com")
    }

    @Test
    fun `M build AP2 endpoint W buildEndpointHost() { AP2 site }`(@StringForgery fakeCustomerDomain: String) {
        // When
        val result = testedHelper.buildEndpointHost(DatadogSite.AP2, fakeCustomerDomain)

        // Then
        assertThat(result).isEqualTo("$fakeCustomerDomain.ff-cdn.ap2.datadoghq.com")
    }

    @Test
    fun `M build EU endpoint W buildEndpointHost() { EU site }`(@StringForgery fakeCustomerDomain: String) {
        // When
        val result = testedHelper.buildEndpointHost(DatadogSite.EU1, fakeCustomerDomain)

        // Then
        assertThat(result).isEqualTo("$fakeCustomerDomain.ff-cdn.datadoghq.eu")
    }

    // endregion

    // region buildEndpointHost - Special domains

    @Test
    fun `M build datad0g endpoint W buildEndpointHost() { datad0g site }`(@StringForgery fakeCustomerDomain: String) {
        // When
        val result = testedHelper.buildEndpointHost(DatadogSite.STAGING, fakeCustomerDomain)

        // Then
        assertThat(result).isEqualTo("$fakeCustomerDomain.ff-cdn.datad0g.com")
    }

    @Test
    fun `M build datad0g endpoint with default customer domain W buildEndpointHost() { datad0g site }`() {
        // When
        val result = testedHelper.buildEndpointHost(DatadogSite.STAGING)

        // Then
        assertThat(result).isEqualTo("preview.ff-cdn.datad0g.com")
    }

    // endregion

    // region buildEndpointHost - Error cases

    @Test
    fun `M return empty string and log error W buildEndpointHost() { gov site }`(
        @StringForgery fakeCustomerDomain: String
    ) {
        // When
        val result = testedHelper.buildEndpointHost(DatadogSite.US1_FED, fakeCustomerDomain)

        // Then
        assertThat(result).isEmpty()
    }

    // endregion

    // region buildEndpointHost - Edge cases

    @Test
    fun `M handle empty customer domain W buildEndpointHost() { empty customer domain }`() {
        // When
        val result = testedHelper.buildEndpointHost(DatadogSite.US1, "")

        // Then
        assertThat(result).isEqualTo(".ff-cdn.datadoghq.com")
    }

    @Test
    fun `M handle special characters in customer domain W buildEndpointHost() { special characters }`() {
        // When
        val result = testedHelper.buildEndpointHost(DatadogSite.US1, "test-domain_123")

        // Then
        assertThat(result).isEqualTo("test-domain_123.ff-cdn.datadoghq.com")
    }

    // endregion
}
