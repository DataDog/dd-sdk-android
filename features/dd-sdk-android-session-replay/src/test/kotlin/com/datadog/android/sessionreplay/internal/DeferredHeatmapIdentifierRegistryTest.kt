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
import com.datadog.android.sessionreplay.internal.DeferredHeatmapIdentifierRegistry.Companion.RUM_FEATURE_NOT_A_REGISTRY_PROVIDER
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
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(ForgeConfigurator::class)
internal class DeferredHeatmapIdentifierRegistryTest {

    private lateinit var testedRegistry: DeferredHeatmapIdentifierRegistry

    @Mock
    lateinit var mockSdkCore: FeatureSdkCore

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @BeforeEach
    fun `set up`() {
        whenever(mockSdkCore.internalLogger).thenReturn(mockInternalLogger)
        testedRegistry = DeferredHeatmapIdentifierRegistry(mockSdkCore)
    }

    // region setHeatmapIdentifiers — RUM not registered

    @Test
    fun `M no-op W setHeatmapIdentifiers() { RUM feature not registered }`(
        @LongForgery fakeKey: Long,
        @StringForgery fakeScreenName: String
    ) {
        whenever(mockSdkCore.getFeature(Feature.RUM_FEATURE_NAME)).thenReturn(null)

        val fakeIdentifier: HeatmapIdentifier = mock()
        testedRegistry.setHeatmapIdentifiers(mapOf(fakeKey to fakeIdentifier), fakeScreenName)

        verify(mockSdkCore).getFeature(Feature.RUM_FEATURE_NAME)
    }

    @Test
    fun `M call getFeature on every call W multiple calls { RUM not registered }`(
        @LongForgery fakeKey: Long,
        @StringForgery fakeScreenName: String
    ) {
        whenever(mockSdkCore.getFeature(Feature.RUM_FEATURE_NAME)).thenReturn(null)
        val fakeIdentifier: HeatmapIdentifier = mock()

        testedRegistry.setHeatmapIdentifiers(mapOf(fakeKey to fakeIdentifier), fakeScreenName)
        testedRegistry.setHeatmapIdentifiers(mapOf(fakeKey to fakeIdentifier), fakeScreenName)

        verify(mockSdkCore, times(2)).getFeature(Feature.RUM_FEATURE_NAME)
    }

    @Test
    fun `M call getFeature on every call W multiple calls { getHeatmapIdentifier, RUM not registered }`(
        @LongForgery fakeHash: Long,
        @StringForgery fakeScreenName: String
    ) {
        whenever(mockSdkCore.getFeature(Feature.RUM_FEATURE_NAME)).thenReturn(null)

        testedRegistry.getHeatmapIdentifier(fakeHash, fakeScreenName)
        testedRegistry.getHeatmapIdentifier(fakeHash, fakeScreenName)

        verify(mockSdkCore, times(2)).getFeature(Feature.RUM_FEATURE_NAME)
    }

    // endregion

    // region getHeatmapIdentifier — RUM not registered

    @Test
    fun `M return null W getHeatmapIdentifier() { RUM feature not registered }`(
        @LongForgery fakeHash: Long,
        @StringForgery fakeScreenName: String
    ) {
        whenever(mockSdkCore.getFeature(Feature.RUM_FEATURE_NAME)).thenReturn(null)

        val result = testedRegistry.getHeatmapIdentifier(fakeHash, fakeScreenName)

        assertThat(result).isNull()
    }

    // endregion

    // region delegate resolution — RUM registered and implements HeatmapIdentifierRegistryProvider

    @Test
    fun `M delegate to RUM registry W setHeatmapIdentifiers() { RUM registered }`(
        @LongForgery fakeKey: Long,
        @StringForgery fakeScreenName: String
    ) {
        val mockRumRegistry: HeatmapIdentifierRegistry = mock()
        val fakeIdentifier: HeatmapIdentifier = mock()
        val mockFeatureScope = stubRumFeatureWithRegistry(mockRumRegistry)
        whenever(mockSdkCore.getFeature(Feature.RUM_FEATURE_NAME)).thenReturn(mockFeatureScope)

        testedRegistry.setHeatmapIdentifiers(mapOf(fakeKey to fakeIdentifier), fakeScreenName)

        verify(mockRumRegistry).setHeatmapIdentifiers(mapOf(fakeKey to fakeIdentifier), fakeScreenName)
    }

    @Test
    fun `M delegate to RUM registry W getHeatmapIdentifier() { RUM registered }`(
        @LongForgery fakeHash: Long,
        @StringForgery fakeScreenName: String
    ) {
        val mockRumRegistry: HeatmapIdentifierRegistry = mock()
        val fakeIdentifier: HeatmapIdentifier = mock()
        whenever(mockRumRegistry.getHeatmapIdentifier(fakeHash, fakeScreenName)).thenReturn(fakeIdentifier)
        val mockFeatureScope = stubRumFeatureWithRegistry(mockRumRegistry)
        whenever(mockSdkCore.getFeature(Feature.RUM_FEATURE_NAME)).thenReturn(mockFeatureScope)

        val result = testedRegistry.getHeatmapIdentifier(fakeHash, fakeScreenName)

        assertThat(result).isEqualTo(fakeIdentifier)
    }

