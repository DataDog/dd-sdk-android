/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.async

import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.android.sessionreplay.internal.processor.RecordedQueuedItemContext
import com.datadog.android.sessionreplay.internal.recorder.SystemInformation
import fr.xgouchet.elmyr.annotation.Forgery
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
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(ForgeConfigurator::class)
internal class SnapshotRecordedDataQueueItemTest {

    @Forgery
    lateinit var fakeSnapshotRecordedDataQueueItem: SnapshotRecordedDataQueueItem

    @Forgery
    lateinit var fakeRecordedQueuedItemContext: RecordedQueuedItemContext

    @Mock
    lateinit var mockSystemInformation: SystemInformation

    private lateinit var testedItem: SnapshotRecordedDataQueueItem

    @BeforeEach
    fun `set up`() {
        testedItem = fakeSnapshotRecordedDataQueueItem
    }

    @Test
    fun `M return false W isValid() { finished traversal with empty nodes }`() {
        // Given
        testedItem.nodes = emptyList()
        testedItem.isFinishedTraversal = true

        // Then
        assertThat(testedItem.isValid()).isFalse()
    }

    @Test
    fun `M return true W isValid() { Snapshot with nodes }`() {
        // Then
        assertThat(testedItem.isValid()).isTrue()
    }

    @Test
    fun `M return true W isValid() { fresh node that has not finished traversal }`() {
        // Given
        testedItem = SnapshotRecordedDataQueueItem(
            fakeRecordedQueuedItemContext,
            mockSystemInformation
        )

        // Then
        assertThat(testedItem.isValid()).isTrue()
    }

    @Test
    fun `M return true W isReady() { Snapshot with no pending images }`() {
        // Given
        testedItem.pendingJobs.set(0)
        testedItem.isFinishedTraversal = true

        // Then
        assertThat(testedItem.isReady()).isTrue()
    }

    @Test
    fun `M return false W isReady() { not finished traveral }`() {
        // Given
        testedItem.pendingJobs.set(0)
        testedItem.isFinishedTraversal = false

        // Then
        assertThat(testedItem.isReady()).isFalse()
    }

    @Test
    fun `M return false W isReady() { Snapshot with pending images greater than 0 }`() {
        // Given
        testedItem.incrementPendingJobs()

        // Then
        assertThat(testedItem.isReady()).isFalse()
    }

    @Test
    fun `M increment pending images W incrementPendingImages()`() {
        // Given
        val initial = testedItem.pendingJobs.get()

        // When
        testedItem.incrementPendingJobs()

        // Then
        assertThat(testedItem.pendingJobs.get()).isEqualTo(initial + 1)
    }

    @Test
    fun `M decrement pending images W decrementPendingImages()`() {
        // Given
        val initial = testedItem.pendingJobs.get()

        // When
        testedItem.decrementPendingJobs()

        // Then
        assertThat(testedItem.pendingJobs.get()).isEqualTo(initial - 1)
    }
}
