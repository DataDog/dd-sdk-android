/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.persistence.file.batch

import android.os.FileObserver
import com.datadog.android.api.InternalLogger
import com.datadog.android.core.internal.metrics.BatchClosedMetadata
import com.datadog.android.core.internal.metrics.MetricsDispatcher
import com.datadog.android.core.internal.metrics.RemovalReason
import com.datadog.android.core.internal.persistence.file.FilePersistenceConfig
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.utils.verifyLog
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.annotation.LongForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.junit.jupiter.api.io.TempDir
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.argThat
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.io.File
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@ForgeConfiguration(Configurator::class)
@MockitoSettings(strictness = Strictness.LENIENT)
internal class BatchFileOrchestratorTest {

    private lateinit var testedOrchestrator: BatchFileOrchestrator

    @TempDir
    lateinit var tempDir: File

    @Mock
    lateinit var mockLogger: InternalLogger

    @StringForgery
    lateinit var fakeRootDirName: String

    lateinit var fakeRootDir: File

    @Mock
    lateinit var mockMetricsDispatcher: MetricsDispatcher

    @IntForgery(min = 0, max = 100)
    var fakePendingBatches: Int = 0

    @Mock
    lateinit var mockPendingFiles: AtomicInteger

    @BeforeEach
    fun `set up`() {
        whenever(mockPendingFiles.decrementAndGet()).thenReturn(fakePendingBatches)
        whenever(mockPendingFiles.incrementAndGet()).thenReturn(fakePendingBatches)
        fakeRootDir = File(tempDir, fakeRootDirName)
        fakeRootDir.mkdirs()
        testedOrchestrator = BatchFileOrchestrator(
            rootDir = fakeRootDir,
            config = TEST_PERSISTENCE_CONFIG,
            internalLogger = mockLogger,
            metricsDispatcher = mockMetricsDispatcher,
            pendingFiles = mockPendingFiles
        )
    }

    // region getWritableFile

    @Test
    fun `M not send batch_closed metric W getWritableFile() {no prev file}`() {
        // Given
        assumeTrue(fakeRootDir.listFiles().isNullOrEmpty())

        // When
        val result = testedOrchestrator.getWritableFile()

        // Then
        checkNotNull(result)
        verifyNoInteractions(mockMetricsDispatcher)
    }

    @Test
    fun `M send batch_closed metric W getWritableFile()`() {
        // Given
        val lowerTimestamp = System.currentTimeMillis()
        val oldFile = testedOrchestrator.getWritableFile()
        val upperTimestamp = System.currentTimeMillis()
        Thread.sleep(RECENT_DELAY_MS + 1)

        // When
        testedOrchestrator.getWritableFile()

        // Then
        val fileArgumentCaptor = argumentCaptor<File>()
        val metadataArgumentCaptor = argumentCaptor<BatchClosedMetadata>()
        verify(mockMetricsDispatcher).sendBatchClosedMetric(
            fileArgumentCaptor.capture(),
            metadataArgumentCaptor.capture()
        )

        assertThat(fileArgumentCaptor.firstValue).isEqualTo(oldFile)
        metadataArgumentCaptor.firstValue.let {
            assertThat(it.eventsCount).isEqualTo(1L)
            assertThat(it.lastTimeWasUsedInMs)
                .isBetween(lowerTimestamp, upperTimestamp)
        }
        verifyNoMoreInteractions(mockMetricsDispatcher)
    }

    @Test
    fun `M warn W getWritableFile() {root is not a dir}`(
        @StringForgery fileName: String
    ) {
        // Given
        val notADir = File(fakeRootDir, fileName)
        notADir.createNewFile()
        testedOrchestrator = BatchFileOrchestrator(
            notADir,
            TEST_PERSISTENCE_CONFIG,
            mockLogger,
            mockMetricsDispatcher
        )

        // When
        val result = testedOrchestrator.getWritableFile()

        // Then
        assertThat(result).isNull()
        mockLogger.verifyLog(
            InternalLogger.Level.ERROR,
            listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
            BatchFileOrchestrator.ERROR_ROOT_NOT_DIR.format(Locale.US, notADir.path)
        )
        verifyNoInteractions(mockMetricsDispatcher)
    }

    @Test
    fun `M warn W getWritableFile() {root can't be created}`() {
        // Given
        val corruptedDir = mock<File>()
        whenever(corruptedDir.exists()).thenReturn(false)
        whenever(corruptedDir.mkdirs()).thenReturn(false)
        whenever(corruptedDir.path) doReturn fakeRootDir.path
        testedOrchestrator = BatchFileOrchestrator(
            corruptedDir,
            TEST_PERSISTENCE_CONFIG,
            mockLogger,
            mockMetricsDispatcher
        )

        // When
        val result = testedOrchestrator.getWritableFile()

        // Then
        assertThat(result).isNull()
        mockLogger.verifyLog(
            InternalLogger.Level.ERROR,
            listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
            BatchFileOrchestrator.ERROR_CANT_CREATE_ROOT.format(Locale.US, fakeRootDir.path)
        )
        verifyNoInteractions(mockMetricsDispatcher)
    }

