/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.featureflags.internal.model

import com.datadog.android.DatadogSite
import com.datadog.android.api.context.DatadogContext
import com.datadog.android.flags.FlagsConfiguration
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(ForgeExtension::class),
    ExtendWith(MockitoExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
internal class FlagsContextTest {

    @Mock
    lateinit var mockDatadogContext: DatadogContext

    @Mock
    lateinit var mockDatadogSite: DatadogSite

    @StringForgery
    lateinit var fakeApplicationId: String

    @StringForgery
    lateinit var fakeClientToken: String

    @StringForgery
    lateinit var fakeSiteName: String

    @StringForgery
    lateinit var fakeEnv: String

    @Test
    fun `M create FlagsContext with all parameters W create() { complete configuration }`(
        @StringForgery(regex = "https://[a-z]+\\.com(/[a-z]+)+") fakeCustomEndpoint: String,
        @StringForgery(regex = "https://[a-z]+\\.com(/[a-z]+)+") fakeProxyUrl: String
    ) {
        // Given
        whenever(mockDatadogContext.clientToken) doReturn fakeClientToken
        whenever(mockDatadogContext.site) doReturn mockDatadogSite
        whenever(mockDatadogSite.name) doReturn fakeSiteName
        whenever(mockDatadogContext.env) doReturn fakeEnv

        val flagsConfiguration = FlagsConfiguration.Builder()
            .setEnableExposureLogging(true)
            .useCustomEndpoint(fakeCustomEndpoint)
            .useFlaggingProxy(fakeProxyUrl)
            .build()

        // When
        val flagsContext = FlagsContext.create(mockDatadogContext, fakeApplicationId, flagsConfiguration)

        // Then
        assertThat(flagsContext.applicationId).isEqualTo(fakeApplicationId)
        assertThat(flagsContext.clientToken).isEqualTo(fakeClientToken)
        assertThat(flagsContext.site).isEqualTo(fakeSiteName)
        assertThat(flagsContext.env).isEqualTo(fakeEnv)
        assertThat(flagsContext.enableExposureLogging).isTrue()
        assertThat(flagsContext.customEndpointUrl).isEqualTo(fakeCustomEndpoint)
        assertThat(flagsContext.flaggingProxyUrl).isEqualTo(fakeProxyUrl)
    }

    @Test
    fun `M create FlagsContext with defaults W create() { minimal configuration }`() {
        // Given
        whenever(mockDatadogContext.clientToken) doReturn fakeClientToken
        whenever(mockDatadogContext.site) doReturn mockDatadogSite
        whenever(mockDatadogSite.name) doReturn fakeSiteName
        whenever(mockDatadogContext.env) doReturn fakeEnv

        val flagsConfiguration = FlagsConfiguration.Builder().build()

        // When
        val flagsContext = FlagsContext.create(mockDatadogContext, fakeApplicationId, flagsConfiguration)

        // Then
        assertThat(flagsContext.applicationId).isEqualTo(fakeApplicationId)
        assertThat(flagsContext.clientToken).isEqualTo(fakeClientToken)
        assertThat(flagsContext.site).isEqualTo(fakeSiteName)
        assertThat(flagsContext.env).isEqualTo(fakeEnv)
        assertThat(flagsContext.enableExposureLogging).isFalse()
        assertThat(flagsContext.customEndpointUrl).isNull()
        assertThat(flagsContext.flaggingProxyUrl).isNull()
    }

    @Test
    fun `M handle null application ID W create() { null app ID }`() {
        // Given
        whenever(mockDatadogContext.clientToken) doReturn fakeClientToken
        whenever(mockDatadogContext.site) doReturn mockDatadogSite
        whenever(mockDatadogSite.name) doReturn fakeSiteName
        whenever(mockDatadogContext.env) doReturn fakeEnv

        val flagsConfiguration = FlagsConfiguration.Builder().build()

        // When
        val flagsContext = FlagsContext.create(mockDatadogContext, null, flagsConfiguration)

        // Then
        assertThat(flagsContext.applicationId).isNull()
        assertThat(flagsContext.clientToken).isEqualTo(fakeClientToken)
        assertThat(flagsContext.site).isEqualTo(fakeSiteName)
        assertThat(flagsContext.env).isEqualTo(fakeEnv)
    }

    @Test
    fun `M handle null site W create() { null site }`() {
        // Given
        whenever(mockDatadogContext.clientToken) doReturn fakeClientToken
        whenever(mockDatadogContext.site).thenReturn(null)
        whenever(mockDatadogContext.env) doReturn fakeEnv

        val flagsConfiguration = FlagsConfiguration.Builder().build()

        // When
        val flagsContext = FlagsContext.create(mockDatadogContext, fakeApplicationId, flagsConfiguration)

        // Then
        assertThat(flagsContext.applicationId).isEqualTo(fakeApplicationId)
        assertThat(flagsContext.clientToken).isEqualTo(fakeClientToken)
        assertThat(flagsContext.site).isEmpty()
        assertThat(flagsContext.env).isEqualTo(fakeEnv)
    }

    @Test
    fun `M handle null env W create() { null env }`() {
        // Given
        whenever(mockDatadogContext.clientToken) doReturn fakeClientToken
        whenever(mockDatadogContext.site) doReturn mockDatadogSite
        whenever(mockDatadogSite.name) doReturn fakeSiteName
        whenever(mockDatadogContext.env).thenReturn(null)

        val flagsConfiguration = FlagsConfiguration.Builder().build()

        // When
        val flagsContext = FlagsContext.create(mockDatadogContext, fakeApplicationId, flagsConfiguration)

        // Then
        assertThat(flagsContext.applicationId).isEqualTo(fakeApplicationId)
        assertThat(flagsContext.clientToken).isEqualTo(fakeClientToken)
        assertThat(flagsContext.site).isEqualTo(fakeSiteName)
        assertThat(flagsContext.env).isEmpty()
    }
}
