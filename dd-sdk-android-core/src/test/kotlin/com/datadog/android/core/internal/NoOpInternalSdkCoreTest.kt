/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal

import android.util.Log
import com.datadog.android.Datadog
import com.datadog.android.api.feature.Feature
import com.datadog.android.core.internal.logger.SdkInternalLogger
import com.datadog.android.utils.forge.Configurator
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class NoOpInternalSdkCoreTest {

    @Mock
    lateinit var mockFeature: Feature

    @BeforeEach
    fun setUp() {
        Datadog.setVerbosity(Log.VERBOSE)
    }

    @AfterEach
    fun tearDown() {
        Datadog.setVerbosity(Int.MAX_VALUE)
    }

    @Test
    fun `M not throw W registerFeature()`(@StringForgery fakeFeatureName: String) {
        // Given
        whenever(mockFeature.name) doReturn fakeFeatureName

        // When + Then
        assertDoesNotThrow {
            NoOpInternalSdkCore.registerFeature(mockFeature)
        }
    }

    @Test
    fun `M return null W getFeature()`(@StringForgery fakeFeatureName: String) {
        // When
        val result = NoOpInternalSdkCore.getFeature(fakeFeatureName)

        // Then
        assertThat(result).isNull()
    }

    @Test
    fun `M not throw W getFeature()`(@StringForgery fakeFeatureName: String) {
        // When + Then
        assertDoesNotThrow {
            NoOpInternalSdkCore.getFeature(fakeFeatureName)
        }
    }

    @Test
    fun `M log error W registerFeature()`(@StringForgery fakeFeatureName: String) {
        // Given
        whenever(mockFeature.name) doReturn fakeFeatureName
        val expectedMessage = "[${NoOpInternalSdkCore.name}]: " +
            REGISTER_FEATURE_INVOKED_ON_NO_OP_CORE_ERROR.format(fakeFeatureName)

        // When + Then
        Mockito.mockStatic(Log::class.java).use { mockedLog ->
            NoOpInternalSdkCore.registerFeature(mockFeature)

            mockedLog.verify {
                Log.println(Log.ERROR, SdkInternalLogger.DEV_LOG_TAG, expectedMessage)
            }
        }
    }

    @Test
    fun `M log error only once W registerFeature() {called multiple times}`(
        @StringForgery fakeFeatureName: String
    ) {
        // Given
        whenever(mockFeature.name) doReturn fakeFeatureName
        val expectedMessage = "[${NoOpInternalSdkCore.name}]: " +
            REGISTER_FEATURE_INVOKED_ON_NO_OP_CORE_ERROR.format(fakeFeatureName)

        // When + Then
        Mockito.mockStatic(Log::class.java).use { mockedLog ->
            NoOpInternalSdkCore.registerFeature(mockFeature)
            NoOpInternalSdkCore.registerFeature(mockFeature)

            mockedLog.verify {
                Log.println(Log.ERROR, SdkInternalLogger.DEV_LOG_TAG, expectedMessage)
            }
        }
    }

    @Test
    fun `M log error W getFeature()`(@StringForgery fakeFeatureName: String) {
        // Given
        val expectedMessage = "[${NoOpInternalSdkCore.name}]: " +
            GET_FEATURE_INVOKED_ON_NO_OP_CORE_ERROR.format(fakeFeatureName)

        // When + Then
        Mockito.mockStatic(Log::class.java).use { mockedLog ->
            NoOpInternalSdkCore.getFeature(fakeFeatureName)

            mockedLog.verify {
                Log.println(Log.ERROR, SdkInternalLogger.DEV_LOG_TAG, expectedMessage)
            }
        }
    }

    @Test
    fun `M log error only once W getFeature() {called multiple times}`(@StringForgery fakeFeatureName: String) {
        // Given
        val expectedMessage = "[${NoOpInternalSdkCore.name}]: " +
            GET_FEATURE_INVOKED_ON_NO_OP_CORE_ERROR.format(fakeFeatureName)

        // When + Then
        Mockito.mockStatic(Log::class.java).use { mockedLog ->
            NoOpInternalSdkCore.getFeature(fakeFeatureName)
            NoOpInternalSdkCore.getFeature(fakeFeatureName)

            mockedLog.verify {
                Log.println(Log.ERROR, SdkInternalLogger.DEV_LOG_TAG, expectedMessage)
            }
        }
    }
}
