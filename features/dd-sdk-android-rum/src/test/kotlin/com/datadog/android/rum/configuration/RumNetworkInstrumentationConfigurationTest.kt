/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.configuration

import com.datadog.android.internal.telemetry.InternalTelemetryEvent.ApiUsage.NetworkInstrumentation.LibraryType
import com.datadog.android.rum.NoOpRumResourceAttributesProvider
import com.datadog.android.rum.RumResourceAttributesProvider
import com.datadog.android.rum.resource.ResourceHeadersExtractor
import com.datadog.android.rum.utils.forge.Configurator
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class RumNetworkInstrumentationConfigurationTest {

    private lateinit var testedConfiguration: RumNetworkInstrumentationConfiguration

    @Mock
    lateinit var mockResourceAttributesProvider: RumResourceAttributesProvider

    @StringForgery
    lateinit var fakeInstrumentationName: String

    @Forgery
    private lateinit var fakeLibraryType: LibraryType

    @BeforeEach
    fun `set up`() {
        testedConfiguration = RumNetworkInstrumentationConfiguration()
    }

    @Test
    fun `M build with default values W createInstrumentation()`() {
        // When
        val result = testedConfiguration.createInstrumentation(fakeInstrumentationName, fakeLibraryType)

        // Then
        assertThat(result.sdkInstanceName).isNull()
        assertThat(result.networkInstrumentationName).isEqualTo(fakeInstrumentationName)
        assertThat(result.rumResourceAttributesProvider).isInstanceOf(NoOpRumResourceAttributesProvider::class.java)
    }

    @Test
    fun `M set SDK instance name W setSdkInstanceName()`(
        @StringForgery fakeSdkInstanceName: String
    ) {
        // When
        val result = testedConfiguration.setSdkInstanceName(
            fakeSdkInstanceName
        ).createInstrumentation(fakeInstrumentationName, fakeLibraryType)

        // Then
        assertThat(result.sdkInstanceName).isEqualTo(fakeSdkInstanceName)
    }

    @Test
    fun `M set resource attributes provider W setRumResourceAttributesProvider()`() {
        // When
        val result = testedConfiguration.setRumResourceAttributesProvider(mockResourceAttributesProvider)
            .createInstrumentation(fakeInstrumentationName, fakeLibraryType)

        // Then
        assertThat(result.rumResourceAttributesProvider).isSameAs(mockResourceAttributesProvider)
    }

    @Test
    fun `M have null resourceHeadersExtractor by default W createInstrumentation()`() {
        // When
        val result = testedConfiguration.createInstrumentation(fakeInstrumentationName, fakeLibraryType)

        // Then
        assertThat(result.resourceHeadersExtractor).isNull()
    }

    @Test
    fun `M set default extractor W trackResourceHeaders()`() {
        // When
        val result = testedConfiguration.trackResourceHeaders()
            .createInstrumentation(fakeInstrumentationName, fakeLibraryType)

        // Then
        assertThat(result.resourceHeadersExtractor).isNotNull
    }

    @Test
    fun `M set custom extractor W trackResourceHeaders(extractor)`() {
        // Given
        val customExtractor = ResourceHeadersExtractor.Builder(includeDefaults = false)
            .captureHeaders("x-request-id")
            .build()

        // When
        val result = testedConfiguration.trackResourceHeaders(customExtractor)
            .createInstrumentation(fakeInstrumentationName, fakeLibraryType)

        // Then
        assertThat(result.resourceHeadersExtractor).isSameAs(customExtractor)
    }

    @Test
    fun `M return self W chaining builder methods()`(
        @StringForgery fakeSdkInstanceName: String
    ) {
        // When
        val result = testedConfiguration
            .setSdkInstanceName(fakeSdkInstanceName)
            .setRumResourceAttributesProvider(mockResourceAttributesProvider)
            .trackResourceHeaders()

        // Then
        assertThat(result).isSameAs(testedConfiguration)
    }
}
