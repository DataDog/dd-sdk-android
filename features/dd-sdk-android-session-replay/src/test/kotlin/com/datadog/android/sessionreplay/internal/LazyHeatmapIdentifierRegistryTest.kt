/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.FeatureScope
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.internal.heatmaps.HeatmapIdentifier
import com.datadog.android.internal.heatmaps.HeatmapIdentifierRegistry
import com.datadog.android.internal.heatmaps.HeatmapIdentifierRegistryProvider
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.android.sessionreplay.internal.LazyHeatmapIdentifierRegistry.Companion.RUM_FEATURE_NOT_A_REGISTRY_PROVIDER
import com.datadog.android.utils.verifyLog
import fr.xgouchet.elmyr.annotation.LongForgery
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
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(ForgeConfigurator::class)
internal class LazyHeatmapIdentifierRegistryTest {

    private lateinit var testedRegistry: LazyHeatmapIdentifierRegistry

    @Mock
    lateinit var mockSdkCore: FeatureSdkCore

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @BeforeEach
    fun `set up`() {
        whenever(mockSdkCore.internalLogger).thenReturn(mockInternalLogger)
        testedRegistry = LazyHeatmapIdentifierRegistry(mockSdkCore)
    }

    // region RUM not registered — retries on every call

    @Test
    fun `M no-op W setHeatmapIdentifiers() { RUM not registered }`(
        @LongForgery fakeKey: Long,
        @StringForgery fakeScreenName: String
    ) {
        // Given
        whenever(mockSdkCore.getFeature(Feature.RUM_FEATURE_NAME)).thenReturn(null)

        // When
        testedRegistry.setHeatmapIdentifiers(mapOf(fakeKey to mock()), fakeScreenName)

        // Then — no crash, no warning logged (RUM just isn't up yet)
        verifyNoInteractions(mockInternalLogger)
    }

    @Test
    fun `M return null W getHeatmapIdentifier() { RUM not registered }`(
        @LongForgery fakeKey: Long,
        @StringForgery fakeScreenName: String
    ) {
        // Given
        whenever(mockSdkCore.getFeature(Feature.RUM_FEATURE_NAME)).thenReturn(null)

        // When
        val result = testedRegistry.getHeatmapIdentifier(fakeKey, fakeScreenName)

        // Then
        assertThat(result).isNull()
    }

    @Test
    fun `M retry getFeature on every call W RUM not yet registered`(
        @LongForgery fakeKey: Long,
        @StringForgery fakeScreenName: String
    ) {
        // Given
        whenever(mockSdkCore.getFeature(Feature.RUM_FEATURE_NAME)).thenReturn(null)

        // When
        testedRegistry.setHeatmapIdentifiers(mapOf(fakeKey to mock()), fakeScreenName)
        testedRegistry.getHeatmapIdentifier(fakeKey, fakeScreenName)

        // Then — keeps trying so it resolves as soon as RUM registers
        verify(mockSdkCore, times(2)).getFeature(Feature.RUM_FEATURE_NAME)
    }

    @Test
    fun `M delegate once RUM registers W calls before and after RUM registration`(
        @LongForgery fakeKey: Long,
        @StringForgery fakeScreenName: String
    ) {
        // Given — first call: RUM not yet registered
        val fakeIdentifier: HeatmapIdentifier = mock()
        whenever(mockSdkCore.getFeature(Feature.RUM_FEATURE_NAME)).thenReturn(null)
        testedRegistry.getHeatmapIdentifier(fakeKey, fakeScreenName)

        // RUM registers
        val mockRegistry: HeatmapIdentifierRegistry = mock()
        whenever(mockRegistry.getHeatmapIdentifier(fakeKey, fakeScreenName)).thenReturn(fakeIdentifier)
        val mockScope = stubRumFeatureWithRegistry(mockRegistry)
        whenever(mockSdkCore.getFeature(Feature.RUM_FEATURE_NAME)).thenReturn(mockScope)

        // When — second call after RUM is up
        val result = testedRegistry.getHeatmapIdentifier(fakeKey, fakeScreenName)

        // Then
        assertThat(result).isEqualTo(fakeIdentifier)
    }

    // endregion

    // region RUM registered but not HeatmapIdentifierRegistryProvider

    @Test
    fun `M return null W getHeatmapIdentifier() { RUM present but not a provider }`(
        @LongForgery fakeKey: Long,
        @StringForgery fakeScreenName: String
    ) {
        // Given
        val mockScope: FeatureScope = mock { whenever(it.unwrap<Feature>()).doReturn(mock<Feature>()) }
        whenever(mockSdkCore.getFeature(Feature.RUM_FEATURE_NAME)).thenReturn(mockScope)

        // When
        val result = testedRegistry.getHeatmapIdentifier(fakeKey, fakeScreenName)

        // Then
        assertThat(result).isNull()
    }

    @Test
    fun `M log MAINTAINER+TELEMETRY warning W { RUM present but not a provider }`(
        @LongForgery fakeKey: Long,
        @StringForgery fakeScreenName: String
    ) {
        // Given
        val mockScope: FeatureScope = mock { whenever(it.unwrap<Feature>()).doReturn(mock<Feature>()) }
        whenever(mockSdkCore.getFeature(Feature.RUM_FEATURE_NAME)).thenReturn(mockScope)

        // When
        testedRegistry.getHeatmapIdentifier(fakeKey, fakeScreenName)

        // Then
        mockInternalLogger.verifyLog(
            InternalLogger.Level.WARN,
            listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
            RUM_FEATURE_NOT_A_REGISTRY_PROVIDER
        )
    }

