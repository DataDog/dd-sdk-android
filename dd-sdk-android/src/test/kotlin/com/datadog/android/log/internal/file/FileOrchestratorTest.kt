/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.log.internal.file

import com.datadog.android.core.internal.data.Orchestrator
import com.datadog.android.core.internal.data.file.FileOrchestrator
import com.datadog.android.core.internal.domain.FilePersistenceConfig
import com.datadog.android.utils.forge.Configurator
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.annotation.StringForgeryType
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import java.io.File
import org.assertj.core.api.Assertions.assertThat
import org.junit.Assume.assumeTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.junit.jupiter.api.io.TempDir
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings()
@ForgeConfiguration(Configurator::class)
internal class FileOrchestratorTest {

    @TempDir
    lateinit var tempDir: File
    lateinit var tempLogsDir: File
    lateinit var testedOrchestrator: Orchestrator

    @StringForgery(type = StringForgeryType.ALPHABETICAL)
    lateinit var fakeStorageFolderName: String

    @BeforeEach
    fun `set up`() {
        tempLogsDir = File(tempDir, fakeStorageFolderName)
        tempLogsDir.mkdirs()
        assumeTrue(tempLogsDir.exists() && tempLogsDir.isDirectory && tempLogsDir.canWrite())

        testedOrchestrator =
            FileOrchestrator(
                tempLogsDir,
                FilePersistenceConfig(
                    recentDelayMs = RECENT_DELAY_MS,
                    maxBatchSize = MAX_BATCH_SIZE,
                    maxItemsPerBatch = MAX_LOGS_PER_BATCH,
                    oldFileThreshold = OLD_FILE_THRESHOLD,
                    maxDiskSpace = MAX_DISK_SPACE
                )
            )
    }

    @Test
    fun `getWritableFile will returns null if the rootDirectory is a File`(
        @IntForgery(min = 1, max = MAX_LOG_SIZE) logSize: Int
    ) {
        // Given
        val invalidRootDir = File(tempDir, "testPathName")
        invalidRootDir.createNewFile()

        testedOrchestrator =
            FileOrchestrator(
                invalidRootDir,
                FilePersistenceConfig(
                    recentDelayMs = RECENT_DELAY_MS,
                    maxBatchSize = MAX_BATCH_SIZE,
                    maxItemsPerBatch = MAX_LOGS_PER_BATCH,
                    oldFileThreshold = OLD_FILE_THRESHOLD,
                    maxDiskSpace = MAX_DISK_SPACE
                )
            )
        // Then
        assertThat(testedOrchestrator.getWritableFile(logSize)).isNull()
    }

    @Test
    fun `getReadableFile will return null if the rootDirectory is a File`() {
        // Given
        val invalidRootDir = File(tempDir, "testPathName")
        invalidRootDir.createNewFile()

        testedOrchestrator =
            FileOrchestrator(
                invalidRootDir,
                FilePersistenceConfig(
                    recentDelayMs = RECENT_DELAY_MS,
                    maxBatchSize = MAX_BATCH_SIZE,
                    maxItemsPerBatch = MAX_LOGS_PER_BATCH,
                    oldFileThreshold = OLD_FILE_THRESHOLD,
                    maxDiskSpace = MAX_DISK_SPACE
                )
            )

        // Then
        assertThat(testedOrchestrator.getReadableFile(emptySet())).isNull()
    }

    @Test
    fun `getWritableFile returns null if the rootDirectory can't be created`(
        @IntForgery(min = 1, max = MAX_LOG_SIZE) logSize: Int
    ) {
        // Given
        val corruptedRootDir = mock<File>()
        whenever(corruptedRootDir.mkdirs()).thenReturn(false)

        testedOrchestrator =
            FileOrchestrator(
                corruptedRootDir,
                FilePersistenceConfig(
                    recentDelayMs = RECENT_DELAY_MS,
                    maxBatchSize = MAX_BATCH_SIZE,
                    maxItemsPerBatch = MAX_LOGS_PER_BATCH,
                    oldFileThreshold = OLD_FILE_THRESHOLD,
                    maxDiskSpace = MAX_DISK_SPACE
                )
            )

        // Then
        assertThat(testedOrchestrator.getWritableFile(logSize)).isNull()
    }

