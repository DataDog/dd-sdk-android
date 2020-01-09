/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2020 Datadog, Inc.
 */

package com.datadog.android.core.internal.data

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.datadog.android.Datadog
import com.datadog.android.core.internal.domain.Batch
import com.datadog.android.core.internal.domain.PersistenceStrategy
import com.datadog.android.log.internal.domain.Log
import com.datadog.android.log.internal.net.LogUploadStatus
import com.datadog.android.log.internal.net.LogUploader
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.utils.mockContext
import com.datadog.tools.unit.invokeMethod
import com.datadog.tools.unit.setFieldValue
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
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
@ForgeConfiguration(Configurator::class)
internal class UploadWorkerTest {

    lateinit var testedWorker: Worker

    @Mock
    lateinit var mockContext: Context
    @Mock
    lateinit var mockLogStrategy: PersistenceStrategy<Log>
    @Mock
    lateinit var mockLogReader: Reader
    @Mock
    lateinit var mockLogUploader: LogUploader

    @Forgery
    lateinit var fakeWorkerParameters: WorkerParameters

    @BeforeEach
    fun `set up`() {
        whenever(mockLogStrategy.getReader()) doReturn mockLogReader

        mockContext = mockContext()
        Datadog.initialize(mockContext, "<CLIENT_TOKEN>")

        Datadog.setFieldValue("logStrategy", mockLogStrategy)
        Datadog.setFieldValue("uploader", mockLogUploader)

        testedWorker = UploadWorker(mockContext, fakeWorkerParameters)
    }

    @AfterEach
    fun `tear down`() {
        Datadog.invokeMethod("stop")
    }

    @Test
    fun `doWork single batch Success`(
        @Forgery batch: Batch
    ) {
        whenever(mockLogReader.readNextBatch()).doReturn(batch, null)
        whenever(mockLogUploader.upload(batch.data)) doReturn LogUploadStatus.SUCCESS

        val result = testedWorker.doWork()

        verify(mockLogReader).dropBatch(batch.id)
        verify(mockLogReader, never()).releaseBatch(batch.id)
        assertThat(result)
            .isEqualTo(ListenableWorker.Result.success())
    }

    @Test
    fun `doWork single batch Failure`(
        @Forgery batch: Batch,
        forge: Forge
    ) {
        val status = forge.aValueFrom(
            LogUploadStatus::class.java,
            exclude = listOf(LogUploadStatus.SUCCESS)
        )
        whenever(mockLogReader.readNextBatch()).doReturn(batch, null)
        whenever(mockLogUploader.upload(batch.data)) doReturn status

        val result = testedWorker.doWork()

        verify(mockLogReader, never()).dropBatch(batch.id)
        verify(mockLogReader).releaseBatch(batch.id)
        assertThat(result)
            .isEqualTo(ListenableWorker.Result.success())
    }

    @Test
    fun `doWork mutliple batches all Success`(
        @Forgery batches: List<Batch>
    ) {
        val firstBatch = batches.first()
        val otherBatchesThenNull = Array(batches.size) {
            batches.getOrNull(it + 1)
        }
        whenever(mockLogReader.readNextBatch()).doReturn(firstBatch, *otherBatchesThenNull)
        batches.forEach {
            whenever(mockLogUploader.upload(it.data)) doReturn LogUploadStatus.SUCCESS
        }

        val result = testedWorker.doWork()

        batches.forEach {
            verify(mockLogReader).dropBatch(it.id)
            verify(mockLogReader, never()).releaseBatch(it.id)
        }
        assertThat(result)
            .isEqualTo(ListenableWorker.Result.success())
    }

    @Test
    fun `doWork mutliple batches first Failed`(
        @Forgery batches: List<Batch>,
        forge: Forge
    ) {
        val status = forge.aValueFrom(
            LogUploadStatus::class.java,
            exclude = listOf(LogUploadStatus.SUCCESS)
        )
        val firstBatch = batches.first()
        val otherBatchesThenNull = Array(batches.size) {
            batches.getOrNull(it + 1)
        }
        whenever(mockLogReader.readNextBatch()).doReturn(firstBatch, *otherBatchesThenNull)
        whenever(mockLogUploader.upload(any())) doReturn LogUploadStatus.SUCCESS
        whenever(mockLogUploader.upload(firstBatch.data)) doReturn status

        val result = testedWorker.doWork()

        batches.forEach {
            if (it == firstBatch) {
                verify(mockLogReader, never()).dropBatch(it.id)
                verify(mockLogReader).releaseBatch(it.id)
            } else {
                verify(mockLogReader).dropBatch(it.id)
                verify(mockLogReader, never()).releaseBatch(it.id)
            }
        }
        assertThat(result)
            .isEqualTo(ListenableWorker.Result.success())
    }
}
