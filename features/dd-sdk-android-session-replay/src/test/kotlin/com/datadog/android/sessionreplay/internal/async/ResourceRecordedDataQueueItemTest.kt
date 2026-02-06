/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.async

import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(ForgeConfigurator::class)
internal class ResourceRecordedDataQueueItemTest {
    @Test
    fun `M return false W isValid() { no resource data }`(
        @Forgery fakeResourceRecordedDataQueueItem: ResourceRecordedDataQueueItem
    ) {
        // Given
        val testedRecordedDataQueueItem = ResourceRecordedDataQueueItem(
            recordedQueuedItemContext = fakeResourceRecordedDataQueueItem.recordedQueuedItemContext,
            identifier = fakeResourceRecordedDataQueueItem.identifier,
            resourceData = ByteArray(0),
            creationTimestampInNs = fakeResourceRecordedDataQueueItem.creationTimestampInNs
        )

        // Then
        assertThat(testedRecordedDataQueueItem.isValid()).isFalse()
    }

    @Test
    fun `M return true W isValid() { has resource data }`(
        @Forgery testedRecordedDataQueueItem: ResourceRecordedDataQueueItem
    ) {
        // Then
        assertThat(testedRecordedDataQueueItem.isValid()).isTrue()
    }

    @Test
    fun `M return true W isReady()`(
        @Forgery testedRecordedDataQueueItem: ResourceRecordedDataQueueItem
    ) {
        // Then
        assertThat(testedRecordedDataQueueItem.isReady()).isTrue()
    }
}