    @Test
    fun `M warn W getWritableFile() {root is not writeable}`() {
        // Given
        val restrictedDir = mock<File>()
        whenever(restrictedDir.exists()).thenReturn(true)
        whenever(restrictedDir.isDirectory).thenReturn(true)
        whenever(restrictedDir.canWrite()).thenReturn(false)
        whenever(restrictedDir.path) doReturn fakeRootDir.path
        testedOrchestrator = BatchFileOrchestrator(
            restrictedDir,
            TEST_PERSISTENCE_CONFIG,
            mockLogger,
            mockMetricsDispatcher
        )

        // When
        val result = testedOrchestrator.getWritableFile()

        // Then
        assertThat(result).isNull()
        mockLogger.verifyLog(
            InternalLogger.Level.ERROR,
            listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
            BatchFileOrchestrator.ERROR_ROOT_NOT_WRITABLE.format(Locale.US, fakeRootDir.path)
        )
        verifyNoInteractions(mockMetricsDispatcher)
    }

    @Test
    fun `M create the rootDirectory W getWritableFile() {root does not exist}`() {
        // Given
        fakeRootDir.deleteRecursively()

        // When
        testedOrchestrator.getWritableFile()

        // Then
        assertThat(fakeRootDir).exists().isDirectory()
        verifyNoInteractions(mockMetricsDispatcher)
    }

    @Test
    fun `M delete obsolete files W getWritableFile()`(
        @LongForgery(min = OLD_FILE_THRESHOLD, max = Int.MAX_VALUE.toLong()) oldFileAge: Long
    ) {
        // Given
        assumeTrue(fakeRootDir.listFiles().isNullOrEmpty())
        val oldTimestamp = System.currentTimeMillis() - oldFileAge
        val oldFile = File(fakeRootDir, oldTimestamp.toString())
        oldFile.createNewFile()
        testedOrchestrator.fileObserver.onEvent(FileObserver.CREATE, oldFile.name)
        val oldFileMeta = File("${oldFile.path}_metadata")
        oldFileMeta.createNewFile()
        testedOrchestrator.fileObserver.onEvent(FileObserver.CREATE, oldFileMeta.name)
        val youngTimestamp = System.currentTimeMillis() - RECENT_DELAY_MS - 1
        val youngFile = File(fakeRootDir, youngTimestamp.toString())
        youngFile.createNewFile()
        testedOrchestrator.fileObserver.onEvent(FileObserver.CREATE, youngFile.name)

        // When
        val start = System.currentTimeMillis()
        val result = testedOrchestrator.getWritableFile()
        val end = System.currentTimeMillis()

        // Then
        checkNotNull(result)
        assertThat(result)
            .doesNotExist()
            .hasParent(fakeRootDir)
        assertThat(result.name.toLong())
            .isBetween(start, end)
        assertThat(oldFile).doesNotExist()
        assertThat(oldFileMeta).doesNotExist()
        assertThat(youngFile).exists()
        verify(mockMetricsDispatcher).sendBatchDeletedMetric(
            eq(oldFile),
            argThat { this is RemovalReason.Obsolete },
            eq(fakePendingBatches)
        )
        verifyNoMoreInteractions(mockMetricsDispatcher)
    }

    @Test
    fun `M respect time threshold to delete obsolete files W getWritableFile() { below threshold }`(
        @LongForgery(min = OLD_FILE_THRESHOLD, max = Int.MAX_VALUE.toLong()) oldFileAge: Long
    ) {
        // Given
        assumeTrue(fakeRootDir.listFiles().isNullOrEmpty())
        val oldTimestamp = System.currentTimeMillis() - oldFileAge
        val oldFile = File(fakeRootDir, oldTimestamp.toString())
        oldFile.createNewFile()
        testedOrchestrator.fileObserver.onEvent(FileObserver.CREATE, oldFile.name)
        val oldFileMeta = File("${oldFile.path}_metadata")
        oldFileMeta.createNewFile()
        testedOrchestrator.fileObserver.onEvent(FileObserver.CREATE, oldFileMeta.name)
        val youngTimestamp = System.currentTimeMillis() - RECENT_DELAY_MS - 1
        val youngFile = File(fakeRootDir, youngTimestamp.toString())
        youngFile.createNewFile()
        testedOrchestrator.fileObserver.onEvent(FileObserver.CREATE, youngFile.name)

        // When
        val start = System.currentTimeMillis()
        val result = testedOrchestrator.getWritableFile()
        val end = System.currentTimeMillis()
        // let's add very old file after the previous cleanup call. If threshold is respected,
        // cleanup shouldn't be performed during the next getWritableFile call
        val evenOlderFile = File(fakeRootDir, (oldTimestamp - 1).toString())
        evenOlderFile.createNewFile()
        testedOrchestrator.fileObserver.onEvent(FileObserver.CREATE, evenOlderFile.name)
        testedOrchestrator.getWritableFile()

        // Then
        checkNotNull(result)
        assertThat(result)
            .doesNotExist()
            .hasParent(fakeRootDir)
        assertThat(result.name.toLong())
            .isBetween(start, end)
        assertThat(oldFile).doesNotExist()
        assertThat(oldFileMeta).doesNotExist()
        assertThat(youngFile).exists()
        assertThat(evenOlderFile).exists()
        verify(mockMetricsDispatcher).sendBatchDeletedMetric(
            eq(oldFile),
            argThat { this is RemovalReason.Obsolete },
            eq(fakePendingBatches)
        )
    }