    @Test
    fun `getReadableFile returns null the rootDirectory can't be created`() {
        // Given
        val corruptedRootDir = mock<File>()
        whenever(corruptedRootDir.mkdirs()).thenReturn(false)

        testedOrchestrator =
            FileOrchestrator(
                corruptedRootDir,
                FilePersistenceConfig(
                    recentDelayMs = RECENT_DELAY_MS,
                    maxBatchSize = MAX_BATCH_SIZE,
                    maxItemsPerBatch = MAX_LOGS_PER_BATCH,
                    oldFileThreshold = OLD_FILE_THRESHOLD,
                    maxDiskSpace = MAX_DISK_SPACE
                )
            )

        // Then
        assertThat(testedOrchestrator.getReadableFile(emptySet())).isNull()
    }

    @Test
    fun `reuse writeable file if possible`(
        @IntForgery(min = 1, max = MAX_LOG_SIZE) logSize1: Int,
        @IntForgery(min = 1, max = MAX_LOG_SIZE) logSize2: Int,
        forge: Forge
    ) {
        val previousFile = testedOrchestrator.getWritableFile(logSize1)
        previousFile?.createNewFile()
        previousFile?.writeText(forge.anAsciiString(logSize1))

        val writeableFile = testedOrchestrator.getWritableFile(logSize2)

        assertThat(writeableFile)
            .isEqualTo(previousFile)
            .exists()
    }

    @Test
    fun `create writeable file if none exists`(
        @IntForgery(min = 1, max = MAX_LOG_SIZE) logSize: Int
    ) {
        val writeableFile = testedOrchestrator.getWritableFile(logSize)

        assertThat(writeableFile)
            .doesNotExist()
            .hasParent(tempLogsDir)
    }

    @Test
    fun `M recreate the rootDirectory W getWritableFile { rootDir deleted }`(
        @IntForgery(min = 1, max = MAX_LOG_SIZE) logSize: Int
    ) {
        // GIVEN
        testedOrchestrator.getWritableFile(logSize)
        tempLogsDir.deleteRecursively()

        // WHEN
        val writeableFile = testedOrchestrator.getWritableFile(logSize)

        assertThat(writeableFile)
            .doesNotExist()
            .hasParent(tempLogsDir)
    }

    @Test
    fun `create writeable file if previous exists but is unknown`(
        @IntForgery(min = 1, max = MAX_LOG_SIZE) logSize: Int
    ) {
        val now = System.currentTimeMillis() - 1000
        val previousFile = File(tempLogsDir, now.toString())
        previousFile.createNewFile()

        val writeableFile = testedOrchestrator.getWritableFile(logSize)

        assertThat(writeableFile)
            .doesNotExist()
            .hasParent(tempLogsDir)
            .isNotEqualTo(previousFile)
    }

    @Test
    fun `create writeable file if previous known has been deleted`(
        @IntForgery(min = 1, max = MAX_LOG_SIZE) logSize: Int
    ) {
        val previousFile = testedOrchestrator.getWritableFile(logSize)
        Thread.sleep(10)

        val writeableFile = testedOrchestrator.getWritableFile(logSize)

        assertThat(writeableFile)
            .doesNotExist()
            .hasParent(tempLogsDir)
            .isNotEqualTo(previousFile)
    }

    @Test
    fun `create writeable file if previous known is too old`(
        @IntForgery(min = 1, max = MAX_LOG_SIZE) logSize: Int
    ) {
        val previousFile = testedOrchestrator.getWritableFile(logSize)
        previousFile?.createNewFile()
        Thread.sleep(RECENT_DELAY_MS)

        val writeableFile = testedOrchestrator.getWritableFile(logSize)

        assertThat(writeableFile)
            .doesNotExist()
            .hasParent(tempLogsDir)
            .isNotEqualTo(previousFile)
    }

    @Test
    fun `create writeable file if previous known is full in size`(
        @IntForgery(min = 1, max = MAX_LOG_SIZE) logSize: Int,
        forge: Forge
    ) {
        val previousFile = testedOrchestrator.getWritableFile(logSize)
        previousFile?.createNewFile()
        previousFile?.writeText(forge.anAsciiString(MAX_BATCH_SIZE.toInt()))

        val writeableFile = testedOrchestrator.getWritableFile(logSize)

        assertThat(writeableFile)
            .doesNotExist()
            .hasParent(tempLogsDir)
            .isNotEqualTo(previousFile)
    }

    @Test
    fun `create writeable file if previous known is full in logs count`(
        @IntForgery(min = 1, max = MAX_LOG_SIZE) logSize: Int,
        forge: Forge
    ) {
        val previousFile = testedOrchestrator.getWritableFile(logSize)
        previousFile?.createNewFile()
        previousFile?.writeText(forge.anAsciiString(logSize))
        for (i in 1 until MAX_LOGS_PER_BATCH) {
            val f = testedOrchestrator.getWritableFile(logSize)
            assumeTrue(f == previousFile)
            f?.writeText(forge.anAsciiString(logSize))
        }

        val writeableFile = testedOrchestrator.getWritableFile(logSize)

        assertThat(writeableFile)
            .doesNotExist()
            .hasParent(tempLogsDir)
            .isNotEqualTo(previousFile)
    }

