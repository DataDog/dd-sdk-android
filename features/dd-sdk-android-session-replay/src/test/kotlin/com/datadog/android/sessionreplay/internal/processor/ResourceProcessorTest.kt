/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.processor

import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.android.sessionreplay.internal.resources.ResourceDataStoreManager
import com.datadog.android.sessionreplay.internal.storage.ResourcesWriter
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
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(ForgeConfigurator::class)
internal class ResourceProcessorTest {

    private lateinit var testedProcessor: ResourceProcessor

    @Mock
    lateinit var mockResourceDataStoreManager: ResourceDataStoreManager

    @Mock
    lateinit var mockResourcesWriter: ResourcesWriter

    @BeforeEach
    fun `set up`() {
        testedProcessor = ResourceProcessor(
            resourceDataStoreManager = mockResourceDataStoreManager,
            resourcesWriter = mockResourcesWriter
        )
    }

    @Test
    fun `M drop resource W process { resource was previously sent }`() {
        // Given
        whenever(mockResourceDataStoreManager.markResourceAsSentIfNew(FAKE_RESOURCE_ID))
            .thenReturn(false)

        // When
        testedProcessor.process(FAKE_RESOURCE_ID, byteArrayOf(1, 2, 3), FAKE_MIME_TYPE)

        // Then
        verify(mockResourceDataStoreManager).markResourceAsSentIfNew(FAKE_RESOURCE_ID)
        verifyNoInteractions(mockResourcesWriter)
    }

    @Test
    fun `M mark and write resource W process`() {
        // Given
        val resourceData = byteArrayOf(1, 2, 3)
        whenever(mockResourceDataStoreManager.markResourceAsSentIfNew(FAKE_RESOURCE_ID))
            .thenReturn(true)

        // When
        testedProcessor.process(FAKE_RESOURCE_ID, resourceData, FAKE_MIME_TYPE)

        // Then
        verify(mockResourceDataStoreManager).markResourceAsSentIfNew(FAKE_RESOURCE_ID)
        argumentCaptor<EnrichedResource> {
            verify(mockResourcesWriter).write(capture())
            assertThat(firstValue.resource).isEqualTo(resourceData)
            assertThat(firstValue.filename).isEqualTo(FAKE_RESOURCE_ID)
            assertThat(firstValue.mimeType).isEqualTo(FAKE_MIME_TYPE)
        }
    }

    @Test
    fun `M preserve null mime type W process`() {
        // Given
        val resourceData = byteArrayOf(1, 2, 3)
        whenever(mockResourceDataStoreManager.markResourceAsSentIfNew(FAKE_RESOURCE_ID))
            .thenReturn(true)

        // When
        testedProcessor.process(FAKE_RESOURCE_ID, resourceData, null)

        // Then
        argumentCaptor<EnrichedResource> {
            verify(mockResourcesWriter).write(capture())
            assertThat(firstValue.mimeType).isNull()
        }
    }

    private companion object {
        const val FAKE_RESOURCE_ID = "resource-id"
        const val FAKE_MIME_TYPE = "image/png"
    }
}