    @Test
    fun `M respect time threshold to delete obsolete files W getWritableFile() { above threshold }`(
        @LongForgery(min = OLD_FILE_THRESHOLD, max = Int.MAX_VALUE.toLong()) oldFileAge: Long
    ) {
        // Given
        assumeTrue(fakeRootDir.listFiles().isNullOrEmpty())
        val oldTimestamp = System.currentTimeMillis() - oldFileAge
        val oldFile = File(fakeRootDir, oldTimestamp.toString())
        oldFile.createNewFile()
        testedOrchestrator.fileObserver.onEvent(FileObserver.CREATE, oldFile.name)
        val oldFileMeta = File("${oldFile.path}_metadata")
        oldFileMeta.createNewFile()
        testedOrchestrator.fileObserver.onEvent(FileObserver.CREATE, oldFileMeta.name)

        // When
        val start = System.currentTimeMillis()
        val result = testedOrchestrator.getWritableFile()
        val end = System.currentTimeMillis()
        Thread.sleep(maxOf(CLEANUP_FREQUENCY_THRESHOLD_MS, RECENT_DELAY_MS) + 1)
        val evenOlderFile = File(fakeRootDir, (oldTimestamp - 1).toString())
        evenOlderFile.createNewFile()
        testedOrchestrator.fileObserver.onEvent(FileObserver.CREATE, evenOlderFile.name)
        testedOrchestrator.getWritableFile()

        // Then
        checkNotNull(result)
        assertThat(result)
            .doesNotExist()
            .hasParent(fakeRootDir)
        assertThat(result.name.toLong())
            .isBetween(start, end)
        assertThat(oldFile).doesNotExist()
        assertThat(oldFileMeta).doesNotExist()
        assertThat(evenOlderFile).doesNotExist()
        verify(mockMetricsDispatcher).sendBatchDeletedMetric(
            eq(evenOlderFile),
            argThat { this is RemovalReason.Obsolete },
            eq(fakePendingBatches)
        )
        verify(mockMetricsDispatcher).sendBatchDeletedMetric(
            eq(oldFile),
            argThat { this is RemovalReason.Obsolete },
            eq(fakePendingBatches)
        )
        argumentCaptor<BatchClosedMetadata> {
            verify(mockMetricsDispatcher).sendBatchClosedMetric(
                eq(result),
                capture()
            )
            assertThat(firstValue.eventsCount).isEqualTo(1L)
            assertThat(firstValue.lastTimeWasUsedInMs)
                .isBetween(start, end)
        }
        verifyNoMoreInteractions(mockMetricsDispatcher)
    }

    @Test
    fun `M return new File W getWritableFile() {no available file}`() {
        // Given
        assumeTrue(fakeRootDir.listFiles().isNullOrEmpty())

        // When
        val start = System.currentTimeMillis()
        val result = testedOrchestrator.getWritableFile()
        val end = System.currentTimeMillis()

        // Then
        checkNotNull(result)
        assertThat(result)
            .doesNotExist()
            .hasParent(fakeRootDir)
        assertThat(result.name.toLong())
            .isBetween(start, end)
        verifyNoInteractions(mockMetricsDispatcher)
    }

    @Test
    fun `M return existing File W getWritableFile() {recent file exist with spare space}`(
        @StringForgery(size = SMALL_ITEM_SIZE) previousData: String
    ) {
        // Given
        assumeTrue(fakeRootDir.listFiles().isNullOrEmpty())
        val previousFile = testedOrchestrator.getWritableFile()
        checkNotNull(previousFile)
        testedOrchestrator.fileObserver.onEvent(FileObserver.CREATE, previousFile.name)
        previousFile.writeText(previousData)
        Thread.sleep(1)

        // When
        val result = testedOrchestrator.getWritableFile()

        // Then
        checkNotNull(result)
        assertThat(result).isEqualTo(previousFile)
        assertThat(previousFile.readText()).isEqualTo(previousData)
        verifyNoInteractions(mockMetricsDispatcher)
    }

    @Test
    fun `M return new File W getWritableFile() {previous file is too old}`(
        @StringForgery(size = SMALL_ITEM_SIZE) previousData: String
    ) {
        // Given
        assumeTrue(fakeRootDir.listFiles().isNullOrEmpty())
        val beforeFileCreateTimestamp = System.currentTimeMillis()
        val previousFile = testedOrchestrator.getWritableFile()
        val afterFileCreateTimestamp = System.currentTimeMillis()

        checkNotNull(previousFile)
        previousFile.writeText(previousData)
        Thread.sleep(RECENT_DELAY_MS + 1)

        // When
        val start = System.currentTimeMillis()
        val result = testedOrchestrator.getWritableFile()
        val end = System.currentTimeMillis()

        // Then
        checkNotNull(result)
        assertThat(result)
            .doesNotExist()
            .hasParent(fakeRootDir)
        assertThat(result.name.toLong())
            .isBetween(start, end)
        assertThat(previousFile.readText()).isEqualTo(previousData)
        argumentCaptor<BatchClosedMetadata> {
            verify(mockMetricsDispatcher).sendBatchClosedMetric(eq(previousFile), capture())
            assertThat(firstValue.lastTimeWasUsedInMs)
                .isBetween(beforeFileCreateTimestamp, afterFileCreateTimestamp)
            assertThat(firstValue.eventsCount).isEqualTo(1L)
        }
        verifyNoMoreInteractions(mockMetricsDispatcher)
    }

