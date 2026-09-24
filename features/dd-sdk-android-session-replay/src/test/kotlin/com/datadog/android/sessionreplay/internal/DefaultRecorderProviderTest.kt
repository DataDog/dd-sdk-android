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
import com.datadog.android.sessionreplay.internal.composition.CompositionPipelineFactory
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
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(ForgeConfigurator::class)
internal class DefaultRecorderProviderTest {

    @Mock
    lateinit var mockSdkCore: FeatureSdkCore

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @Mock
    lateinit var mockApplication: Application

    private var compositionPipelineConstructions = 0

    @BeforeEach
    fun `set up`(
        @StringForgery fakePackageName: String
    ) {
        whenever(mockSdkCore.internalLogger).thenReturn(mockInternalLogger)
        whenever(mockSdkCore.timeProvider).thenReturn(mock())
        whenever(mockSdkCore.createSingleThreadExecutorService(any())).thenReturn(mock())
        whenever(mockSdkCore.createScheduledExecutorService(any())).thenReturn(mock())
        whenever(mockApplication.packageName).thenReturn(fakePackageName)
        compositionPipelineConstructions = 0
    }

    @Test
    fun `M create only legacy recorder W provideSessionReplayRecorder { flag disabled }`() {
        // Given
        val testedProvider = createProvider(
            heatmapsEnabled = false,
            compositionTreeRecordingEnabled = false
        )

        // When
        val result = testedProvider.provideRecorder()

        // Then
        assertThat(result).isInstanceOf(SessionReplayRecorder::class.java)
        assertThat(compositionPipelineConstructions).isZero()
    }

    @Test
    fun `M create only composition recorder W provideSessionReplayRecorder { flag enabled }`() {
        // Given
        val mockCompositionRecorder = mock<Recorder>()
        val testedProvider = createProvider(
            heatmapsEnabled = false,
            compositionTreeRecordingEnabled = true,
            compositionRecorder = mockCompositionRecorder
        )

        // When
        val result = testedProvider.provideRecorder()

        // Then
        assertThat(result).isSameAs(mockCompositionRecorder)
        assertThat(compositionPipelineConstructions).isEqualTo(1)
    }

    @Test
    fun `M pass the recording collaborators to the factory W provideSessionReplayRecorder { flag enabled }`() {
        // Given
        val mockRecordWriter = mock<RecordWriter>()
        val mockRumContextProvider = mock<RumContextProvider>()
        var factoryArguments: Triple<RecordWriter, RumContextProvider, Application>? = null
        val testedProvider = createProvider(
            heatmapsEnabled = false,
            compositionTreeRecordingEnabled = true,
            compositionPipelineFactory = { recordWriter, rumContextProvider, application ->
                factoryArguments = Triple(recordWriter, rumContextProvider, application)
                mock()
            }
        )

        // When
        testedProvider.provideRecorder(
            recordWriter = mockRecordWriter,
            rumContextProvider = mockRumContextProvider
        )

        // Then
        assertThat(factoryArguments)
            .isEqualTo(Triple(mockRecordWriter, mockRumContextProvider, mockApplication))
    }

    @Test
    fun `M log warning W provideSessionReplayRecorder { heatmaps and composition recording enabled }`() {
        // Given
        val testedProvider = createProvider(
            heatmapsEnabled = true,
            compositionTreeRecordingEnabled = true
        )

        // When
        testedProvider.provideRecorder()

        // Then
        mockInternalLogger.verifyLog(
            InternalLogger.Level.WARN,
            InternalLogger.Target.USER,
            DefaultRecorderProvider.HEATMAPS_UNSUPPORTED_WITH_COMPOSITION_RECORDING_MESSAGE
        )
    }

    @Test
    fun `M not log warning W provideSessionReplayRecorder { heatmaps enabled, composition disabled }`() {
        // Given
        val testedProvider = createProvider(
            heatmapsEnabled = true,
            compositionTreeRecordingEnabled = false
        )

        // When
        testedProvider.provideRecorder()

        // Then
        verifyNoInteractions(mockInternalLogger)
    }

    private fun createProvider(
        heatmapsEnabled: Boolean,
        compositionTreeRecordingEnabled: Boolean,
        compositionRecorder: Recorder = mock(),
        compositionPipelineFactory: CompositionPipelineFactory = CompositionPipelineFactory { _, _, _ ->
            compositionPipelineConstructions++
            compositionRecorder
        }
    ) = DefaultRecorderProvider(
        sdkCore = mockSdkCore,
        textAndInputPrivacy = TextAndInputPrivacy.MASK_ALL,
        imagePrivacy = ImagePrivacy.MASK_ALL,
        touchPrivacyManager = mock<TouchPrivacyManager>(),
        customMappers = emptyList(),
        customOptionSelectorDetectors = emptyList(),
        customDrawableMappers = emptyList(),
        dynamicOptimizationEnabled = false,
        internalCallback = mock<SessionReplayInternalCallback>(),
        heatmapsEnabled = heatmapsEnabled,
        compositionTreeRecordingEnabled = compositionTreeRecordingEnabled,
        compositionPipelineFactory = compositionPipelineFactory
    )

    private fun DefaultRecorderProvider.provideRecorder(
        recordWriter: RecordWriter = mock(),
        rumContextProvider: RumContextProvider = mock()
    ): Recorder = provideSessionReplayRecorder(
        resourceDataStoreManager = mock<ResourceDataStoreManager>(),
        resourceWriter = mock<ResourcesWriter>(),
        recordWriter = recordWriter,
        rumContextProvider = rumContextProvider,
        application = mockApplication,
        embeddedContentSlotRegistry = mock<EmbeddedContentSlotRegistry>()
    )
}
