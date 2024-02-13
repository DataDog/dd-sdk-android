/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.storage

import com.datadog.android.api.context.DatadogContext
import com.datadog.android.api.feature.FeatureScope
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.api.storage.EventBatchWriter
import com.datadog.android.api.storage.RawBatchEvent
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.android.sessionreplay.internal.ResourcesFeature
import com.datadog.android.sessionreplay.internal.processor.EnrichedResource
import com.datadog.android.sessionreplay.internal.processor.asBinaryMetadata
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(ForgeConfigurator::class)
internal class SessionReplayResourcesWriterTest {
    private lateinit var testedWriter: SessionReplayResourcesWriter

    @Mock
    lateinit var mockFeatureSdkCore: FeatureSdkCore

    @Mock
    lateinit var mockResourcesFeature: FeatureScope

    @Forgery
    lateinit var fakeEnrichedResource: EnrichedResource

    @Mock
    lateinit var mockEventBatchWriter: EventBatchWriter

    @Forgery
    lateinit var fakeDatadogContext: DatadogContext

    @BeforeEach
    fun setup() {
        whenever(mockEventBatchWriter.write(anyOrNull(), anyOrNull()))
            .thenReturn(true)

        whenever(mockFeatureSdkCore.getFeature(ResourcesFeature.SESSION_REPLAY_RESOURCES_FEATURE_NAME))
            .thenReturn(mockResourcesFeature)

        testedWriter = SessionReplayResourcesWriter(
            sdkCore = mockFeatureSdkCore
        )
    }

    @Test
    fun `M write the resource W write()`() {
        // Given
        whenever(mockResourcesFeature.withWriteContext(anyOrNull(), anyOrNull())) doAnswer {
            val callback = it.getArgument<(DatadogContext, EventBatchWriter) -> Unit>(1)
            callback.invoke(fakeDatadogContext, mockEventBatchWriter)
        }

        // When
        testedWriter.write(fakeEnrichedResource)

        // Then
        val metadataBytearray = fakeEnrichedResource.asBinaryMetadata()
        verify(mockEventBatchWriter).write(
            RawBatchEvent(data = fakeEnrichedResource.resource, metadata = metadataBytearray),
            batchMetadata = null
        )
    }
}