    @Test
    fun `M return new File W getWritableFile() {previous file is unknown}`(
        @StringForgery(size = SMALL_ITEM_SIZE) previousData: String
    ) {
        // Given
        assumeTrue(fakeRootDir.listFiles().isNullOrEmpty())
        val previousFile = File(fakeRootDir, System.currentTimeMillis().toString())
        previousFile.writeText(previousData)
        Thread.sleep(1)

        // When
        val start = System.currentTimeMillis()
        val result = testedOrchestrator.getWritableFile()
        val end = System.currentTimeMillis()

        // Then
        checkNotNull(result)
        assertThat(result)
            .doesNotExist()
            .hasParent(fakeRootDir)
        assertThat(result.name.toLong())
            .isBetween(start, end)
        assertThat(previousFile.readText()).isEqualTo(previousData)
        verifyNoInteractions(mockMetricsDispatcher)
    }

    @Test
    fun `M return new File W getWritableFile() {previous file is deleted}`() {
        // Given
        assumeTrue(fakeRootDir.listFiles().isNullOrEmpty())
        val beforeFileCreateTimestamp = System.currentTimeMillis()
        val previousFile = testedOrchestrator.getWritableFile()
        val afterFileCreateTimestamp = System.currentTimeMillis()
        checkNotNull(previousFile)
        previousFile.createNewFile()
        previousFile.delete()
        testedOrchestrator.fileObserver.onEvent(FileObserver.DELETE, previousFile.name)
        Thread.sleep(1)

        // When
        val start = System.currentTimeMillis()
        val result = testedOrchestrator.getWritableFile()
        val end = System.currentTimeMillis()

        // Then
        checkNotNull(result)
        assertThat(result)
            .doesNotExist()
            .hasParent(fakeRootDir)
        assertThat(result.name.toLong())
            .isBetween(start, end)
        assertThat(previousFile).doesNotExist()
        argumentCaptor<BatchClosedMetadata> {
            verify(mockMetricsDispatcher).sendBatchClosedMetric(eq(previousFile), capture())
            assertThat(firstValue.lastTimeWasUsedInMs)
                .isBetween(beforeFileCreateTimestamp, afterFileCreateTimestamp)
            assertThat(firstValue.eventsCount).isEqualTo(1L)
        }
        verifyNoMoreInteractions(mockMetricsDispatcher)
    }

    @Test
    fun `M return new File W getWritableFile() {previous file is too large}`(
        @StringForgery(size = MAX_BATCH_SIZE) previousData: String
    ) {
        // Given
        assumeTrue(fakeRootDir.listFiles().isNullOrEmpty())
        val beforeFileCreateTimestamp = System.currentTimeMillis()
        val previousFile = testedOrchestrator.getWritableFile()
        val afterFileCreateTimestamp = System.currentTimeMillis()
        checkNotNull(previousFile)
        previousFile.writeText(previousData)
        Thread.sleep(1)

        // When
        val start = System.currentTimeMillis()
        val result = testedOrchestrator.getWritableFile()
        val end = System.currentTimeMillis()

        // Then
        checkNotNull(result)
        assertThat(result)
            .doesNotExist()
            .hasParent(fakeRootDir)
        assertThat(result.name.toLong())
            .isBetween(start, end)
        assertThat(previousFile.readText()).isEqualTo(previousData)
        argumentCaptor<BatchClosedMetadata> {
            verify(mockMetricsDispatcher).sendBatchClosedMetric(eq(previousFile), capture())
            assertThat(firstValue.lastTimeWasUsedInMs)
                .isBetween(beforeFileCreateTimestamp, afterFileCreateTimestamp)
            assertThat(firstValue.eventsCount).isEqualTo(1L)
        }
        verifyNoMoreInteractions(mockMetricsDispatcher)
    }

    @Test
    fun `M return new File W getWritableFile() {previous file has too many items}`(
        forge: Forge
    ) {
        // Given
        assumeTrue(fakeRootDir.listFiles().isNullOrEmpty())
        val beforeFileCreateTimestamp = System.currentTimeMillis()
        var previousFile = testedOrchestrator.getWritableFile()

        repeat(4) {
            val currentFile = checkNotNull(previousFile)

            val previousData = forge.aList(MAX_ITEM_PER_BATCH) {
                forge.anAlphabeticalString()
            }

            currentFile.writeText(previousData[0])

            for (i in 1 until MAX_ITEM_PER_BATCH) {
                val file = testedOrchestrator.getWritableFile()
                assumeTrue(file == previousFile)
                file?.appendText(previousData[i])
            }
            val afterLastFileUsageTimestamp = System.currentTimeMillis()

            // When
            val start = System.currentTimeMillis()
            val nextFile = testedOrchestrator.getWritableFile()
            val end = System.currentTimeMillis()

            // Then
            checkNotNull(nextFile)
            assertThat(nextFile)
                .doesNotExist()
                .hasParent(fakeRootDir)
            assertThat(nextFile.name.toLong())
                .isBetween(start, end)
            assertThat(currentFile.readText())
                .isEqualTo(previousData.joinToString(separator = ""))

            argumentCaptor<BatchClosedMetadata> {
                verify(mockMetricsDispatcher).sendBatchClosedMetric(eq(currentFile), capture())
                assertThat(firstValue.lastTimeWasUsedInMs)
                    .isBetween(beforeFileCreateTimestamp, afterLastFileUsageTimestamp)
                assertThat(firstValue.eventsCount).isEqualTo(MAX_ITEM_PER_BATCH.toLong())
            }
            previousFile = nextFile
        }
        verifyNoMoreInteractions(mockMetricsDispatcher)
    }

