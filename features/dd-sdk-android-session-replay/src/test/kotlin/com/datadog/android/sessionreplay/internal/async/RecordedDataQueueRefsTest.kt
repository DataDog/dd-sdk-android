/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.async

import android.os.Handler
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.Mockito.`when`
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
internal class RecordedDataQueueRefsTest {
    lateinit var testedDataQueueRefs: RecordedDataQueueRefs

    @Mock
    lateinit var mockDataQueueHandler: RecordedDataQueueHandler

    @Mock
    lateinit var mockRecordedDataQueueItem: SnapshotRecordedDataQueueItem

    @Mock
    lateinit var mockHandler: Handler

    @BeforeEach
    fun setup() {
        // make mockHandler execute all runnables immediately
        `when`(mockHandler.post(any())).thenAnswer {
            (it.arguments[0] as Runnable).run()
            true
        }

        testedDataQueueRefs = RecordedDataQueueRefs(mockDataQueueHandler, mockHandler)
        testedDataQueueRefs.recordedDataQueueItem = mockRecordedDataQueueItem
    }

    @Test
    fun `M increment images W incrementPendingImages()`() {
        // When
        testedDataQueueRefs.incrementPendingImages()

        // Then
        verify(mockRecordedDataQueueItem).incrementPendingImages()
    }

    @Test
    fun `M decrement images W decrementPendingImages()`() {
        // When
        testedDataQueueRefs.decrementPendingImages()

        // Then
        verify(mockRecordedDataQueueItem).decrementPendingImages()
    }

    @Test
    fun `M try to consume queue W tryToConsumeItem()`() {
        // When
        testedDataQueueRefs.tryToConsumeItem()

        // Then
        verify(mockDataQueueHandler).tryToConsumeItems()
    }
}
