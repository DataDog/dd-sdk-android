/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.async

import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
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

    @BeforeEach
    fun setup() {
        testedDataQueueRefs = RecordedDataQueueRefs(mockDataQueueHandler)
        testedDataQueueRefs.recordedDataQueueItem = mockRecordedDataQueueItem
    }

    @Test
    fun `M increment images W incrementPendingJobs()`() {
        // When
        testedDataQueueRefs.incrementPendingJobs()

        // Then
        verify(mockRecordedDataQueueItem).incrementPendingJobs()
    }

    @Test
    fun `M decrement images W decrementPendingJobs()`() {
        // When
        testedDataQueueRefs.decrementPendingJobs()

        // Then
        verify(mockRecordedDataQueueItem).decrementPendingJobs()
    }

    @Test
    fun `M try to consume queue W tryToConsumeItem()`() {
        // When
        testedDataQueueRefs.tryToConsumeItem()

        // Then
        verify(mockDataQueueHandler).tryToConsumeItems()
    }
}