    @Test
    fun `M discard File W getWritableFile() {previous files take too much disk space}`(
        @StringForgery(size = MAX_BATCH_SIZE) previousData: String
    ) {
        // Given
        assumeTrue(fakeRootDir.listFiles().isNullOrEmpty())
        val filesCount = MAX_DISK_SPACE / MAX_BATCH_SIZE
        val files = mutableListOf<File>()
        repeat(filesCount + 1) {
            val file = testedOrchestrator.getWritableFile()
            checkNotNull(file)
            if (files.none { it.name == file.name }) {
                testedOrchestrator.fileObserver.onEvent(FileObserver.CREATE, file.name)
            }
            file.writeText(previousData)
            files.add(file)
            Thread.sleep(1)
        }

        // When
        Thread.sleep(CLEANUP_FREQUENCY_THRESHOLD_MS + 1)
        val start = System.currentTimeMillis()
        val result = testedOrchestrator.getWritableFile()
        val end = System.currentTimeMillis()

        // Then
        checkNotNull(result)
        assertThat(result)
            .doesNotExist()
            .hasParent(fakeRootDir)
        assertThat(result.name.toLong())
            .isBetween(start, end)
        assertThat(files.first()).doesNotExist()
        mockLogger.verifyLog(
            InternalLogger.Level.ERROR,
            listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
            BatchFileOrchestrator.ERROR_DISK_FULL.format(
                Locale.US,
                files.size * previousData.length,
                MAX_DISK_SPACE,
                (files.size * previousData.length) - MAX_DISK_SPACE
            )
        )
    }

    // endregion

    // region getReadableFile

    @Test
    fun `M warn W getReadableFile() {root is not a dir}`(
        @StringForgery fileName: String
    ) {
        // Given
        val notADir = File(fakeRootDir, fileName)
        notADir.createNewFile()
        testedOrchestrator = BatchFileOrchestrator(
            notADir,
            TEST_PERSISTENCE_CONFIG,
            mockLogger,
            mockMetricsDispatcher
        )

        // When
        val result = testedOrchestrator.getReadableFile(emptySet())

        // Then
        assertThat(result).isNull()
        mockLogger.verifyLog(
            InternalLogger.Level.ERROR,
            listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
            BatchFileOrchestrator.ERROR_ROOT_NOT_DIR.format(Locale.US, notADir.path)
        )
    }

    @Test
    fun `M warn W getReadableFile() {root can't be created}`() {
        // Given
        val corruptedDir = mock<File>()
        whenever(corruptedDir.exists()).thenReturn(false)
        whenever(corruptedDir.mkdirs()).thenReturn(false)
        whenever(corruptedDir.path) doReturn fakeRootDir.path
        testedOrchestrator = BatchFileOrchestrator(
            corruptedDir,
            TEST_PERSISTENCE_CONFIG,
            mockLogger,
            mockMetricsDispatcher
        )

        // When
        val result = testedOrchestrator.getReadableFile(emptySet())

        // Then
        assertThat(result).isNull()
        mockLogger.verifyLog(
            InternalLogger.Level.ERROR,
            listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
            BatchFileOrchestrator.ERROR_CANT_CREATE_ROOT.format(Locale.US, fakeRootDir.path)
        )
    }

    @Test
    fun `M warn W getReadableFile() {root is not writeable}`() {
        // Given
        val restrictedDir = mock<File>()
        whenever(restrictedDir.exists()).thenReturn(true)
        whenever(restrictedDir.isDirectory).thenReturn(true)
        whenever(restrictedDir.canWrite()).thenReturn(false)
        whenever(restrictedDir.path) doReturn fakeRootDir.path
        testedOrchestrator = BatchFileOrchestrator(
            restrictedDir,
            TEST_PERSISTENCE_CONFIG,
            mockLogger,
            mockMetricsDispatcher
        )

        // When
        val result = testedOrchestrator.getReadableFile(emptySet())

        // Then
        assertThat(result).isNull()
        mockLogger.verifyLog(
            InternalLogger.Level.ERROR,
            listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
            BatchFileOrchestrator.ERROR_ROOT_NOT_WRITABLE.format(Locale.US, fakeRootDir.path)
        )
    }

    @Test
    fun `M delete obsolete files W getReadableFile()`(
        @LongForgery(min = OLD_FILE_THRESHOLD, max = Int.MAX_VALUE.toLong()) oldFileAge: Long
    ) {
        // Given
        assumeTrue(fakeRootDir.listFiles().isNullOrEmpty())
        val oldTimestamp = System.currentTimeMillis() - oldFileAge
        val oldFile = File(fakeRootDir, oldTimestamp.toString())
        oldFile.createNewFile()
        testedOrchestrator.fileObserver.onEvent(FileObserver.CREATE, oldFile.name)
        val oldFileMeta = File("${oldFile.path}_metadata")
        oldFileMeta.createNewFile()
        testedOrchestrator.fileObserver.onEvent(FileObserver.CREATE, oldFileMeta.name)
        val youngTimestamp = System.currentTimeMillis() - RECENT_DELAY_MS - 1
        val youngFile = File(fakeRootDir, youngTimestamp.toString())
        youngFile.createNewFile()
        testedOrchestrator.fileObserver.onEvent(FileObserver.CREATE, youngFile.name)

        // When
        val result = testedOrchestrator.getReadableFile(emptySet())

        // Then
        assertThat(result).isNull()
        assertThat(oldFile).doesNotExist()
        assertThat(oldFileMeta).doesNotExist()
        assertThat(youngFile).exists()
    }