    @Test
    fun `getWritableFile discards oldest files if too many space is taken on disk`(
        @IntForgery(min = 1, max = MAX_LOG_SIZE) logSize: Int,
        forge: Forge
    ) {
        val batches = MAX_DISK_SPACE / MAX_BATCH_SIZE
        val earlier = System.currentTimeMillis() - OLD_FILE_THRESHOLD - RECENT_DELAY_MS
        val writtenFiles = mutableListOf<File>()
        for (i in 0..batches) {
            val writtenFile = File(tempLogsDir, (earlier + i).toString())
            writtenFile.createNewFile()
            writtenFile.writeText(forge.anAsciiString(MAX_BATCH_SIZE.toInt()))
            writtenFiles.add(writtenFile)
        }

        val writeableFile = testedOrchestrator.getWritableFile(logSize)

        assertThat(writeableFile)
            .doesNotExist()
            .isNotIn(writtenFiles)
        assertThat(writtenFiles.first())
            .doesNotExist()
    }

    @Test
    fun `getReadableFile file`() {
        val earlier = System.currentTimeMillis() - RECENT_DELAY_MS - RECENT_DELAY_MS
        val writtenFile = File(tempLogsDir, earlier.toString())
        writtenFile.createNewFile()

        val readableFile = testedOrchestrator.getReadableFile(emptySet())

        assertThat(readableFile)
            .isEqualTo(writtenFile)
    }

    @Test
    fun `getReadableFile with excludes returns null`() {
        val earlier = System.currentTimeMillis() - RECENT_DELAY_MS - RECENT_DELAY_MS
        val writtenFile = File(tempLogsDir, earlier.toString())
        writtenFile.createNewFile()

        val readableFile = testedOrchestrator.getReadableFile(setOf(writtenFile.name))

        assertThat(readableFile)
            .isNull()
    }

    @Test
    fun `M return null W getReadableFile { rootDir deleted }`() {
        // GIVEN
        tempLogsDir.deleteRecursively()

        // THEN
        assertThat(testedOrchestrator.getReadableFile(emptySet())).isNull()
    }

    @Test
    fun `getReadableFile ignores recent`(forge: Forge) {
        val earlier = System.currentTimeMillis() - (RECENT_DELAY_MS / 2)
        val writtenFile = File(tempLogsDir, earlier.toString())
        writtenFile.createNewFile()
        writtenFile.writeText(forge.anAsciiString())

        val readableFile = testedOrchestrator.getReadableFile(emptySet())

        assertThat(readableFile)
            .isNull()
    }

    @Test
    fun `getReadableFile discards obsolete files`(forge: Forge) {
        val earlier = System.currentTimeMillis() - OLD_FILE_THRESHOLD - RECENT_DELAY_MS
        val writtenFile = File(tempLogsDir, earlier.toString())
        writtenFile.createNewFile()
        writtenFile.writeText(forge.anAsciiString())

        val readableFile = testedOrchestrator.getReadableFile(emptySet())

        assertThat(readableFile)
            .isNull()
        assertThat(writtenFile)
            .doesNotExist()
    }

    @Test
    fun `M reset the internal attributes W reset`(
        @IntForgery(min = 1, max = MAX_LOG_SIZE) logSize1: Int,
        @IntForgery(min = 1, max = MAX_LOG_SIZE) logSize2: Int,
        forge: Forge
    ) {
        // GIVEN
        val previousFile = testedOrchestrator.getWritableFile(logSize1)
        previousFile?.createNewFile()
        previousFile?.writeText(forge.anAsciiString(logSize1))
        testedOrchestrator.getWritableFile(logSize2)

        // WHEN
        testedOrchestrator.reset()

        // THEN
        val testedFileOrchestrator = testedOrchestrator as FileOrchestrator
        assertThat(testedFileOrchestrator.previousFileLogCount).isEqualTo(0)
        assertThat(testedFileOrchestrator.previousFile).isNull()
    }

    companion object {
        private const val RECENT_DELAY_MS = 150L

        const val MAX_BATCH_SIZE: Long = 32 * 1024
        const val MAX_LOGS_PER_BATCH: Int = 32
        const val MAX_LOG_SIZE: Int = 256

        const val OLD_FILE_THRESHOLD: Long = RECENT_DELAY_MS * 4
        const val MAX_DISK_SPACE = MAX_BATCH_SIZE * 4
    }
}
