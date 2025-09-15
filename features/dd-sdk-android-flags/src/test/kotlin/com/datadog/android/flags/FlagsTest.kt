/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags

import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.flags.featureflags.NoOpFlagsProvider
import com.datadog.android.flags.internal.FlagsFeature
import com.datadog.android.flags.internal.FlagsFeature.Companion.FLAGS_FEATURE_NAME
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
internal class FlagsTest {

    @Mock
    lateinit var mockSdkCore: FeatureSdkCore

    @Mock
    lateinit var mockExecutorService: ExecutorService

    @BeforeEach
    fun `set up`() {
        Flags.flagsClient = NoOpFlagsProvider()

        whenever(mockSdkCore.internalLogger) doReturn mock()
        whenever(mockSdkCore.createSingleThreadExecutorService("flags-executor")) doReturn mockExecutorService
    }

    // region enable()

    @Test
    fun `M register FlagsFeature W enable()`() {
        // Given
        val fakeConfiguration = FlagsConfiguration.Builder()
            .setEnableExposureLogging(true)
            .build()

        // When
        Flags.enable(fakeConfiguration, mockSdkCore)

        // Then
        argumentCaptor<FlagsFeature> {
            verify(mockSdkCore).registerFeature(capture())

            assertThat(lastValue.name).isEqualTo(FLAGS_FEATURE_NAME)
        }
    }

    // endregion

    @Test
    fun `M have NoOpFeatureFlagsProvider as default W initial state`() {
        // When
        val flagsClient = Flags.flagsClient

        // Then
        assertThat(flagsClient).isInstanceOf(NoOpFlagsProvider::class.java)
    }
}