    @Test
    fun `M create the rootDirectory W getReadableFile() {root does not exist}`() {
        // Given
        fakeRootDir.deleteRecursively()

        // When
        testedOrchestrator.getReadableFile(emptySet())

        // Then
        assertThat(fakeRootDir).exists().isDirectory()
    }

    @Test
    fun `M return null W getReadableFile() {empty dir}`() {
        // Given
        assumeTrue(fakeRootDir.listFiles().isNullOrEmpty())

        // When
        val result = testedOrchestrator.getReadableFile(emptySet())

        // Then
        assertThat(result).isNull()
    }

    @Test
    fun `M return file W getReadableFile() {existing old enough file}`() {
        // Given
        assumeTrue(fakeRootDir.listFiles().isNullOrEmpty())
        val timestamp = System.currentTimeMillis() - (RECENT_DELAY_MS * 2)
        val file = File(fakeRootDir, timestamp.toString())
        file.createNewFile()
        testedOrchestrator.fileObserver.onEvent(FileObserver.CREATE, file.name)

        // When
        val result = testedOrchestrator.getReadableFile(emptySet())

        // Then
        assertThat(result)
            .isEqualTo(file)
            .exists()
            .hasContent("")
    }

    @Test
    fun `M return null W getReadableFile() {file is too recent}`() {
        // Given
        assumeTrue(fakeRootDir.listFiles().isNullOrEmpty())
        val timestamp = System.currentTimeMillis() - (RECENT_DELAY_MS / 2)
        val file = File(fakeRootDir, timestamp.toString())
        file.createNewFile()

        // When
        val result = testedOrchestrator.getReadableFile(emptySet())

        // Then
        assertThat(result).isNull()
    }

    @Test
    fun `M return null W getReadableFile() {file is in exclude list}`() {
        // Given
        assumeTrue(fakeRootDir.listFiles().isNullOrEmpty())
        val timestamp = System.currentTimeMillis() - (RECENT_DELAY_MS * 2)
        val file = File(fakeRootDir, timestamp.toString())
        file.createNewFile()

        // When
        val result = testedOrchestrator.getReadableFile(setOf(file))

        // Then
        assertThat(result).isNull()
    }

    // endregion

    // region getAllFiles

    @Test
    fun `M warn W getAllFiles() {root is not a dir}`(
        @StringForgery fileName: String
    ) {
        // Given
        val notADir = File(fakeRootDir, fileName)
        notADir.createNewFile()
        testedOrchestrator = BatchFileOrchestrator(
            notADir,
            TEST_PERSISTENCE_CONFIG,
            mockLogger,
            mockMetricsDispatcher
        )

        // When
        val result = testedOrchestrator.getAllFiles()

        // Then
        assertThat(result).isEmpty()
        mockLogger.verifyLog(
            InternalLogger.Level.ERROR,
            listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
            BatchFileOrchestrator.ERROR_ROOT_NOT_DIR.format(Locale.US, notADir.path)
        )
    }

    @Test
    fun `M warn W getAllFiles() {root can't be created}`() {
        // Given
        val corruptedDir = mock<File>()
        whenever(corruptedDir.exists()).thenReturn(false)
        whenever(corruptedDir.mkdirs()).thenReturn(false)
        whenever(corruptedDir.path) doReturn fakeRootDir.path
        testedOrchestrator = BatchFileOrchestrator(
            corruptedDir,
            TEST_PERSISTENCE_CONFIG,
            mockLogger,
            mockMetricsDispatcher
        )

        // When
        val result = testedOrchestrator.getAllFiles()

        // Then
        assertThat(result).isEmpty()
        mockLogger.verifyLog(
            InternalLogger.Level.ERROR,
            listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
            BatchFileOrchestrator.ERROR_CANT_CREATE_ROOT.format(Locale.US, fakeRootDir.path)
        )
    }

    @Test
    fun `M warn W getAllFiles() {root is not writeable}`() {
        // Given
        val restrictedDir = mock<File>()
        whenever(restrictedDir.exists()).thenReturn(true)
        whenever(restrictedDir.isDirectory).thenReturn(true)
        whenever(restrictedDir.canWrite()).thenReturn(false)
        whenever(restrictedDir.path) doReturn fakeRootDir.path
        testedOrchestrator = BatchFileOrchestrator(
            restrictedDir,
            TEST_PERSISTENCE_CONFIG,
            mockLogger,
            mockMetricsDispatcher
        )

        // When
        val result = testedOrchestrator.getAllFiles()

        // Then
        assertThat(result).isEmpty()
        mockLogger.verifyLog(
            InternalLogger.Level.ERROR,
            listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
            BatchFileOrchestrator.ERROR_ROOT_NOT_WRITABLE.format(Locale.US, fakeRootDir.path)
        )
    }

