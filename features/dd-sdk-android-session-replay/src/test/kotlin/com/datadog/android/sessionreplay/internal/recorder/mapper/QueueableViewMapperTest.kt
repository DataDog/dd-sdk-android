/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.mapper

import android.view.View
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.android.sessionreplay.internal.async.RecordedDataQueueRefs
import com.datadog.android.sessionreplay.internal.recorder.MappingContext
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.verify
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(ForgeConfigurator::class)
internal class QueueableViewMapperTest {
    private lateinit var testedMapper: QueueableViewMapper

    @Mock
    lateinit var mockMapper: BaseWireframeMapper<View, *>

    @Mock
    lateinit var mockRecordedDataQueueRefs: RecordedDataQueueRefs

    @Mock
    lateinit var mockView: View

    @Mock
    lateinit var mockMappingContext: MappingContext

    @BeforeEach
    fun setup() {
        testedMapper = QueueableViewMapper(mockMapper, mockRecordedDataQueueRefs)
    }

    @Test
    fun `M increment images W startProcessingImage()`() {
        // When
        testedMapper.startProcessingImage()

        // Then
        verify(mockRecordedDataQueueRefs).incrementPendingImages()
    }

    @Test
    fun `M decrement images W finishProcessingImage()`() {
        // When
        testedMapper.finishProcessingImage()

        // Then
        verify(mockRecordedDataQueueRefs).decrementPendingImages()
    }

    @Test
    fun `M try to consume queue W finishLoadingImage()`() {
        // When
        testedMapper.finishProcessingImage()

        // Then
        verify(mockRecordedDataQueueRefs).tryToConsumeItem()
    }

    @Test
    fun `M register callback W map()`() {
        // When
        testedMapper.map(mockView, mockMappingContext)

        // Then
        verify(mockMapper).registerAsyncImageProcessingCallback(any())
    }

    @Test
    fun `M call mapper map W map()`() {
        // When
        testedMapper.map(mockView, mockMappingContext)

        // Then
        verify(mockMapper).map(mockView, mockMappingContext)
    }
}