    @Test
    fun `M log warning only once W { RUM present but not a provider, called multiple times }`(
        @LongForgery fakeKey: Long,
        @StringForgery fakeScreenName: String
    ) {
        // Given
        val mockScope: FeatureScope = mock { whenever(it.unwrap<Feature>()).doReturn(mock<Feature>()) }
        whenever(mockSdkCore.getFeature(Feature.RUM_FEATURE_NAME)).thenReturn(mockScope)

        // When
        testedRegistry.getHeatmapIdentifier(fakeKey, fakeScreenName)
        testedRegistry.getHeatmapIdentifier(fakeKey, fakeScreenName)

        // Then
        mockInternalLogger.verifyLog(
            InternalLogger.Level.WARN,
            listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
            RUM_FEATURE_NOT_A_REGISTRY_PROVIDER,
            mode = times(1)
        )
    }

    @Test
    fun `M call getFeature only once W { RUM present but not a provider, called multiple times }`(
        @LongForgery fakeKey: Long,
        @StringForgery fakeScreenName: String
    ) {
        // Given
        val mockScope: FeatureScope = mock { whenever(it.unwrap<Feature>()).doReturn(mock<Feature>()) }
        whenever(mockSdkCore.getFeature(Feature.RUM_FEATURE_NAME)).thenReturn(mockScope)

        // When
        testedRegistry.getHeatmapIdentifier(fakeKey, fakeScreenName)
        testedRegistry.getHeatmapIdentifier(fakeKey, fakeScreenName)

        // Then — caches Unavailable after first resolution
        verify(mockSdkCore, times(1)).getFeature(Feature.RUM_FEATURE_NAME)
    }

    // endregion

    // region RUM registered and implements HeatmapIdentifierRegistryProvider

    @Test
    fun `M delegate to RUM registry W setHeatmapIdentifiers() { RUM registered }`(
        @LongForgery fakeKey: Long,
        @StringForgery fakeScreenName: String
    ) {
        // Given
        val mockRegistry: HeatmapIdentifierRegistry = mock()
        val fakeIdentifier: HeatmapIdentifier = mock()
        val mockScope = stubRumFeatureWithRegistry(mockRegistry)
        whenever(mockSdkCore.getFeature(Feature.RUM_FEATURE_NAME)).thenReturn(mockScope)

        // When
        testedRegistry.setHeatmapIdentifiers(mapOf(fakeKey to fakeIdentifier), fakeScreenName)

        // Then
        verify(mockRegistry).setHeatmapIdentifiers(mapOf(fakeKey to fakeIdentifier), fakeScreenName)
    }

    @Test
    fun `M delegate to RUM registry W getHeatmapIdentifier() { RUM registered }`(
        @LongForgery fakeKey: Long,
        @StringForgery fakeScreenName: String
    ) {
        // Given
        val mockRegistry: HeatmapIdentifierRegistry = mock()
        val fakeIdentifier: HeatmapIdentifier = mock()
        whenever(mockRegistry.getHeatmapIdentifier(fakeKey, fakeScreenName)).thenReturn(fakeIdentifier)
        val mockScope = stubRumFeatureWithRegistry(mockRegistry)
        whenever(mockSdkCore.getFeature(Feature.RUM_FEATURE_NAME)).thenReturn(mockScope)

        // When
        val result = testedRegistry.getHeatmapIdentifier(fakeKey, fakeScreenName)

        // Then
        assertThat(result).isEqualTo(fakeIdentifier)
    }

    @Test
    fun `M call getFeature only once W multiple calls { RUM registered }`(
        @LongForgery fakeKey: Long,
        @StringForgery fakeScreenName: String
    ) {
        // Given
        val mockRegistry: HeatmapIdentifierRegistry = mock()
        val fakeIdentifier: HeatmapIdentifier = mock()
        val mockScope = stubRumFeatureWithRegistry(mockRegistry)
        whenever(mockSdkCore.getFeature(Feature.RUM_FEATURE_NAME)).thenReturn(mockScope)

        // When
        testedRegistry.setHeatmapIdentifiers(mapOf(fakeKey to fakeIdentifier), fakeScreenName)
        testedRegistry.getHeatmapIdentifier(fakeKey, fakeScreenName)

        // Then — caches on first successful resolution
        verify(mockSdkCore, times(1)).getFeature(Feature.RUM_FEATURE_NAME)
    }

    // endregion

    // region helpers

    private fun stubRumFeatureWithRegistry(registry: HeatmapIdentifierRegistry): FeatureScope {
        val mockRumFeature: Feature = mock()
        val provider = object : Feature by mockRumFeature, HeatmapIdentifierRegistryProvider {
            override val heatmapIdentifierRegistry: HeatmapIdentifierRegistry = registry
        }
        return mock {
            whenever(it.unwrap<Feature>()).doReturn(provider)
        }
    }

    // endregion
}
