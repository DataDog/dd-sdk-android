/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.resources

import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.android.sessionreplay.internal.async.DataQueueHandler
import fr.xgouchet.elmyr.annotation.StringForgery
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
internal class ResourceItemCreationHandlerTest {
    private lateinit var testedHandler: ResourceItemCreationHandler

    @Mock
    lateinit var mockDataQueueHandler: DataQueueHandler

    @StringForgery
    lateinit var fakeApplicationId: String

    @StringForgery
    lateinit var fakeResourceId: String

    @BeforeEach
    fun `set up`() {
        testedHandler = ResourceItemCreationHandler(
            applicationId = fakeApplicationId,
            recordedDataQueueHandler = mockDataQueueHandler
        )
    }

    @Test
    fun `M queue item W queueItemIfNotPreviouslySeen() { not previously seen }`() {
        // Given
        val fakeByteArray = fakeResourceId.toByteArray()

        // When
        testedHandler.queueItem(fakeResourceId, fakeByteArray)

        // Then
        verify(mockDataQueueHandler).addResourceItem(
            identifier = fakeResourceId,
            resourceData = fakeByteArray,
            applicationId = fakeApplicationId
        )
    }

    @Test
    fun `M not queue item W queueItemIfNotPreviouslySeen() { previously seen }`() {
        // Given
        val fakeByteArray = fakeResourceId.toByteArray()

        // When
        testedHandler.queueItem(fakeResourceId, fakeByteArray)
        testedHandler.queueItem(fakeResourceId, fakeByteArray)

        // Then
        verify(mockDataQueueHandler).addResourceItem(
            identifier = fakeResourceId,
            resourceData = fakeByteArray,
            applicationId = fakeApplicationId
        )
    }

    @Test
    fun `M add unique resourceId only once W queueItemIfNotPreviouslySeen()`() {
        // Given
        val fakeByteArray = fakeResourceId.toByteArray()

        // When
        testedHandler.queueItem(fakeResourceId, fakeByteArray)
        testedHandler.queueItem(fakeResourceId, fakeByteArray)

        // Then
        assert(testedHandler.resourceIdsSeen.size == 1)
    }
}
