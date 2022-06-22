/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.data.upload

import android.content.Context
import android.util.Log
import androidx.work.ListenableWorker
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.datadog.android.Datadog
import com.datadog.android.core.internal.net.UploadStatus
import com.datadog.android.utils.config.ApplicationContextTestConfiguration
import com.datadog.android.utils.config.LoggerTestConfiguration
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.v2.api.NoOpSDKCore
import com.datadog.android.v2.api.context.DatadogContext
import com.datadog.android.v2.core.DatadogCore
import com.datadog.android.v2.core.DatadogFeature
import com.datadog.android.v2.core.internal.net.DataUploader
import com.datadog.android.v2.core.internal.storage.BatchConfirmation
import com.datadog.android.v2.core.internal.storage.BatchId
import com.datadog.android.v2.core.internal.storage.BatchReader
import com.datadog.android.v2.core.internal.storage.Storage
import com.datadog.tools.unit.annotations.TestConfigurationsProvider
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import com.datadog.tools.unit.extensions.config.TestConfiguration
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doAnswer
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyZeroInteractions
import com.nhaarman.mockitokotlin2.whenever
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.mockito.Mock
import org.mockito.invocation.InvocationOnMock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness
import org.mockito.stubbing.Answer

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class),
    ExtendWith(TestConfigurationExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class UploadWorkerTest {

    private lateinit var testedWorker: Worker

    @Mock
    lateinit var mockGlobalSdkCore: DatadogCore

    @Mock
    lateinit var mockFeatureA: DatadogFeature

    @Mock
    lateinit var mockStorageA: Storage

    @Mock
    lateinit var mockBatchReaderA: BatchReader

    @Mock
    lateinit var mockUploaderA: DataUploader

    @Mock
    lateinit var mockFeatureB: DatadogFeature

    @Mock
    lateinit var mockStorageB: Storage

    @Mock
    lateinit var mockBatchReaderB: BatchReader

    @Mock
    lateinit var mockUploaderB: DataUploader

    @Forgery
    lateinit var fakeWorkerParameters: WorkerParameters

    @Forgery
    lateinit var fakeContext: DatadogContext

    @BeforeEach
    fun `set up`() {
        Datadog.initialized.set(true)
        Datadog.globalSDKCore = mockGlobalSdkCore

        whenever(mockGlobalSdkCore.context) doReturn fakeContext

        stubFeatures(
            mockGlobalSdkCore,
            listOf(mockFeatureA, mockFeatureB),
            listOf(mockStorageA, mockStorageB),
            listOf(mockUploaderA, mockUploaderB)
        )

        testedWorker = UploadWorker(
            appContext.mockInstance,
            fakeWorkerParameters
        )
    }

    @AfterEach
    fun `tear down`() {
        Datadog.globalSDKCore = NoOpSDKCore()
        Datadog.initialized.set(false)
    }

    // region doWork

    @Test
    fun `𝕄 send batches 𝕎 doWork() {single batch per feature}`(
        @StringForgery batchA: List<String>,
        @StringForgery batchAMeta: String,
        @StringForgery batchB: List<String>,
        @StringForgery batchBMeta: String,
        forge: Forge
    ) {
        // Given
        val batchAData = batchA.map { it.toByteArray() }
        val batchBData = batchB.map { it.toByteArray() }
        val batchAMetadata = forge.aNullable { batchAMeta.toByteArray() }
        val batchBMetadata = forge.aNullable { batchBMeta.toByteArray() }

        val batchAConfirmation = mock<BatchConfirmation>()
        stubReadSequence(
            mockStorageA,
            fakeContext,
            mockBatchReaderA,
            mock(),
            batchAConfirmation,
            batchAData,
            batchAMetadata
        )

        val batchBConfirmation = mock<BatchConfirmation>()
        stubReadSequence(
            mockStorageB,
            fakeContext,
            mockBatchReaderB,
            mock(),
            batchBConfirmation,
            batchBData,
            batchBMetadata
        )

        whenever(
            mockUploaderA.upload(
                fakeContext,
                batchAData,
                batchAMetadata
            )
        ) doReturn UploadStatus.SUCCESS
        whenever(
            mockUploaderB.upload(
                fakeContext,
                batchBData,
                batchBMetadata
            )
        ) doReturn UploadStatus.SUCCESS

        // When
        val result = testedWorker.doWork()

        // Then
        verify(mockUploaderA).upload(
            fakeContext,
            batchAData,
            batchAMetadata
        )
        verify(mockUploaderB).upload(
            fakeContext,
            batchBData,
            batchBMetadata
        )

        verify(batchAConfirmation).markAsRead(true)
        verify(batchBConfirmation).markAsRead(true)

        assertThat(result)
            .isEqualTo(ListenableWorker.Result.success())
    }

    @ParameterizedTest
    @EnumSource(UploadStatus::class, names = ["SUCCESS"], mode = EnumSource.Mode.EXCLUDE)
    fun `𝕄 send and keep batches 𝕎 doWork() {single batch per feature with error}`(
        status: UploadStatus,
        @StringForgery batchA: List<String>,
        @StringForgery batchAMeta: String,
        @StringForgery batchB: List<String>,
        @StringForgery batchBMeta: String,
        forge: Forge
    ) {
        // Given
        val batchAData = batchA.map { it.toByteArray() }
        val batchBData = batchB.map { it.toByteArray() }
        val batchAMetadata = forge.aNullable { batchAMeta.toByteArray() }
        val batchBMetadata = forge.aNullable { batchBMeta.toByteArray() }

        val batchAConfirmation = mock<BatchConfirmation>()
        stubReadSequence(
            mockStorageA,
            fakeContext,
            mockBatchReaderA,
            mock(),
            batchAConfirmation,
            batchAData,
            batchAMetadata
        )

        val batchBConfirmation = mock<BatchConfirmation>()
        stubReadSequence(
            mockStorageB,
            fakeContext,
            mockBatchReaderB,
            mock(),
            batchBConfirmation,
            batchBData,
            batchBMetadata
        )

        whenever(
            mockUploaderA.upload(
                fakeContext,
                batchAData,
                batchAMetadata
            )
        ) doReturn status
        whenever(
            mockUploaderB.upload(
                fakeContext,
                batchBData,
                batchBMetadata
            )
        ) doReturn status

        // When
        val result = testedWorker.doWork()

        // Then
        verify(mockUploaderA).upload(
            fakeContext,
            batchAData,
            batchAMetadata
        )
        verify(mockUploaderB).upload(
            fakeContext,
            batchBData,
            batchBMetadata
        )
        verify(batchAConfirmation).markAsRead(false)
        verify(batchBConfirmation).markAsRead(false)

        assertThat(result)
            .isEqualTo(ListenableWorker.Result.success())
    }

    @Test
    fun `𝕄 send batches 𝕎 doWork() {multiple batches, all Success}`(forge: Forge) {
        // Given
        val batchesA = forge.aList {
            forge.aList { forge.aString().toByteArray() }
        }
        val batchesAMeta = forge.aList(batchesA.size) {
            forge.aNullable { forge.aString().toByteArray() }
        }
        val aIds = forge.aList(batchesA.size) { mock<BatchId>() }
        val aConfirmations = forge.aList(batchesA.size) { mock<BatchConfirmation>() }
        val aReaders = forge.aList(batchesA.size) { mock<BatchReader>() }

        val batchB = forge.aList { forge.aString().toByteArray() }
        val batchBMeta = forge.aString().toByteArray()

        stubMultipleReadSequence(
            mockStorageA,
            fakeContext,
            aReaders,
            aIds,
            aConfirmations,
            batchesA,
            batchesAMeta
        )

        val batchBConfirmation = mock<BatchConfirmation>()
        stubReadSequence(
            mockStorageB,
            fakeContext,
            mockBatchReaderB,
            mock(),
            batchBConfirmation,
            batchB,
            batchBMeta
        )

        batchesA.forEachIndexed { index, batch ->
            whenever(
                mockUploaderA.upload(
                    fakeContext,
                    batch,
                    batchesAMeta[index]
                )
            ) doReturn UploadStatus.SUCCESS
        }

        whenever(
            mockUploaderB.upload(
                fakeContext,
                batchB,
                batchBMeta
            )
        ) doReturn UploadStatus.SUCCESS

        // When
        val result = testedWorker.doWork()

        // Then
        batchesA.forEachIndexed { index, batch ->
            verify(mockUploaderA).upload(
                fakeContext,
                batch,
                batchesAMeta[index]
            )

            verify(aConfirmations[index]).markAsRead(true)
        }

        verify(mockUploaderB).upload(
            fakeContext,
            batchB,
            batchBMeta
        )
        verify(batchBConfirmation).markAsRead(true)

        assertThat(result)
            .isEqualTo(ListenableWorker.Result.success())
    }

    @ParameterizedTest
    @EnumSource(UploadStatus::class, names = ["SUCCESS"], mode = EnumSource.Mode.EXCLUDE)
    fun `𝕄 send batches 𝕎 doWork() {multiple batches, some fails}`(
        failingStatus: UploadStatus,
        forge: Forge
    ) {
        // Given
        val batchesA = forge.aList {
            forge.aList { forge.aString().toByteArray() }
        }
        val batchesAMeta = forge.aList(batchesA.size) {
            forge.aNullable { forge.aString().toByteArray() }
        }
        val aIds = forge.aList(batchesA.size) { mock<BatchId>() }
        val aConfirmations = forge.aList(batchesA.size) { mock<BatchConfirmation>() }
        val aReaders = forge.aList(batchesA.size) { mock<BatchReader>() }

        val batchB = forge.aList { forge.aString().toByteArray() }
        val batchBMeta = forge.aString().toByteArray()

        val failingBatchIndex = forge.anInt(min = 0, max = batchesA.size)

        stubMultipleReadSequence(
            mockStorageA,
            fakeContext,
            aReaders,
            aIds,
            aConfirmations,
            batchesA,
            batchesAMeta
        )

        val batchBConfirmation = mock<BatchConfirmation>()
        stubReadSequence(
            mockStorageB,
            fakeContext,
            mockBatchReaderB,
            mock(),
            batchBConfirmation,
            batchB,
            batchBMeta
        )

        batchesA.forEachIndexed { index, batch ->
            whenever(
                mockUploaderA.upload(
                    fakeContext,
                    batch,
                    batchesAMeta[index]
                )
            ) doReturn if (index == failingBatchIndex) failingStatus else UploadStatus.SUCCESS
        }

        whenever(
            mockUploaderB.upload(
                fakeContext,
                batchB,
                batchBMeta
            )
        ) doReturn UploadStatus.SUCCESS

        // When
        val result = testedWorker.doWork()

        // Then
        batchesA.forEachIndexed { index, batch ->
            verify(mockUploaderA).upload(
                fakeContext,
                batch,
                batchesAMeta[index]
            )

            if (index != failingBatchIndex) {
                verify(aConfirmations[index]).markAsRead(true)
            } else {
                verify(aConfirmations[index]).markAsRead(false)
            }
        }

        verify(mockUploaderB).upload(
            fakeContext,
            batchB,
            batchBMeta
        )
        verify(batchBConfirmation).markAsRead(true)

        assertThat(result)
            .isEqualTo(ListenableWorker.Result.success())
    }

    // endregion

    // region private

    private fun stubFeatures(
        core: DatadogCore,
        features: List<DatadogFeature>,
        storages: List<Storage>,
        uploaders: List<DataUploader>
    ) {
        whenever(core.getAllFeatures()) doReturn features
        features.forEachIndexed { index, feature ->
            whenever(feature.uploader) doReturn uploaders[index]
            whenever(feature.storage) doReturn storages[index]
        }
    }

    private fun stubReadSequence(
        storage: Storage,
        context: DatadogContext,
        batchReader: BatchReader,
        batchId: BatchId,
        batchConfirmation: BatchConfirmation,
        batch: List<ByteArray>,
        batchMetadata: ByteArray?
    ) {
        stubMultipleReadSequence(
            storage,
            context,
            listOf(batchReader),
            listOf(batchId),
            listOf(batchConfirmation),
            listOf(batch),
            listOf(batchMetadata)
        )
    }

    private fun stubMultipleReadSequence(
        storage: Storage,
        context: DatadogContext,
        batchReaders: List<BatchReader>,
        batchIds: List<BatchId>,
        batchConfirmations: List<BatchConfirmation>,
        batches: List<List<ByteArray>>,
        batchMetadata: List<ByteArray?>
    ) {
        whenever(storage.readNextBatch(eq(context), any())).thenAnswer(object : Answer<Unit> {
            var invocationCount: Int = 0

            override fun answer(invocation: InvocationOnMock) {
                if (invocationCount >= batches.size) return

                val reader = batchReaders[invocationCount]
                val batchId = batchIds[invocationCount]
                val batchConfirmation = batchConfirmations[invocationCount]

                whenever(reader.read()) doReturn batches[invocationCount]
                whenever(reader.currentMetadata()) doReturn batchMetadata[invocationCount]

                invocationCount++

                whenever(storage.confirmBatchRead(eq(batchId), any())) doAnswer {
                    (it.getArgument<(BatchConfirmation) -> Unit>(1)).invoke(batchConfirmation)
                }

                (invocation.getArgument<(BatchId, BatchReader) -> Unit>(1)).invoke(
                    batchId,
                    reader
                )
            }
        })
    }

    @Test
    fun `𝕄 log error 𝕎 doWork() { SDK is not initialized }`() {
        // Given
        Datadog.initialized.set(false)

        // When
        val result = testedWorker.doWork()

        // Then
        verify(logger.mockDevLogHandler).handleLog(
            Log.ERROR,
            Datadog.MESSAGE_NOT_INITIALIZED
        )
        verifyZeroInteractions(mockFeatureA, mockBatchReaderA, mockUploaderA)
        verifyZeroInteractions(mockFeatureB, mockBatchReaderB, mockUploaderB)

        assertThat(result)
            .isEqualTo(ListenableWorker.Result.success())
    }

    // endregion

    companion object {
        val logger = LoggerTestConfiguration()
        val appContext = ApplicationContextTestConfiguration(Context::class.java)

        @TestConfigurationsProvider
        @JvmStatic
        fun getTestConfigurations(): List<TestConfiguration> {
            return listOf(appContext, logger)
        }
    }
}
