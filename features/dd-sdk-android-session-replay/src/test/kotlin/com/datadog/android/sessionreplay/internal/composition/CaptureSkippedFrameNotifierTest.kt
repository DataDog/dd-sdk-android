/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition

import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.FeatureScope
import com.datadog.android.api.feature.FeatureSdkCore
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

internal class CaptureSkippedFrameNotifierTest {

    @Test
    fun `M send skipped-frame event W RUM feature exists`() {
        // Given
        val sdkCore = mock<FeatureSdkCore>()
        val rumFeature = mock<FeatureScope>()
        whenever(sdkCore.getFeature(Feature.RUM_FEATURE_NAME)).thenReturn(rumFeature)

        // When
        CaptureSkippedFrameNotifier(sdkCore).notifySkippedFrame()

        // Then
        val event = argumentCaptor<Any>()
        verify(rumFeature).sendEvent(event.capture())
        assertThat(event.firstValue).isEqualTo(mapOf("type" to "sr_skipped_frame"))
    }

    @Test
    fun `M skip event W RUM feature is absent`() {
        // Given
        val sdkCore = mock<FeatureSdkCore>()

        // When
        CaptureSkippedFrameNotifier(sdkCore).notifySkippedFrame()

        // Then
        verify(sdkCore).getFeature(Feature.RUM_FEATURE_NAME)
    }
}
