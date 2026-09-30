/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition

import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.FeatureScope
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(ForgeConfigurator::class)
internal class CaptureSkippedFrameNotifierTest {

    @Mock
    lateinit var mockSdkCore: FeatureSdkCore

    @Mock
    lateinit var mockRumFeature: FeatureScope

    @Test
    fun `M send skipped-frame event W RUM feature exists`() {
        // Given
        whenever(mockSdkCore.getFeature(Feature.RUM_FEATURE_NAME)).thenReturn(mockRumFeature)

        // When
        CaptureSkippedFrameNotifier(mockSdkCore).notifySkippedFrame()

        // Then
        val event = argumentCaptor<Any>()
        verify(mockRumFeature).sendEvent(event.capture())
        assertThat(event.firstValue).isEqualTo(mapOf("type" to "sr_skipped_frame"))
    }

    @Test
    fun `M send one event per skipped frame W notifySkippedFrame { repeated denials }`(
        @IntForgery(min = 2, max = 10) fakeSkippedFrameCount: Int
    ) {
        // Given
        whenever(mockSdkCore.getFeature(Feature.RUM_FEATURE_NAME)).thenReturn(mockRumFeature)
        val testedNotifier = CaptureSkippedFrameNotifier(mockSdkCore)

        // When
        repeat(fakeSkippedFrameCount) { testedNotifier.notifySkippedFrame() }

        // Then
        val event = argumentCaptor<Any>()
        verify(mockRumFeature, times(fakeSkippedFrameCount)).sendEvent(event.capture())
        assertThat(event.allValues).allMatch { it == mapOf("type" to "sr_skipped_frame") }
    }

    @Test
    fun `M skip event W RUM feature is absent`() {
        // Given
        whenever(mockSdkCore.getFeature(Feature.RUM_FEATURE_NAME)).thenReturn(null)

        // When
        CaptureSkippedFrameNotifier(mockSdkCore).notifySkippedFrame()

        // Then
        verify(mockSdkCore).getFeature(Feature.RUM_FEATURE_NAME)
        verifyNoMoreInteractions(mockSdkCore, mockRumFeature)
    }
}
