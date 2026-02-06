/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.openfeature

import com.datadog.android.flags.FlagsClient
import com.datadog.android.flags.model.ResolutionDetails
import com.datadog.android.flags.model.ResolutionReason
import com.datadog.tools.unit.forge.BaseConfigurator
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
@ForgeConfiguration(BaseConfigurator::class)
internal class FlagsClientExtTest {

    @Mock
    lateinit var mockFlagsClient: FlagsClient

    @Test
    fun `M return DatadogFlagsProvider W asOpenFeatureProvider()`() {
        // When
        val result = mockFlagsClient.asOpenFeatureProvider()

        // Then
        assertThat(result).isInstanceOf(DatadogFlagsProvider::class.java)
        assertThat(result.metadata.name).isEqualTo("Datadog Feature Flags Provider")
    }

    @Test
    fun `M delegate to wrapped client W asOpenFeatureProvider() { boolean evaluation }`(
        forge: Forge,
        @StringForgery flagKey: String
    ) {
        // Given
        val expectedValue = forge.aBool()
        val defaultValue = forge.aBool()
        val resolution = ResolutionDetails(
            value = expectedValue,
            reason = forge.aValueFrom(ResolutionReason::class.java)
        )
        whenever(mockFlagsClient.resolve(flagKey, defaultValue)).thenReturn(resolution)

        // When
        val provider = mockFlagsClient.asOpenFeatureProvider()
        val result = provider.getBooleanEvaluation(flagKey, defaultValue, null)

        // Then
        assertThat(result.value).isEqualTo(expectedValue)
        assertThat(result.reason).isEqualTo(resolution.reason?.name)
    }

    @Test
    fun `M delegate to wrapped client W asOpenFeatureProvider() { string evaluation }`(
        forge: Forge,
        @StringForgery flagKey: String,
        @StringForgery expectedValue: String,
        @StringForgery defaultValue: String
    ) {
        // Given
        val resolution = ResolutionDetails(
            value = expectedValue,
            reason = forge.aValueFrom(ResolutionReason::class.java)
        )
        whenever(mockFlagsClient.resolve(flagKey, defaultValue)).thenReturn(resolution)

        // When
        val provider = mockFlagsClient.asOpenFeatureProvider()
        val result = provider.getStringEvaluation(flagKey, defaultValue, null)

        // Then
        assertThat(result.value).isEqualTo(expectedValue)
        assertThat(result.reason).isEqualTo(resolution.reason?.name)
    }

    @Test
    fun `M delegate to wrapped client W asOpenFeatureProvider() { integer evaluation }`(
        forge: Forge,
        @StringForgery flagKey: String
    ) {
        // Given
        val expectedValue = forge.anInt()
        val defaultValue = forge.anInt()
        val resolution = ResolutionDetails(
            value = expectedValue,
            reason = forge.aValueFrom(ResolutionReason::class.java)
        )
        whenever(mockFlagsClient.resolve(flagKey, defaultValue)).thenReturn(resolution)

        // When
        val provider = mockFlagsClient.asOpenFeatureProvider()
        val result = provider.getIntegerEvaluation(flagKey, defaultValue, null)

        // Then
        assertThat(result.value).isEqualTo(expectedValue)
        assertThat(result.reason).isEqualTo(resolution.reason?.name)
    }

    @Test
    fun `M delegate to wrapped client W asOpenFeatureProvider() { double evaluation }`(
        forge: Forge,
        @StringForgery flagKey: String
    ) {
        // Given
        val expectedValue = forge.aDouble()
        val defaultValue = forge.aDouble()
        val resolution = ResolutionDetails(
            value = expectedValue,
            reason = forge.aValueFrom(ResolutionReason::class.java)
        )
        whenever(mockFlagsClient.resolve(flagKey, defaultValue)).thenReturn(resolution)

        // When
        val provider = mockFlagsClient.asOpenFeatureProvider()
        val result = provider.getDoubleEvaluation(flagKey, defaultValue, null)

        // Then
        assertThat(result.value).isEqualTo(expectedValue)
        assertThat(result.reason).isEqualTo(resolution.reason?.name)
    }

    @Test
    fun `M return same provider type W asOpenFeatureProvider() { called multiple times }`() {
        // When
        val provider1 = mockFlagsClient.asOpenFeatureProvider()
        val provider2 = mockFlagsClient.asOpenFeatureProvider()

        // Then
        // Note: Each call creates a new provider instance wrapping the same client
        assertThat(provider1).isNotSameAs(provider2)
        assertThat(provider1).isInstanceOf(DatadogFlagsProvider::class.java)
        assertThat(provider2).isInstanceOf(DatadogFlagsProvider::class.java)
    }
}
