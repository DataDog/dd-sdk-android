/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal

import android.app.Application
import com.datadog.android.api.InternalLogger
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.sessionreplay.ImagePrivacy
import com.datadog.android.sessionreplay.SessionReplayInternalCallback
import com.datadog.android.sessionreplay.TextAndInputPrivacy
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.android.sessionreplay.internal.composition.CompositionCapturePipeline
import com.datadog.android.sessionreplay.internal.embedded.EmbeddedContentSlotRegistry
import com.datadog.android.sessionreplay.internal.recorder.Recorder
import com.datadog.android.sessionreplay.internal.recorder.SessionReplayRecorder
import com.datadog.android.sessionreplay.internal.resources.ResourceDataStoreManager
import com.datadog.android.sessionreplay.internal.storage.RecordWriter
import com.datadog.android.sessionreplay.internal.storage.ResourcesWriter
import com.datadog.android.sessionreplay.internal.utils.RumContextProvider
import com.datadog.android.utils.verifyLog
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

@Extensions(
    ExtendWith(ForgeExtension::class)
)
@ForgeConfiguration(ForgeConfigurator::class)
internal class DefaultRecorderProviderTest {

    @Test
    fun `M create only legacy recorder W provideSessionReplayRecorder { flag omitted }`() {
        // Given
        val sdkCore = mock<FeatureSdkCore>()
        whenever(sdkCore.timeProvider).thenReturn(mock())
        whenever(sdkCore.internalLogger).thenReturn(mock())
        whenever(sdkCore.createSingleThreadExecutorService(any())).thenReturn(mock())
        whenever(sdkCore.createScheduledExecutorService(any())).thenReturn(mock())
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
            application = mock<Application>(),
            embeddedContentSlotRegistry = mock<EmbeddedContentSlotRegistry>()
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
            application = mock<Application>(),
            embeddedContentSlotRegistry = mock<EmbeddedContentSlotRegistry>()
        )

        // Then
        assertThat(result).isSameAs(compositionRecorder)
        assertThat(compositionConstructions).isEqualTo(1)
    }

    @Test
    fun `M construct wired composition pipeline W provideSessionReplayRecorder { default factory }`() {
        // Given
        val sdkCore = mock<FeatureSdkCore>()
        whenever(sdkCore.timeProvider).thenReturn(mock())
        whenever(sdkCore.internalLogger).thenReturn(mock())
        whenever(sdkCore.createSingleThreadExecutorService(any())).thenReturn(mock())
        whenever(sdkCore.createScheduledExecutorService(any())).thenReturn(mock())
        var producerConstructions = 0
        var timeBankConstructions = 0
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
            compositionTreeRecordingEnabled = true,
            compositionSnapshotProducerFactory = {
                producerConstructions++
                mock()
            },
            recordingTimeBankFactory = {
                timeBankConstructions++
                mock()
            }
        )

        // When
        val result = provider.provideSessionReplayRecorder(
            resourceDataStoreManager = mock<ResourceDataStoreManager>(),
            resourceWriter = mock<ResourcesWriter>(),
            recordWriter = mock<RecordWriter>(),
            rumContextProvider = mock<RumContextProvider>(),
            application = mock<Application>(),
            embeddedContentSlotRegistry = mock<EmbeddedContentSlotRegistry>()
        )

        // Then
        assertThat(result).isInstanceOf(CompositionCapturePipeline::class.java)
        assertThat(producerConstructions).isEqualTo(1)
        assertThat(timeBankConstructions).isZero()
    }

    @Test
    fun `M log warning W provideSessionReplayRecorder { heatmaps and composition recording both enabled }`() {
        // Given
        val sdkCore = mock<FeatureSdkCore>()
        val internalLogger = mock<InternalLogger>()
        whenever(sdkCore.internalLogger).thenReturn(internalLogger)
        whenever(sdkCore.timeProvider).thenReturn(mock())
        whenever(sdkCore.createSingleThreadExecutorService(any())).thenReturn(mock())
        whenever(sdkCore.createScheduledExecutorService(any())).thenReturn(mock())
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
            heatmapsEnabled = true,
            compositionTreeRecordingEnabled = true,
            compositionPipelineFactory = { mock() }
        )

        // When
        provider.provideSessionReplayRecorder(
            resourceDataStoreManager = mock<ResourceDataStoreManager>(),
            resourceWriter = mock<ResourcesWriter>(),
            recordWriter = mock<RecordWriter>(),
            rumContextProvider = mock<RumContextProvider>(),
            application = mock<Application>(),
            embeddedContentSlotRegistry = mock<EmbeddedContentSlotRegistry>()
        )

        // Then
        internalLogger.verifyLog(
            InternalLogger.Level.WARN,
            InternalLogger.Target.USER,
            DefaultRecorderProvider.HEATMAPS_UNSUPPORTED_WITH_COMPOSITION_RECORDING_MESSAGE
        )
    }

    @Test
    fun `M not log warning W provideSessionReplayRecorder { heatmaps enabled, composition recording disabled }`(
        @StringForgery fakePackageName: String
    ) {
        // Given
        val sdkCore = mock<FeatureSdkCore>()
        val internalLogger = mock<InternalLogger>()
        whenever(sdkCore.internalLogger).thenReturn(internalLogger)
        whenever(sdkCore.timeProvider).thenReturn(mock())
        whenever(sdkCore.createSingleThreadExecutorService(any())).thenReturn(mock())
        whenever(sdkCore.createScheduledExecutorService(any())).thenReturn(mock())
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
            heatmapsEnabled = true,
            compositionTreeRecordingEnabled = false,
            compositionPipelineFactory = { mock() }
        )

        // When
        provider.provideSessionReplayRecorder(
            resourceDataStoreManager = mock<ResourceDataStoreManager>(),
            resourceWriter = mock<ResourcesWriter>(),
            recordWriter = mock<RecordWriter>(),
            rumContextProvider = mock<RumContextProvider>(),
            application = mock<Application>().apply { whenever(packageName).thenReturn(fakePackageName) },
            embeddedContentSlotRegistry = mock<EmbeddedContentSlotRegistry>()
        )

        // Then
        verifyNoInteractions(internalLogger)
    }
}
