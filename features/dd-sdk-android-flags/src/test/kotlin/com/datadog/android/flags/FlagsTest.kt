/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags

import com.datadog.android.DatadogSite
import com.datadog.android.api.InternalLogger
import com.datadog.android.api.context.DatadogContext
import com.datadog.android.api.feature.Feature.Companion.FLAGS_FEATURE_NAME
import com.datadog.android.api.feature.Feature.Companion.RUM_FEATURE_NAME
import com.datadog.android.core.InternalSdkCore
// Executor name imports removed - now using any() matcher for flexibility
import com.datadog.android.flags.internal.FlagsFeature
import com.datadog.android.flags.utils.forge.ForgeConfigurator
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.util.concurrent.ExecutorService

@ExtendWith(MockitoExtension::class, ForgeExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(ForgeConfigurator::class)
internal class FlagsTest {

    @Mock
    lateinit var mockSdkCore: InternalSdkCore

    @Mock
    lateinit var mockExecutorService: ExecutorService

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @Mock
    lateinit var mockDatadogContext: DatadogContext

    @StringForgery
    lateinit var fakeClientToken: String

    @StringForgery
    lateinit var fakeEnv: String

    @BeforeEach
    fun `set up`() {
        whenever(mockSdkCore.internalLogger) doReturn mockInternalLogger
        whenever(mockSdkCore.createSingleThreadExecutorService(org.mockito.kotlin.any())) doReturn
            mockExecutorService

        whenever(mockDatadogContext.clientToken) doReturn fakeClientToken
        whenever(mockDatadogContext.site) doReturn DatadogSite.US1
        whenever(mockDatadogContext.env) doReturn fakeEnv
        whenever(mockSdkCore.getDatadogContext()) doReturn mockDatadogContext
        whenever(mockSdkCore.getFeature(RUM_FEATURE_NAME)) doReturn mock()
    }

    // region enable()

    @Test
    fun `M register FlagsFeature W enable()`() {
        // Given
        val config = FlagsConfiguration.Builder().trackExposures(false).build()

        // When
        Flags.enable(config, mockSdkCore)

        // Then
        argumentCaptor<FlagsFeature> {
            verify(mockSdkCore).registerFeature(capture())
            assertThat(lastValue.name).isEqualTo(FLAGS_FEATURE_NAME)
        }
    }

    @Test
    fun `M use default configuration W enable() { no config provided }`() {
        // When
        Flags.enable(sdkCore = mockSdkCore)

        // Then
        argumentCaptor<FlagsFeature> {
            verify(mockSdkCore).registerFeature(capture())
            assertThat(lastValue.flagsConfiguration.trackExposures).isTrue()
            assertThat(lastValue.flagsConfiguration.customExposureEndpoint).isNull()
            assertThat(lastValue.flagsConfiguration).isEqualTo(FlagsConfiguration.default)
        }
    }

    @Test
    fun `M pass default configuration to FlagsFeature W enable() { default config }`() {
        // Given
        val defaultConfiguration = FlagsConfiguration.default

        // When
        Flags.enable(defaultConfiguration, mockSdkCore)

        // Then
        argumentCaptor<FlagsFeature> {
            verify(mockSdkCore).registerFeature(capture())
            assertThat(lastValue.flagsConfiguration.trackExposures).isTrue()
            assertThat(lastValue.flagsConfiguration.customExposureEndpoint).isNull()
            assertThat(lastValue.flagsConfiguration.customFlagEndpoint).isNull()
            assertThat(lastValue.flagsConfiguration).isEqualTo(FlagsConfiguration.default)
        }
    }

    @Test
    fun `M pass configuration to FlagsFeature W enable() { with custom config }`(
        @StringForgery(regex = "https://[a-z]+\\.com(/[a-z]+)+") fakeCustomEndpoint: String,
        @StringForgery(regex = "https://[a-z]+\\.com(/[a-z]+)+") fakeCustomFlagEndpoint: String
    ) {
        // Given
        val fakeConfiguration = FlagsConfiguration.Builder()
            .useCustomExposureEndpoint(fakeCustomEndpoint)
            .useCustomFlagEndpoint(fakeCustomFlagEndpoint)
            .build()

        // When
        Flags.enable(fakeConfiguration, mockSdkCore)

        // Then
        argumentCaptor<FlagsFeature> {
            verify(mockSdkCore).registerFeature(capture())
            assertThat(lastValue.name).isEqualTo(FLAGS_FEATURE_NAME)
            assertThat(lastValue.flagsConfiguration.customExposureEndpoint).isEqualTo(fakeCustomEndpoint)
            assertThat(lastValue.flagsConfiguration.customFlagEndpoint).isEqualTo(fakeCustomFlagEndpoint)
        }
    }

    // endregion
}
