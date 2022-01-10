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
import com.datadog.android.core.configuration.Configuration
import com.datadog.android.core.configuration.Credentials
import com.datadog.android.core.internal.net.DataUploader
import com.datadog.android.core.internal.net.UploadStatus
import com.datadog.android.core.internal.persistence.Batch
import com.datadog.android.core.internal.persistence.DataReader
import com.datadog.android.core.internal.persistence.PersistenceStrategy
import com.datadog.android.error.internal.CrashReportsFeature
import com.datadog.android.log.internal.LogsFeature
import com.datadog.android.log.model.LogEvent
import com.datadog.android.privacy.TrackingConsent
import com.datadog.android.rum.internal.RumFeature
import com.datadog.android.tracing.internal.TracingFeature
import com.datadog.android.utils.config.ApplicationContextTestConfiguration
import com.datadog.android.utils.config.MainLooperTestConfiguration
import com.datadog.android.utils.extension.mockChoreographerInstance
import com.datadog.android.utils.forge.Configurator
import com.datadog.opentracing.DDSpan
import com.datadog.tools.unit.annotations.TestConfigurationsProvider
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import com.datadog.tools.unit.extensions.config.TestConfiguration
import com.datadog.tools.unit.invokeMethod
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
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
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
internal class UploadWorkerTest {

    lateinit var testedWorker: Worker

    @Mock
    lateinit var mockLogsStrategy: PersistenceStrategy<LogEvent>

    @Mock
    lateinit var mockTracesStrategy: PersistenceStrategy<DDSpan>

    @Mock
    lateinit var mockCrashReportsStrategy: PersistenceStrategy<LogEvent>

    @Mock
    lateinit var mockRumStrategy: PersistenceStrategy<Any>

    @Mock
    lateinit var mockLogsReader: DataReader

    @Mock
    lateinit var mockTracesReader: DataReader

    @Mock
    lateinit var mockCrashReader: DataReader

    @Mock
    lateinit var mockRumReader: DataReader

    @Mock
    lateinit var mockLogsUploader: DataUploader

    @Mock
    lateinit var mockTracesUploader: DataUploader

    @Mock
    lateinit var mockCrashUploader: DataUploader

    @Mock
    lateinit var mockRumUploader: DataUploader

    @Forgery
    lateinit var fakeWorkerParameters: WorkerParameters

    @BeforeEach
    fun `set up`() {
        whenever(mockLogsStrategy.getReader()) doReturn mockLogsReader
        whenever(mockTracesStrategy.getReader()) doReturn mockTracesReader
        whenever(mockCrashReportsStrategy.getReader()) doReturn mockCrashReader
        whenever(mockRumStrategy.getReader()) doReturn mockRumReader

        // Prevent crash when initializing RumFeature
        mockChoreographerInstance()

        Datadog.initialize(
            appContext.mockInstance,
            Credentials("CLIENT_TOKEN", "ENVIRONMENT", Credentials.NO_VARIANT, null),
            Configuration.Builder(
                logsEnabled = true,
                tracesEnabled = true,
                crashReportsEnabled = true,
                rumEnabled = true
            ).build(),
            TrackingConsent.GRANTED
        )

        LogsFeature.persistenceStrategy = mockLogsStrategy
        LogsFeature.uploader = mockLogsUploader
        TracingFeature.persistenceStrategy = mockTracesStrategy
        TracingFeature.uploader = mockTracesUploader
        CrashReportsFeature.persistenceStrategy = mockCrashReportsStrategy
        CrashReportsFeature.uploader = mockCrashUploader
        RumFeature.persistenceStrategy = mockRumStrategy
        RumFeature.uploader = mockRumUploader

        testedWorker = UploadWorker(
            appContext.mockInstance,
            fakeWorkerParameters
        )
    }

    @AfterEach
    fun `tear down`() {
        Datadog.invokeMethod("stop")
    }

