/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition

import android.app.Activity
import android.app.Application
import com.datadog.android.api.InternalLogger
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.sessionreplay.SessionReplayInternalCallback
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.android.sessionreplay.internal.recorder.TimeBank
import com.datadog.android.sessionreplay.internal.storage.RecordWriter
import com.datadog.android.sessionreplay.internal.utils.RumContextProvider
import fr.xgouchet.elmyr.Forge
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
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(ForgeConfigurator::class)
internal class CompositionPipelineFactoryTest {

    @Mock
    lateinit var mockSdkCore: FeatureSdkCore

    @Mock
    lateinit var mockInternalCallback: SessionReplayInternalCallback

    @Mock
    lateinit var mockApplication: Application

    @BeforeEach
    fun `set up`() {
        whenever(mockSdkCore.internalLogger).thenReturn(mock<InternalLogger>())
        whenever(mockSdkCore.timeProvider).thenReturn(mock())
        whenever(mockSdkCore.createSingleThreadExecutorService(any())).thenReturn(mock())
        whenever(mockSdkCore.createScheduledExecutorService(any())).thenReturn(mock())
    }

    @Test
    fun `M build a composition pipeline W create`() {
        // Given
        val testedFactory = createFactory(dynamicOptimizationEnabled = false)

        // When
        val result = testedFactory.create(mock<RecordWriter>(), mock<RumContextProvider>(), mockApplication)

        // Then
        assertThat(result).isInstanceOf(CompositionCapturePipeline::class.java)
    }

    @Test
    fun `M create dedicated executors W create`() {
        // Given
        val testedFactory = createFactory(dynamicOptimizationEnabled = false)

        // When
        testedFactory.create(mock<RecordWriter>(), mock<RumContextProvider>(), mockApplication)

        // Then
        verify(mockSdkCore).createSingleThreadExecutorService("sr-composition-processing")
        verify(mockSdkCore).createScheduledExecutorService("sr-composition-expiry")
    }

    @Test
    fun `M give the producer the shared window source W create`() {
        // Given
        val windowSources = mutableListOf<ActiveWindowSource>()
        val testedFactory = createFactory(
            dynamicOptimizationEnabled = false,
            snapshotProducerFactory = {
                windowSources += it
                NoOpCapturedSnapshotProducer()
            }
        )

        // When
        testedFactory.create(mock<RecordWriter>(), mock<RumContextProvider>(), mockApplication)

        // Then
        assertThat(windowSources).hasSize(1)
    }

    @Test
    fun `M not create a recording time bank W create { dynamic optimization disabled }`() {
        // Given
        var timeBankConstructions = 0
        val testedFactory = createFactory(
            dynamicOptimizationEnabled = false,
            recordingTimeBankFactory = {
                timeBankConstructions++
                mock<TimeBank>()
            }
        )

        // When
        testedFactory.create(mock<RecordWriter>(), mock<RumContextProvider>(), mockApplication)

        // Then
        assertThat(timeBankConstructions).isZero()
    }

    @Test
    fun `M create a recording time bank W create { dynamic optimization enabled }`() {
        // Given
        var timeBankConstructions = 0
        val testedFactory = createFactory(
            dynamicOptimizationEnabled = true,
            recordingTimeBankFactory = {
                timeBankConstructions++
                mock<TimeBank>()
            }
        )

        // When
        testedFactory.create(mock<RecordWriter>(), mock<RumContextProvider>(), mockApplication)

        // Then
        assertThat(timeBankConstructions).isEqualTo(1)
    }

    @Test
    fun `M read the current activity W create`() {
        // Given
        whenever(mockInternalCallback.getCurrentActivity()).thenReturn(mock<Activity>())
        val testedFactory = createFactory(dynamicOptimizationEnabled = false)

        // When
        testedFactory.create(mock<RecordWriter>(), mock<RumContextProvider>(), mockApplication)

        // Then
        verify(mockInternalCallback).getCurrentActivity()
    }

    @Test
    fun `M capture nothing W the placeholder producer runs`(forge: Forge) {
        // Given
        val fakeGeneration = CaptureGenerationContext(
            id = forge.aLong(min = 1L, max = 1_000L),
            startedAtNs = 0L,
            deadlineNs = Long.MAX_VALUE,
            timeProvider = { 0L }
        )

        // When
        val result = NoOpCapturedSnapshotProducer().capture(fakeGeneration, CaptureChangeset.EMPTY)

        // Then
        assertThat(result).isNull()
    }

    private fun createFactory(
        dynamicOptimizationEnabled: Boolean,
        snapshotProducerFactory: (ActiveWindowSource) -> CapturedSnapshotProducer = { NoOpCapturedSnapshotProducer() },
        recordingTimeBankFactory: () -> TimeBank = { mock() }
    ) = DefaultCompositionPipelineFactory(
        sdkCore = mockSdkCore,
        internalCallback = mockInternalCallback,
        dynamicOptimizationEnabled = dynamicOptimizationEnabled,
        snapshotProducerFactory = snapshotProducerFactory,
        recordingTimeBankFactory = recordingTimeBankFactory
    )
}