    @Test
    fun `M create the rootDirectory W getAllFiles() {root does not exist}`() {
        // Given
        fakeRootDir.deleteRecursively()

        // When
        val result = testedOrchestrator.getAllFiles()

        // Then
        assertThat(result).isEmpty()
        assertThat(fakeRootDir).exists().isDirectory()
    }

    @Test
    fun `M return empty list W getAllFiles() {dir is empty}`() {
        // Given
        assumeTrue(fakeRootDir.listFiles().isNullOrEmpty())

        // When
        val result = testedOrchestrator.getAllFiles()

        // Then
        assertThat(result).isEmpty()
    }

    @Test
    fun `M return all files W getAllFiles() {dir is not empty}`(
        @IntForgery(1, 32) count: Int
    ) {
        // Given
        assumeTrue(fakeRootDir.listFiles().isNullOrEmpty())
        val old = System.currentTimeMillis() - (RECENT_DELAY_MS * 2)
        val new = System.currentTimeMillis() - (RECENT_DELAY_MS / 2)
        val expectedFiles = mutableListOf<File>()
        for (i in 1..count) {
            // create both non readable and non writable files
            expectedFiles.add(
                File(fakeRootDir, (new + i).toString()).also {
                    it.createNewFile()
                    testedOrchestrator.fileObserver.onEvent(FileObserver.CREATE, it.name)
                }
            )
            expectedFiles.add(
                File(fakeRootDir, (old - i).toString()).also {
                    it.createNewFile()
                    testedOrchestrator.fileObserver.onEvent(FileObserver.CREATE, it.name)
                }
            )
        }

        // When
        val result = testedOrchestrator.getAllFiles()

        // Then
        assertThat(result).containsAll(expectedFiles)
    }

    @Test
    fun `M return empty list W getAllFiles() {dir files don't match pattern}`(
        @StringForgery fileName: String
    ) {
        // Given
        assumeTrue(fakeRootDir.listFiles().isNullOrEmpty())
        val file = File(fakeRootDir, fileName)
        file.createNewFile()

        // When
        val result = testedOrchestrator.getAllFiles()

        // Then
        assertThat(result).isEmpty()
    }

    // endregion

    // region getAllFlushableFiles

    @Test
    fun `M return all files W getAllFlushableFiles() {dir is not empty}`(
        @IntForgery(1, 32) count: Int
    ) {
        // Given
        assumeTrue(fakeRootDir.listFiles().isNullOrEmpty())
        val old = System.currentTimeMillis() - (RECENT_DELAY_MS * 2)
        val new = System.currentTimeMillis() - (RECENT_DELAY_MS / 2)
        val expectedFiles = mutableListOf<File>()
        for (i in 1..count) {
            // create both non readable and non writable files
            expectedFiles.add(
                File(fakeRootDir, (new + i).toString()).also {
                    it.createNewFile()
                    testedOrchestrator.fileObserver.onEvent(FileObserver.CREATE, it.name)
                }
            )
            expectedFiles.add(
                File(fakeRootDir, (old - i).toString()).also {
                    it.createNewFile()
                    testedOrchestrator.fileObserver.onEvent(FileObserver.CREATE, it.name)
                }
            )
        }

        // When
        val result = testedOrchestrator.getFlushableFiles()

        // Then
        assertThat(result).containsAll(expectedFiles)
    }

    @Test
    fun `M return empty list W getAllFlushableFiles() {dir is empty}`() {
        // Given
        assumeTrue(fakeRootDir.listFiles().isNullOrEmpty())

        // When
        val result = testedOrchestrator.getFlushableFiles()

        // Then
        assertThat(result).isEmpty()
    }

    // endregion

    // region getRootDir

    @Test
    fun `M warn W getRootDir() {root is not a dir}`(
        @StringForgery fileName: String
    ) {
        // Given
        val notADir = File(fakeRootDir, fileName)
        notADir.createNewFile()
        testedOrchestrator = BatchFileOrchestrator(
            notADir,
            TEST_PERSISTENCE_CONFIG,
            mockLogger,
            mockMetricsDispatcher
        )

        // When
        val result = testedOrchestrator.getRootDir()

        // Then
        assertThat(result).isNull()
        mockLogger.verifyLog(
            InternalLogger.Level.ERROR,
            listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
            BatchFileOrchestrator.ERROR_ROOT_NOT_DIR.format(Locale.US, notADir.path)
        )
    }

    @Test
    fun `M warn W getRootDir() {root can't be created}`() {
        // Given
        val corruptedDir = mock<File>()
        whenever(corruptedDir.exists()).thenReturn(false)
        whenever(corruptedDir.mkdirs()).thenReturn(false)
        whenever(corruptedDir.path) doReturn fakeRootDir.path
        testedOrchestrator = BatchFileOrchestrator(
            corruptedDir,
            TEST_PERSISTENCE_CONFIG,
            mockLogger,
            mockMetricsDispatcher
        )

        // When
        val result = testedOrchestrator.getRootDir()

        // Then
        assertThat(result).isNull()
        mockLogger.verifyLog(
            InternalLogger.Level.ERROR,
            listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
            BatchFileOrchestrator.ERROR_CANT_CREATE_ROOT.format(Locale.US, fakeRootDir.path)
        )
    }