    @Test
    fun `𝕄 send batches 𝕎 doWork() {single batch per feature}`(
        @Forgery logsBatch: Batch,
        @Forgery tracesBatch: Batch,
        @Forgery rumBatch: Batch,
        @Forgery crashReportsBatch: Batch
    ) {
        // Given
        whenever(mockLogsReader.lockAndReadNext()).doReturn(logsBatch, null)
        whenever(mockLogsUploader.upload(logsBatch.data)) doReturn UploadStatus.SUCCESS
        whenever(mockTracesReader.lockAndReadNext()).doReturn(tracesBatch, null)
        whenever(mockTracesUploader.upload(tracesBatch.data)) doReturn UploadStatus.SUCCESS
        whenever(mockRumReader.lockAndReadNext()).doReturn(rumBatch, null)
        whenever(mockRumUploader.upload(rumBatch.data)) doReturn UploadStatus.SUCCESS
        whenever(mockCrashReader.lockAndReadNext()).doReturn(crashReportsBatch, null)
        whenever(mockCrashUploader.upload(crashReportsBatch.data)).doReturn(UploadStatus.SUCCESS)

        // When
        val result = testedWorker.doWork()

        // Then
        verify(mockLogsReader).drop(logsBatch)
        verify(mockLogsReader, never()).release(logsBatch)
        verify(mockTracesReader).drop(tracesBatch)
        verify(mockTracesReader, never()).release(tracesBatch)
        verify(mockRumReader).drop(rumBatch)
        verify(mockRumReader, never()).release(rumBatch)
        verify(mockCrashReader).drop(crashReportsBatch)
        verify(mockCrashReader, never()).release(crashReportsBatch)
        assertThat(result)
            .isEqualTo(ListenableWorker.Result.success())
    }

    @ParameterizedTest
    @EnumSource(UploadStatus::class, names = ["SUCCESS"], mode = EnumSource.Mode.EXCLUDE)
    fun `𝕄 send and keep batches 𝕎 doWork() {single batch per feature with error}`(
        status: UploadStatus,
        @Forgery logsBatch: Batch,
        @Forgery tracesBatch: Batch,
        @Forgery rumBatch: Batch,
        @Forgery crashReportsBatch: Batch
    ) {
        whenever(mockLogsReader.lockAndReadNext()).doReturn(logsBatch, null)
        whenever(mockLogsUploader.upload(logsBatch.data)) doReturn status
        whenever(mockTracesReader.lockAndReadNext()).doReturn(tracesBatch, null)
        whenever(mockTracesUploader.upload(tracesBatch.data)) doReturn status
        whenever(mockRumReader.lockAndReadNext()).doReturn(rumBatch, null)
        whenever(mockRumUploader.upload(rumBatch.data)) doReturn status
        whenever(mockCrashReader.lockAndReadNext()).doReturn(crashReportsBatch, null)
        whenever(mockCrashUploader.upload(crashReportsBatch.data)) doReturn status

        val result = testedWorker.doWork()

        verify(mockLogsReader, never()).drop(logsBatch)
        verify(mockLogsReader).release(logsBatch)
        verify(mockTracesReader, never()).drop(tracesBatch)
        verify(mockTracesReader).release(tracesBatch)
        verify(mockRumReader, never()).drop(rumBatch)
        verify(mockRumReader).release(rumBatch)
        verify(mockCrashReader, never()).drop(crashReportsBatch)
        verify(mockCrashReader).release(crashReportsBatch)
        assertThat(result)
            .isEqualTo(ListenableWorker.Result.success())
    }

    @Test
    fun `𝕄 send batches 𝕎 doWork() {multiple Logs batches, all Success}`(forge: Forge) {
        val batches = forge.aBatchList()
        val firstBatch = batches.first()
        val otherBatchesThenNull = Array(batches.size) {
            batches.getOrNull(it + 1)
        }
        whenever(mockLogsReader.lockAndReadNext()).doReturn(firstBatch, *otherBatchesThenNull)
        batches.forEach {
            whenever(mockLogsUploader.upload(it.data)) doReturn UploadStatus.SUCCESS
        }

        val result = testedWorker.doWork()

        batches.forEach {
            verify(mockLogsReader).drop(it)
            verify(mockLogsReader, never()).release(it)
        }
        verify(mockTracesReader, never()).drop(any())
        verify(mockTracesReader, never()).release(any())
        verify(mockRumReader, never()).drop(any())
        verify(mockRumReader, never()).release(any())
        verify(mockCrashReader, never()).drop(any())
        verify(mockCrashReader, never()).release(any())
        assertThat(result)
            .isEqualTo(ListenableWorker.Result.success())
    }

    @Test
    fun `𝕄 send batches 𝕎 doWork() {multiple Trace batches, all Success}`(forge: Forge) {
        val batches = forge.aBatchList()
        val firstBatch = batches.first()
        val otherBatchesThenNull = Array(batches.size) {
            batches.getOrNull(it + 1)
        }
        whenever(mockTracesReader.lockAndReadNext()).doReturn(firstBatch, *otherBatchesThenNull)
        batches.forEach {
            whenever(mockTracesUploader.upload(it.data)) doReturn UploadStatus.SUCCESS
        }

        val result = testedWorker.doWork()

        batches.forEach {
            verify(mockTracesReader).drop(it)
            verify(mockTracesReader, never()).release(it)
        }
        verify(mockLogsReader, never()).drop(any())
        verify(mockLogsReader, never()).release(any())
        verify(mockRumReader, never()).drop(any())
        verify(mockRumReader, never()).release(any())
        verify(mockCrashReader, never()).drop(any())
        verify(mockCrashReader, never()).release(any())
        assertThat(result)
            .isEqualTo(ListenableWorker.Result.success())
    }

