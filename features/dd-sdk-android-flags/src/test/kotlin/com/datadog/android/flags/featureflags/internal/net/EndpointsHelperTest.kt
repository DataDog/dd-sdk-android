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

    // region buildEndpointHost - Supported sites

    @Test
    fun `M build US1 endpoint W buildEndpointHost() { US1 site }`(@StringForgery fakeCustomerDomain: String) {
        // When
        val result = EndpointsHelper.buildEndpointHost(DatadogSite.US1, mockInternalLogger, fakeCustomerDomain)

        // Then
        assertThat(result).isEqualTo("$fakeCustomerDomain.ff-cdn.datadoghq.com")
    }

    @Test
    fun `M build US1 endpoint W buildEndpointHost() { US1 site and no customer domain }`() {
        // When
        val result = EndpointsHelper.buildEndpointHost(DatadogSite.US1, mockInternalLogger)

        // Then
        assertThat(result).isEqualTo("preview.ff-cdn.datadoghq.com")
    }

    @Test
    fun `M build US3 endpoint W buildEndpointHost() { US3 site }`(@StringForgery fakeCustomerDomain: String) {
        // When
        val result = EndpointsHelper.buildEndpointHost(DatadogSite.US3, mockInternalLogger, fakeCustomerDomain)

        // Then
        assertThat(result).isEqualTo("$fakeCustomerDomain.ff-cdn.us3.datadoghq.com")
    }

    @Test
    fun `M build US5 endpoint W buildEndpointHost() { US5 site }`(@StringForgery fakeCustomerDomain: String) {
        // When
        val result = EndpointsHelper.buildEndpointHost(DatadogSite.US5, mockInternalLogger, fakeCustomerDomain)

        // Then
        assertThat(result).isEqualTo("$fakeCustomerDomain.ff-cdn.us5.datadoghq.com")
    }

    @Test
    fun `M build AP1 endpoint W buildEndpointHost() { AP1 site }`(@StringForgery fakeCustomerDomain: String) {
        // When
        val result = EndpointsHelper.buildEndpointHost(DatadogSite.AP1, mockInternalLogger, fakeCustomerDomain)

        // Then
        assertThat(result).isEqualTo("$fakeCustomerDomain.ff-cdn.ap1.datadoghq.com")
    }

    @Test
    fun `M build AP2 endpoint W buildEndpointHost() { AP2 site }`(@StringForgery fakeCustomerDomain: String) {
        // When
        val result = EndpointsHelper.buildEndpointHost(DatadogSite.AP2, mockInternalLogger, fakeCustomerDomain)

        // Then
        assertThat(result).isEqualTo("$fakeCustomerDomain.ff-cdn.ap2.datadoghq.com")
    }

    @Test
    fun `M build EU endpoint W buildEndpointHost() { EU site }`(@StringForgery fakeCustomerDomain: String) {
        // When
        val result = EndpointsHelper.buildEndpointHost(DatadogSite.EU1, mockInternalLogger, fakeCustomerDomain)

        // Then
        assertThat(result).isEqualTo("$fakeCustomerDomain.ff-cdn.datadoghq.eu")
    }

    // endregion

    // region buildEndpointHost - Special domains

    @Test
    fun `M build datad0g endpoint W buildEndpointHost() { datad0g site }`(@StringForgery fakeCustomerDomain: String) {
        // When
        val result = EndpointsHelper.buildEndpointHost(DatadogSite.STAGING, mockInternalLogger, fakeCustomerDomain)

        // Then
        assertThat(result).isEqualTo("$fakeCustomerDomain.ff-cdn.datad0g.com")
    }

    @Test
    fun `M build datad0g endpoint with default customer domain W buildEndpointHost() { datad0g site }`() {
        // When
        val result = EndpointsHelper.buildEndpointHost(DatadogSite.STAGING, mockInternalLogger)

        // Then
        assertThat(result).isEqualTo("preview.ff-cdn.datad0g.com")
    }

    // endregion

    // region buildEndpointHost - Error cases

    @Test
    fun `M return null and log error W buildEndpointHost() { gov site }`(@StringForgery fakeCustomerDomain: String) {
        // When
        val result = EndpointsHelper.buildEndpointHost(DatadogSite.US1_FED, mockInternalLogger, fakeCustomerDomain)

        // Then
        assertThat(result).isNull()
    }

    // endregion

    // region buildEndpointHost - Edge cases

    @Test
    fun `M handle empty customer domain W buildEndpointHost() { empty customer domain }`() {
        // When
        val result = EndpointsHelper.buildEndpointHost(DatadogSite.US1, mockInternalLogger, "")

        // Then
        assertThat(result).isEqualTo(".ff-cdn.datadoghq.com")
    }

    @Test
    fun `M handle special characters in customer domain W buildEndpointHost() { special characters }`() {
        // When
        val result = EndpointsHelper.buildEndpointHost(DatadogSite.US1, mockInternalLogger, "test-domain_123")

        // Then
        assertThat(result).isEqualTo("test-domain_123.ff-cdn.datadoghq.com")
    }

    // endregion

    // region getFlaggingEndpoint

    @Test
    fun `M return custom flag endpoint URL W getFlaggingEndpoint() {flagging endpoint configured}`(
        @StringForgery fakeApplicationId: String,
        @StringForgery fakeClientToken: String,
        @StringForgery fakeEnv: String,
        @StringForgery fakeFlagEndpoint: String
    ) {
        // Given
        val context = FlagsContext(
            applicationId = fakeApplicationId,
            clientToken = fakeClientToken,
            site = DatadogSite.US1,
            env = fakeEnv,
            customFlagEndpoint = fakeFlagEndpoint
        )

        // When
        val result = EndpointsHelper.getFlaggingEndpoint(context, mockInternalLogger)

        // Then
        assertThat(result).isEqualTo(fakeFlagEndpoint)
    }

    @Test
    fun `M build from site W getFlaggingEndpoint() {no flag endpoint configured}`(
        @StringForgery fakeApplicationId: String,
        @StringForgery fakeClientToken: String,
        @StringForgery fakeEnv: String
    ) {
        // Given
        val context = FlagsContext(
            applicationId = fakeApplicationId,
            clientToken = fakeClientToken,
            site = DatadogSite.US1,
            env = fakeEnv,
            customFlagEndpoint = null
        )

        // When
        val result = EndpointsHelper.getFlaggingEndpoint(context, mockInternalLogger)

        // Then
        assertThat(result).isEqualTo("https://preview.ff-cdn.datadoghq.com/precompute-assignments")
    }

    @Test
    fun `M return null W getFlaggingEndpoint() {unsupported site and no flag endpoint}`(
        @StringForgery fakeApplicationId: String,
        @StringForgery fakeClientToken: String,
        @StringForgery fakeEnv: String
    ) {
        // Given
        val context = FlagsContext(
            applicationId = fakeApplicationId,
            clientToken = fakeClientToken,
            site = DatadogSite.US1_FED,
            env = fakeEnv,
            customFlagEndpoint = null
        )

        // When
        val result = EndpointsHelper.getFlaggingEndpoint(context, mockInternalLogger)

        // Then
        assertThat(result).isNull()
    }

    // endregion
}