    @Test
    fun `M warn W getRootDir() {root is not writeable}`() {
        // Given
        val restrictedDir = mock<File>()
        whenever(restrictedDir.exists()).thenReturn(true)
        whenever(restrictedDir.isDirectory).thenReturn(true)
        whenever(restrictedDir.canWrite()).thenReturn(false)
        whenever(restrictedDir.path) doReturn fakeRootDir.path
        testedOrchestrator = BatchFileOrchestrator(
            restrictedDir,
            TEST_PERSISTENCE_CONFIG,
            mockLogger,
            mockMetricsDispatcher
        )

        // When
        val result = testedOrchestrator.getRootDir()

        // Then
        assertThat(result).isNull()
        mockLogger.verifyLog(
            InternalLogger.Level.ERROR,
            listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
            BatchFileOrchestrator.ERROR_ROOT_NOT_WRITABLE.format(Locale.US, fakeRootDir.path)
        )
    }

    @Test
    fun `M return root dir`() {
        // When
        val result = testedOrchestrator.getRootDir()

        // Then
        assertThat(result).isEqualTo(fakeRootDir)
        verifyNoInteractions(mockLogger)
    }

    @Test
    fun `M return root dir { multithreaded }`(
        @IntForgery(4, 8) repeatCount: Int
    ) {
        // since getRootDir involves the creation of the directory structure,
        // we need to make sure that other threads won't try to create it again when it is already
        // created by some thread

        // Given
        fakeRootDir.deleteRecursively()
        val countDownLatch = CountDownLatch(repeatCount)
        val results = mutableListOf<File?>()

        // When
        repeat(repeatCount) {
            Thread {
                val result = testedOrchestrator.getRootDir()
                synchronized(results) { results.add(result) }
                countDownLatch.countDown()
            }.start()
        }
        countDownLatch.await(5, TimeUnit.SECONDS)

        // Then
        assertThat(countDownLatch.count).isZero()
        assertThat(results)
            .hasSize(repeatCount)
            .containsOnly(fakeRootDir)
        verifyNoInteractions(mockLogger)
    }

    // endregion

    // region getRootDirName

    @Test
    fun `M return rootDirName W getRootDirName()`() {
        // When
        val result = testedOrchestrator.getRootDirName()

        // Then
        assertThat(result).isEqualTo(fakeRootDir.nameWithoutExtension)
    }

    // endregion

    // region getMetadataFile

    @Test
    fun `M return metadata file W getMetadataFile()`() {
        // Given
        val fakeFileName = System.currentTimeMillis().toString()
        val fakeFile = File(fakeRootDir.path, fakeFileName)

        // When
        val result = testedOrchestrator.getMetadataFile(fakeFile)

        // Then
        assertThat(result).isNotNull()
        assertThat(result!!.name).isEqualTo("${fakeFileName}_metadata")
    }

    @Test
    fun `M log debug file W getMetadataFile() { file is from another folder }`(
        @StringForgery fakeSuffix: String
    ) {
        // Given
        val fakeFileName = System.currentTimeMillis().toString()
        val fakeFile = File("${fakeRootDir.parent}$fakeSuffix", fakeFileName)

        // When
        val result = testedOrchestrator.getMetadataFile(fakeFile)

        // Then
        assertThat(result).isNotNull()
        mockLogger.verifyLog(
            InternalLogger.Level.DEBUG,
            listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
            BatchFileOrchestrator.DEBUG_DIFFERENT_ROOT
                .format(Locale.US, fakeFile.path, fakeRootDir.path)
        )
    }

    @Test
    fun `M log error file W getMetadataFile() { not batch file argument }`(
        @StringForgery fakeFileName: String
    ) {
        // Given
        val fakeFile = File(fakeRootDir.path, fakeFileName)

        // When
        val result = testedOrchestrator.getMetadataFile(fakeFile)

        // Then
        assertThat(result).isNull()
        mockLogger.verifyLog(
            InternalLogger.Level.ERROR,
            listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
            BatchFileOrchestrator.ERROR_NOT_BATCH_FILE.format(Locale.US, fakeFile.path)
        )
    }

    // endregion

    companion object {

        private const val RECENT_DELAY_MS = 250L

        private const val MAX_ITEM_PER_BATCH: Int = 32
        private const val MAX_ITEM_SIZE: Int = 256
        private const val MAX_BATCH_SIZE: Int = MAX_ITEM_PER_BATCH * (MAX_ITEM_SIZE + 1)
        private const val SMALL_ITEM_SIZE: Int = 32

        private const val OLD_FILE_THRESHOLD: Long = RECENT_DELAY_MS * 4
        private const val MAX_DISK_SPACE = MAX_BATCH_SIZE * 4

        private const val CLEANUP_FREQUENCY_THRESHOLD_MS = 50L

        private val TEST_PERSISTENCE_CONFIG = FilePersistenceConfig(
            RECENT_DELAY_MS,
            MAX_BATCH_SIZE.toLong(),
            MAX_ITEM_SIZE.toLong(),
            MAX_ITEM_PER_BATCH,
            OLD_FILE_THRESHOLD,
            MAX_DISK_SPACE.toLong(),
            CLEANUP_FREQUENCY_THRESHOLD_MS
        )
    }
}