    @Test
    fun `𝕄 send batches 𝕎 doWork() {multiple RUM batches, all Success}`(forge: Forge) {
        val batches = forge.aBatchList()
        val firstBatch = batches.first()
        val otherBatchesThenNull = Array(batches.size) {
            batches.getOrNull(it + 1)
        }
        whenever(mockRumReader.lockAndReadNext()).doReturn(firstBatch, *otherBatchesThenNull)
        batches.forEach {
            whenever(mockRumUploader.upload(it.data)) doReturn UploadStatus.SUCCESS
        }

        val result = testedWorker.doWork()

        batches.forEach {
            verify(mockRumReader).drop(it)
            verify(mockRumReader, never()).release(it)
        }
        verify(mockLogsReader, never()).drop(any())
        verify(mockLogsReader, never()).release(any())
        verify(mockTracesReader, never()).drop(any())
        verify(mockTracesReader, never()).release(any())
        verify(mockCrashReader, never()).drop(any())
        verify(mockCrashReader, never()).release(any())
        assertThat(result)
            .isEqualTo(ListenableWorker.Result.success())
    }

    @Test
    fun `𝕄 send batches 𝕎 doWork() {multiple Crash batches, all Success}`(forge: Forge) {
        val batches = forge.aBatchList()
        val firstBatch = batches.first()
        val otherBatchesThenNull = Array(batches.size) {
            batches.getOrNull(it + 1)
        }
        whenever(mockCrashReader.lockAndReadNext())
            .doReturn(firstBatch, *otherBatchesThenNull)
        batches.forEach {
            whenever(mockCrashUploader.upload(it.data)) doReturn UploadStatus.SUCCESS
        }

        val result = testedWorker.doWork()

        batches.forEach {
            verify(mockCrashReader).drop(it)
            verify(mockCrashReader, never()).release(it)
        }
        verify(mockLogsReader, never()).drop(any())
        verify(mockLogsReader, never()).release(any())
        verify(mockTracesReader, never()).drop(any())
        verify(mockTracesReader, never()).release(any())
        verify(mockRumReader, never()).drop(any())
        verify(mockRumReader, never()).release(any())
        assertThat(result)
            .isEqualTo(ListenableWorker.Result.success())
    }

    @ParameterizedTest
    @EnumSource(UploadStatus::class, names = ["SUCCESS"], mode = EnumSource.Mode.EXCLUDE)
    fun `𝕄 send batches 𝕎 doWork() {multiple Log batches, first fails}`(
        status: UploadStatus,
        forge: Forge
    ) {
        val batches = forge.aBatchList()
        val firstBatch = batches.first()
        val otherBatchesThenNull = Array(batches.size) {
            batches.getOrNull(it + 1)
        }
        whenever(mockLogsReader.lockAndReadNext()).doReturn(firstBatch, *otherBatchesThenNull)
        whenever(mockLogsUploader.upload(any())) doReturn UploadStatus.SUCCESS
        whenever(mockLogsUploader.upload(firstBatch.data)) doReturn status

        val result = testedWorker.doWork()

        batches.forEach {
            if (it == firstBatch) {
                verify(mockLogsReader, never()).drop(it)
                verify(mockLogsReader).release(it)
            } else {
                verify(mockLogsReader).drop(it)
                verify(mockLogsReader, never()).release(it)
            }
        }
        verify(mockTracesReader, never()).drop(any())
        verify(mockTracesReader, never()).release(any())
        verify(mockRumReader, never()).drop(any())
        verify(mockRumReader, never()).release(any())
        verify(mockCrashReader, never()).drop(any())
        verify(mockCrashReader, never()).release(any())
        assertThat(result)
            .isEqualTo(ListenableWorker.Result.success())
    }

