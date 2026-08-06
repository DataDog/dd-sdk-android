/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal

import android.app.Application
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.sessionreplay.ImagePrivacy
import com.datadog.android.sessionreplay.SessionReplayInternalCallback
import com.datadog.android.sessionreplay.TextAndInputPrivacy
import com.datadog.android.sessionreplay.internal.recorder.Recorder
import com.datadog.android.sessionreplay.internal.recorder.SessionReplayRecorder
import com.datadog.android.sessionreplay.internal.resources.ResourceDataStoreManager
import com.datadog.android.sessionreplay.internal.storage.RecordWriter
import com.datadog.android.sessionreplay.internal.storage.ResourcesWriter
import com.datadog.android.sessionreplay.internal.utils.RumContextProvider
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

internal class DefaultRecorderProviderTest {

    @Test
    fun `M create only legacy recorder W provideSessionReplayRecorder { flag omitted }`() {
        // Given
        val sdkCore = mock<FeatureSdkCore>()
        whenever(sdkCore.timeProvider).thenReturn(mock())
        whenever(sdkCore.internalLogger).thenReturn(mock())
        whenever(sdkCore.createSingleThreadExecutorService(any())).thenReturn(mock())
        var compositionConstructions = 0
        val provider = DefaultRecorderProvider(
            sdkCore = sdkCore,
            textAndInputPrivacy = TextAndInputPrivacy.MASK_ALL,
            imagePrivacy = ImagePrivacy.MASK_ALL,
            touchPrivacyManager = mock<TouchPrivacyManager>(),
            customMappers = emptyList(),
            customOptionSelectorDetectors = emptyList(),
            customDrawableMappers = emptyList(),
            dynamicOptimizationEnabled = false,
            internalCallback = mock<SessionReplayInternalCallback>(),
            heatmapsEnabled = false,
            compositionTreeRecordingEnabled = false,
            compositionPipelineFactory = {
                compositionConstructions++
                mock()
            }
        )

        // When
        val result = provider.provideSessionReplayRecorder(
            resourceDataStoreManager = mock<ResourceDataStoreManager>(),
            resourceWriter = mock<ResourcesWriter>(),
            recordWriter = mock<RecordWriter>(),
            rumContextProvider = mock<RumContextProvider>(),
            application = mock<Application>()
        )

        // Then
        assertThat(result).isInstanceOf(SessionReplayRecorder::class.java)
        assertThat(compositionConstructions).isZero()
    }

    @Test
    fun `M create only composition recorder W provideSessionReplayRecorder { flag enabled }`() {
        // Given
        val compositionRecorder = mock<Recorder>()
        var compositionConstructions = 0
        val provider = DefaultRecorderProvider(
            sdkCore = mock<FeatureSdkCore>(),
            textAndInputPrivacy = TextAndInputPrivacy.MASK_ALL,
            imagePrivacy = ImagePrivacy.MASK_ALL,
            touchPrivacyManager = mock<TouchPrivacyManager>(),
            customMappers = emptyList(),
            customOptionSelectorDetectors = emptyList(),
            customDrawableMappers = emptyList(),
            dynamicOptimizationEnabled = false,
            internalCallback = mock<SessionReplayInternalCallback>(),
            heatmapsEnabled = false,
            compositionTreeRecordingEnabled = true,
            compositionPipelineFactory = {
                compositionConstructions++
                compositionRecorder
            }
        )

        // When
        val result = provider.provideSessionReplayRecorder(
            resourceDataStoreManager = mock<ResourceDataStoreManager>(),
            resourceWriter = mock<ResourcesWriter>(),
            recordWriter = mock<RecordWriter>(),
            rumContextProvider = mock<RumContextProvider>(),
            application = mock<Application>()
        )

        // Then
        assertThat(result).isSameAs(compositionRecorder)
        assertThat(compositionConstructions).isEqualTo(1)
    }
}
