/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.data.upload

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.datadog.android.Datadog
import com.datadog.android.DatadogConfig
import com.datadog.android.core.internal.data.Reader
import com.datadog.android.core.internal.domain.Batch
import com.datadog.android.core.internal.domain.PersistenceStrategy
import com.datadog.android.core.internal.net.UploadStatus
import com.datadog.android.error.internal.CrashReportsFeature
import com.datadog.android.log.internal.LogsFeature
import com.datadog.android.log.internal.domain.Log
import com.datadog.android.log.internal.net.LogsOkHttpUploader
import com.datadog.android.tracing.internal.TracesFeature
import com.datadog.android.tracing.internal.net.TracesOkHttpUploader
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.utils.mockContext
import com.datadog.tools.unit.invokeMethod
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import datadog.opentracing.DDSpan
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assumptions.assumeTrue
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
    lateinit var mockLogsStrategy: PersistenceStrategy<Log>

    @Mock
    lateinit var mockTracesStrategy: PersistenceStrategy<DDSpan>

    @Mock
    lateinit var mockCrashReportsStrategy: PersistenceStrategy<Log>

    @Mock
    lateinit var mockLogsReader: Reader

    @Mock
    lateinit var mockTracesReader: Reader

    @Mock
    lateinit var mockCrashReportsReader: Reader

    @Mock
    lateinit var mockLogsUploader: LogsOkHttpUploader

    @Mock
    lateinit var mockTracesUploader: TracesOkHttpUploader

    @Mock
    lateinit var mockCrashReportsUploader: LogsOkHttpUploader

    @Forgery
    lateinit var fakeWorkerParameters: WorkerParameters

    @BeforeEach
    fun `set up`() {
        whenever(mockLogsStrategy.getReader()) doReturn mockLogsReader
        whenever(mockTracesStrategy.getReader()) doReturn mockTracesReader
        whenever(mockCrashReportsStrategy.getReader()) doReturn mockCrashReportsReader

        mockContext = mockContext()
        Datadog.initialize(
            mockContext,
            DatadogConfig.Builder("CLIENT_TOKEN", "ENVIRONMENT").build()
        )

        LogsFeature.persistenceStrategy = mockLogsStrategy
        LogsFeature.uploader = mockLogsUploader
        TracesFeature.persistenceStrategy = mockTracesStrategy
        TracesFeature.uploader = mockTracesUploader
        CrashReportsFeature.persistenceStrategy = mockCrashReportsStrategy
        CrashReportsFeature.uploader = mockCrashReportsUploader

        testedWorker = UploadWorker(
            mockContext,
            fakeWorkerParameters
        )
    }

    @AfterEach
    fun `tear down`() {
        Datadog.invokeMethod("stop")
    }

    @Test
    fun `doWork single batch Success`(
        @Forgery logsBatch: Batch,
        @Forgery tracesBatch: Batch,
        @Forgery crashReportsBatch: Batch
    ) {
        whenever(mockLogsReader.readNextBatch()).doReturn(logsBatch, null)
        whenever(mockLogsUploader.upload(logsBatch.data)) doReturn UploadStatus.SUCCESS
        whenever(mockTracesReader.readNextBatch()).doReturn(tracesBatch, null)
        whenever(mockTracesUploader.upload(tracesBatch.data)) doReturn UploadStatus.SUCCESS
        whenever(mockCrashReportsReader.readNextBatch()).doReturn(crashReportsBatch, null)
        whenever(mockCrashReportsUploader.upload(crashReportsBatch.data))
            .doReturn(UploadStatus.SUCCESS)

        val result = testedWorker.doWork()

        verify(mockLogsReader).dropBatch(logsBatch.id)
        verify(mockLogsReader, never()).releaseBatch(logsBatch.id)
        verify(mockTracesReader).dropBatch(tracesBatch.id)
        verify(mockTracesReader, never()).releaseBatch(tracesBatch.id)
        verify(mockCrashReportsReader).dropBatch(crashReportsBatch.id)
        verify(mockCrashReportsReader, never()).releaseBatch(crashReportsBatch.id)
        assertThat(result)
            .isEqualTo(ListenableWorker.Result.success())
    }

    @Test
    fun `doWork single batch Failure`(
        @Forgery logsBatch: Batch,
        @Forgery tracesBatch: Batch,
        @Forgery crashReportsBatch: Batch,
        forge: Forge
    ) {
        val status = forge.aValueFrom(
            UploadStatus::class.java,
            exclude = listOf(UploadStatus.SUCCESS)
        )
        whenever(mockLogsReader.readNextBatch()).doReturn(logsBatch, null)
        whenever(mockLogsUploader.upload(logsBatch.data)) doReturn status
        whenever(mockTracesReader.readNextBatch()).doReturn(tracesBatch, null)
        whenever(mockTracesUploader.upload(tracesBatch.data)) doReturn status
        whenever(mockCrashReportsReader.readNextBatch()).doReturn(crashReportsBatch, null)
        whenever(mockCrashReportsUploader.upload(crashReportsBatch.data)) doReturn status

        val result = testedWorker.doWork()

        verify(mockLogsReader, never()).dropBatch(logsBatch.id)
        verify(mockLogsReader).releaseBatch(logsBatch.id)
        verify(mockTracesReader, never()).dropBatch(tracesBatch.id)
        verify(mockTracesReader).releaseBatch(tracesBatch.id)
        verify(mockCrashReportsReader, never()).dropBatch(crashReportsBatch.id)
        verify(mockCrashReportsReader).releaseBatch(crashReportsBatch.id)
        assertThat(result)
            .isEqualTo(ListenableWorker.Result.success())
    }

    @Test
    fun `doWork multiple logs batches all Success`(
        @Forgery batches: List<Batch>
    ) {
        assumeTrue {
            // make sure there are no id duplicates
            batches.map { it.id }.toSet().size == batches.size
        }
        val firstBatch = batches.first()
        val otherBatchesThenNull = Array(batches.size) {
            batches.getOrNull(it + 1)
        }
        whenever(mockLogsReader.readNextBatch()).doReturn(firstBatch, *otherBatchesThenNull)
        batches.forEach {
            whenever(mockLogsUploader.upload(it.data)) doReturn UploadStatus.SUCCESS
        }

        val result = testedWorker.doWork()

        batches.forEach {
            verify(mockLogsReader).dropBatch(it.id)
            verify(mockLogsReader, never()).releaseBatch(it.id)
        }
        verify(mockTracesReader, never()).dropBatch(any())
        verify(mockTracesReader, never()).releaseBatch(any())
        verify(mockCrashReportsReader, never()).dropBatch(any())
        verify(mockCrashReportsReader, never()).releaseBatch(any())
        assertThat(result)
            .isEqualTo(ListenableWorker.Result.success())
    }

    @Test
    fun `doWork multiple traces batches all Success`(
        @Forgery batches: List<Batch>
    ) {
        assumeTrue {
            // make sure there are no id duplicates
            batches.map { it.id }.toSet().size == batches.size
        }
        val firstBatch = batches.first()
        val otherBatchesThenNull = Array(batches.size) {
            batches.getOrNull(it + 1)
        }
        whenever(mockTracesReader.readNextBatch()).doReturn(firstBatch, *otherBatchesThenNull)
        batches.forEach {
            whenever(mockTracesUploader.upload(it.data)) doReturn UploadStatus.SUCCESS
        }

        val result = testedWorker.doWork()

        batches.forEach {
            verify(mockTracesReader).dropBatch(it.id)
            verify(mockTracesReader, never()).releaseBatch(it.id)
        }
        verify(mockLogsReader, never()).dropBatch(any())
        verify(mockLogsReader, never()).releaseBatch(any())
        verify(mockCrashReportsReader, never()).dropBatch(any())
        verify(mockCrashReportsReader, never()).releaseBatch(any())
        assertThat(result)
            .isEqualTo(ListenableWorker.Result.success())
    }

    @Test
    fun `doWork multiple crashReports batches all Success`(
        @Forgery batches: List<Batch>
    ) {
        assumeTrue {
            // make sure there are no id duplicates
            batches.map { it.id }.toSet().size == batches.size
        }
        val firstBatch = batches.first()
        val otherBatchesThenNull = Array(batches.size) {
            batches.getOrNull(it + 1)
        }
        whenever(mockCrashReportsReader.readNextBatch())
            .doReturn(firstBatch, *otherBatchesThenNull)
        batches.forEach {
            whenever(mockCrashReportsUploader.upload(it.data)) doReturn UploadStatus.SUCCESS
        }

        val result = testedWorker.doWork()

        batches.forEach {
            verify(mockCrashReportsReader).dropBatch(it.id)
            verify(mockCrashReportsReader, never()).releaseBatch(it.id)
        }
        verify(mockLogsReader, never()).dropBatch(any())
        verify(mockLogsReader, never()).releaseBatch(any())
        verify(mockTracesReader, never()).dropBatch(any())
        verify(mockTracesReader, never()).releaseBatch(any())
        assertThat(result)
            .isEqualTo(ListenableWorker.Result.success())
    }

    @Test
    fun `doWork multiple logs batches first Failed`(
        @Forgery batches: List<Batch>,
        forge: Forge
    ) {
        assumeTrue {
            // make sure there are no id duplicates
            batches.map { it.id }.toSet().size == batches.size
        }
        val status = forge.aValueFrom(
            UploadStatus::class.java,
            exclude = listOf(UploadStatus.SUCCESS)
        )
        val firstBatch = batches.first()
        val otherBatchesThenNull = Array(batches.size) {
            batches.getOrNull(it + 1)
        }
        whenever(mockLogsReader.readNextBatch()).doReturn(firstBatch, *otherBatchesThenNull)
        whenever(mockLogsUploader.upload(any())) doReturn UploadStatus.SUCCESS
        whenever(mockLogsUploader.upload(firstBatch.data)) doReturn status

        val result = testedWorker.doWork()

        batches.forEach {
            if (it == firstBatch) {
                verify(mockLogsReader, never()).dropBatch(it.id)
                verify(mockLogsReader).releaseBatch(it.id)
            } else {
                verify(mockLogsReader).dropBatch(it.id)
                verify(mockLogsReader, never()).releaseBatch(it.id)
            }
        }
        verify(mockTracesReader, never()).dropBatch(any())
        verify(mockTracesReader, never()).releaseBatch(any())
        verify(mockCrashReportsReader, never()).dropBatch(any())
        verify(mockCrashReportsReader, never()).releaseBatch(any())
        assertThat(result)
            .isEqualTo(ListenableWorker.Result.success())
    }

    @Test
    fun `doWork multiple traces batches first Failed`(
        @Forgery batches: List<Batch>,
        forge: Forge
    ) {
        assumeTrue {
            // make sure there are no id duplicates
            batches.map { it.id }.toSet().size == batches.size
        }
        val status = forge.aValueFrom(
            UploadStatus::class.java,
            exclude = listOf(UploadStatus.SUCCESS)
        )
        val firstBatch = batches.first()
        val otherBatchesThenNull = Array(batches.size) {
            batches.getOrNull(it + 1)
        }
        whenever(mockTracesReader.readNextBatch()).doReturn(firstBatch, *otherBatchesThenNull)
        whenever(mockTracesUploader.upload(any())) doReturn UploadStatus.SUCCESS
        whenever(mockTracesUploader.upload(firstBatch.data)) doReturn status

        val result = testedWorker.doWork()

        batches.forEach {
            if (it == firstBatch) {
                verify(mockTracesReader, never()).dropBatch(it.id)
                verify(mockTracesReader).releaseBatch(it.id)
            } else {
                verify(mockTracesReader).dropBatch(it.id)
                verify(mockTracesReader, never()).releaseBatch(it.id)
            }
        }
        verify(mockLogsReader, never()).dropBatch(any())
        verify(mockLogsReader, never()).releaseBatch(any())
        verify(mockCrashReportsReader, never()).dropBatch(any())
        verify(mockCrashReportsReader, never()).releaseBatch(any())
        assertThat(result)
            .isEqualTo(ListenableWorker.Result.success())
    }

    @Test
    fun `doWork multiple crashReports batches first Failed`(
        @Forgery batches: List<Batch>,
        forge: Forge
    ) {
        assumeTrue {
            // make sure there are no id duplicates
            batches.map { it.id }.toSet().size == batches.size
        }
        val status = forge.aValueFrom(
            UploadStatus::class.java,
            exclude = listOf(UploadStatus.SUCCESS)
        )
        val firstBatch = batches.first()
        val otherBatchesThenNull = Array(batches.size) {
            batches.getOrNull(it + 1)
        }
        whenever(mockCrashReportsReader.readNextBatch()).doReturn(firstBatch, *otherBatchesThenNull)
        whenever(mockCrashReportsUploader.upload(any())) doReturn UploadStatus.SUCCESS
        whenever(mockCrashReportsUploader.upload(firstBatch.data)) doReturn status

        val result = testedWorker.doWork()

        batches.forEach {
            if (it == firstBatch) {
                verify(mockCrashReportsReader, never()).dropBatch(it.id)
                verify(mockCrashReportsReader).releaseBatch(it.id)
            } else {
                verify(mockCrashReportsReader).dropBatch(it.id)
                verify(mockCrashReportsReader, never()).releaseBatch(it.id)
            }
        }
        verify(mockLogsReader, never()).dropBatch(any())
        verify(mockLogsReader, never()).releaseBatch(any())
        verify(mockTracesReader, never()).dropBatch(any())
        verify(mockTracesReader, never()).releaseBatch(any())
        assertThat(result)
            .isEqualTo(ListenableWorker.Result.success())
    }
}