    @ParameterizedTest
    @EnumSource(UploadStatus::class, names = ["SUCCESS"], mode = EnumSource.Mode.EXCLUDE)
    fun `𝕄 send batches 𝕎 doWork() {multiple Trace batches, first fails}`(
        status: UploadStatus,
        forge: Forge
    ) {
        val batches = forge.aBatchList()
        val firstBatch = batches.first()
        val otherBatchesThenNull = Array(batches.size) {
            batches.getOrNull(it + 1)
        }
        whenever(mockTracesReader.lockAndReadNext()).doReturn(firstBatch, *otherBatchesThenNull)
        whenever(mockTracesUploader.upload(any())) doReturn UploadStatus.SUCCESS
        whenever(mockTracesUploader.upload(firstBatch.data)) doReturn status

        val result = testedWorker.doWork()

        batches.forEach {
            if (it == firstBatch) {
                verify(mockTracesReader, never()).drop(it)
                verify(mockTracesReader).release(it)
            } else {
                verify(mockTracesReader).drop(it)
                verify(mockTracesReader, never()).release(it)
            }
        }
        verify(mockLogsReader, never()).drop(any())
        verify(mockLogsReader, never()).release(any())
        verify(mockRumReader, never()).drop(any())
        verify(mockRumReader, never()).release(any())
        verify(mockCrashReader, never()).drop(any())
        verify(mockCrashReader, never()).release(any())
        assertThat(result)
            .isEqualTo(ListenableWorker.Result.success())
    }

    @ParameterizedTest
    @EnumSource(UploadStatus::class, names = ["SUCCESS"], mode = EnumSource.Mode.EXCLUDE)
    fun `𝕄 send batches 𝕎 doWork() {multiple Rum batches, first fails}`(
        status: UploadStatus,
        forge: Forge
    ) {
        val batches = forge.aBatchList()
        val firstBatch = batches.first()
        val otherBatchesThenNull = Array(batches.size) {
            batches.getOrNull(it + 1)
        }
        whenever(mockRumReader.lockAndReadNext()).doReturn(firstBatch, *otherBatchesThenNull)
        whenever(mockRumUploader.upload(any())) doReturn UploadStatus.SUCCESS
        whenever(mockRumUploader.upload(firstBatch.data)) doReturn status

        val result = testedWorker.doWork()

        batches.forEach {
            if (it == firstBatch) {
                verify(mockRumReader, never()).drop(it)
                verify(mockRumReader).release(it)
            } else {
                verify(mockRumReader).drop(it)
                verify(mockRumReader, never()).release(it)
            }
        }
        verify(mockLogsReader, never()).drop(any())
        verify(mockLogsReader, never()).release(any())
        verify(mockTracesReader, never()).drop(any())
        verify(mockTracesReader, never()).release(any())
        verify(mockCrashReader, never()).drop(any())
        verify(mockCrashReader, never()).release(any())
        assertThat(result)
            .isEqualTo(ListenableWorker.Result.success())
    }

    @ParameterizedTest
    @EnumSource(UploadStatus::class, names = ["SUCCESS"], mode = EnumSource.Mode.EXCLUDE)
    fun `𝕄 send batches 𝕎 doWork() {multiple Crash batches, first fails}`(
        status: UploadStatus,
        forge: Forge
    ) {
        val batches = forge.aBatchList()
        val firstBatch = batches.first()
        val otherBatchesThenNull = Array(batches.size) {
            batches.getOrNull(it + 1)
        }
        whenever(mockCrashReader.lockAndReadNext())
            .doReturn(firstBatch, *otherBatchesThenNull)
        whenever(mockCrashUploader.upload(any())) doReturn UploadStatus.SUCCESS
        whenever(mockCrashUploader.upload(firstBatch.data)) doReturn status

        val result = testedWorker.doWork()

        batches.forEach {
            if (it == firstBatch) {
                verify(mockCrashReader, never()).drop(it)
                verify(mockCrashReader).release(it)
            } else {
                verify(mockCrashReader).drop(it)
                verify(mockCrashReader, never()).release(it)
            }
        }
        verify(mockLogsReader, never()).drop(any())
        verify(mockLogsReader, never()).release(any())
        verify(mockTracesReader, never()).drop(any())
        verify(mockTracesReader, never()).release(any())
        verify(mockRumReader, never()).drop(any())
        verify(mockRumReader, never()).release(any())
        assertThat(result)
            .isEqualTo(ListenableWorker.Result.success())
    }

    private fun Forge.aBatchList(): List<Batch> {
        val list = mutableListOf<Batch>()
        val ids = mutableListOf<String>()
        for (i in 0..aTinyInt()) {
            val batch: Batch = getForgery()
            if (batch.id !in ids) {
                list.add(batch)
                ids.add(batch.id)
            }
        }
        return list
    }

    companion object {
        val appContext = ApplicationContextTestConfiguration(Context::class.java)
        val mainLooper = MainLooperTestConfiguration()

        @TestConfigurationsProvider
        @JvmStatic
        fun getTestConfigurations(): List<TestConfiguration> {
            return listOf(appContext, mainLooper)
        }
    }
}
