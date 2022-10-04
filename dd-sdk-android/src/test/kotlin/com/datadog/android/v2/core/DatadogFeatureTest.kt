/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.v2.core

import com.datadog.android.utils.forge.Configurator
import com.datadog.android.v2.api.EventBatchWriter
import com.datadog.android.v2.api.context.DatadogContext
import com.datadog.android.v2.core.internal.ContextProvider
import com.datadog.android.v2.core.internal.net.DataUploader
import com.datadog.android.v2.core.internal.storage.BatchWriter
import com.datadog.android.v2.core.internal.storage.Storage
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doAnswer
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyZeroInteractions
import com.nhaarman.mockitokotlin2.whenever
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
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class),
    ExtendWith(TestConfigurationExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class DatadogFeatureTest {

    lateinit var testedFeature: DatadogFeature

    @Mock
    lateinit var mockSdkCore: DatadogCore

    @Mock
    lateinit var mockStorage: Storage

    @Mock
    lateinit var mockUploader: DataUploader

    @Mock
    lateinit var mockContextProvider: ContextProvider

    @Mock
    lateinit var mockBatchWriter: BatchWriter

    @Forgery
    lateinit var fakeContext: DatadogContext

    @BeforeEach
    fun `set up`() {
        whenever(mockSdkCore.contextProvider) doReturn mockContextProvider
        whenever(mockContextProvider.context) doReturn fakeContext

        testedFeature = DatadogFeature(
            mockSdkCore,
            mockStorage,
            mockUploader
        )
    }

    @Test
    fun `𝕄 provide write context 𝕎 withWriteContext(callback)`() {
        // Given
        val callback = mock<(DatadogContext, EventBatchWriter) -> Unit>()

        whenever(mockStorage.writeCurrentBatch(eq(fakeContext), any())) doAnswer {
            val storageCallback = it.getArgument<(BatchWriter) -> Unit>(1)
            storageCallback.invoke(mockBatchWriter)
        }

        // When
        testedFeature.withWriteContext(callback)

        // Then
        verify(callback).invoke(
            fakeContext,
            mockBatchWriter
        )
    }

    @Test
    fun `𝕄 do nothing 𝕎 withWriteContext(callback) { no Datadog context }`() {
        // Given
        val callback = mock<(DatadogContext, EventBatchWriter) -> Unit>()

        whenever(mockSdkCore.contextProvider) doReturn null

        // When
        testedFeature.withWriteContext(callback)

        // Then
        verifyZeroInteractions(mockStorage)
        verifyZeroInteractions(callback)
    }
}