    @Test
    fun `M resolve delegate once W multiple calls { RUM registered }`(
        @LongForgery fakeKey: Long,
        @StringForgery fakeScreenName: String
    ) {
        val mockRumRegistry: HeatmapIdentifierRegistry = mock()
        val fakeIdentifier: HeatmapIdentifier = mock()
        val mockFeatureScope = stubRumFeatureWithRegistry(mockRumRegistry)
        whenever(mockSdkCore.getFeature(Feature.RUM_FEATURE_NAME)).thenReturn(mockFeatureScope)

        testedRegistry.setHeatmapIdentifiers(mapOf(fakeKey to fakeIdentifier), fakeScreenName)
        testedRegistry.getHeatmapIdentifier(fakeKey, fakeScreenName)

        verify(mockSdkCore).getFeature(Feature.RUM_FEATURE_NAME)
    }

    // endregion

    // region delegate resolution — RUM registered but doesn't implement HeatmapIdentifierRegistryProvider

    @Test
    fun `M log warning W setHeatmapIdentifiers() { RUM feature present but not a provider }`(
        @LongForgery fakeKey: Long,
        @StringForgery fakeScreenName: String
    ) {
        val mockFeatureScope: FeatureScope = mock {
            whenever(it.unwrap<Feature>()).doReturn(mock<Feature>())
        }
        whenever(mockSdkCore.getFeature(Feature.RUM_FEATURE_NAME)).thenReturn(mockFeatureScope)
        val fakeIdentifier: HeatmapIdentifier = mock()

        testedRegistry.setHeatmapIdentifiers(mapOf(fakeKey to fakeIdentifier), fakeScreenName)

        val messageCaptor = argumentCaptor<() -> String>()
        verify(mockInternalLogger).log(
            eq(InternalLogger.Level.WARN),
            eq(listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY)),
            messageCaptor.capture(),
            isNull(),
            eq(false),
            isNull()
        )
        assertThat(messageCaptor.firstValue()).isEqualTo(RUM_FEATURE_NOT_A_REGISTRY_PROVIDER)
    }

    @Test
    fun `M log warning only once W setHeatmapIdentifiers() { called twice, RUM feature present but not a provider }`(
        @LongForgery fakeKey: Long,
        @StringForgery fakeScreenName: String
    ) {
        // Given
        val mockFeatureScope: FeatureScope = mock {
            whenever(it.unwrap<Feature>()).doReturn(mock<Feature>())
        }
        whenever(mockSdkCore.getFeature(Feature.RUM_FEATURE_NAME)).thenReturn(mockFeatureScope)
        val fakeIdentifier: HeatmapIdentifier = mock()

        testedRegistry.setHeatmapIdentifiers(mapOf(fakeKey to fakeIdentifier), fakeScreenName)
        testedRegistry.setHeatmapIdentifiers(mapOf(fakeKey to fakeIdentifier), fakeScreenName)

        verify(mockInternalLogger, times(1)).log(
            any(),
            any<List<InternalLogger.Target>>(),
            any(),
            isNull(),
            eq(false),
            isNull()
        )
    }

    @Test
    fun `M call getFeature only once W multiple calls { RUM feature present but not a provider }`(
        @LongForgery fakeKey: Long,
        @StringForgery fakeScreenName: String
    ) {
        val mockFeatureScope: FeatureScope = mock {
            whenever(it.unwrap<Feature>()).doReturn(mock<Feature>())
        }
        whenever(mockSdkCore.getFeature(Feature.RUM_FEATURE_NAME)).thenReturn(mockFeatureScope)
        val fakeIdentifier: HeatmapIdentifier = mock()

        testedRegistry.setHeatmapIdentifiers(mapOf(fakeKey to fakeIdentifier), fakeScreenName)
        testedRegistry.setHeatmapIdentifiers(mapOf(fakeKey to fakeIdentifier), fakeScreenName)

        verify(mockSdkCore, times(1)).getFeature(Feature.RUM_FEATURE_NAME)
    }

    @Test
    fun `M return null W getHeatmapIdentifier() { RUM feature present but not a provider }`(
        @LongForgery fakeHash: Long,
        @StringForgery fakeScreenName: String
    ) {
        val mockFeatureScope: FeatureScope = mock {
            whenever(it.unwrap<Feature>()).doReturn(mock<Feature>())
        }
        whenever(mockSdkCore.getFeature(Feature.RUM_FEATURE_NAME)).thenReturn(mockFeatureScope)

        val result = testedRegistry.getHeatmapIdentifier(fakeHash, fakeScreenName)

        assertThat(result).isNull()
    }

    // endregion

    // region helpers

    /**
     * Builds a [FeatureScope] mock whose `unwrap<Feature>()`
     * returns an object implementing both [Feature] and [HeatmapIdentifierRegistryProvider],
     * backed by [registry].
     */
    private fun stubRumFeatureWithRegistry(
        registry: HeatmapIdentifierRegistry
    ): FeatureScope {
        val mockRumFeature: Feature = mock()
        val mockProvider = object : Feature by mockRumFeature, HeatmapIdentifierRegistryProvider {
            override val heatmapIdentifierRegistry: HeatmapIdentifierRegistry = registry
        }
        return mock {
            whenever(it.unwrap<Feature>()).doReturn(mockProvider)
        }
    }

    // endregion
}
