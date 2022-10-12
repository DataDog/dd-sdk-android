/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.persistence.file.batch

import com.datadog.android.core.internal.persistence.file.FileOrchestrator
import com.datadog.android.core.internal.persistence.file.FilePersistenceConfig
import com.datadog.android.log.Logger
import com.datadog.android.log.internal.logger.LogHandler
import com.datadog.android.log.internal.utils.ERROR_WITH_TELEMETRY_LEVEL
import com.datadog.android.utils.forge.Configurator
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyZeroInteractions
import com.nhaarman.mockitokotlin2.whenever
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
import org.mockito.quality.Strictness
import java.io.File
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@ForgeConfiguration(Configurator::class)
@MockitoSettings(strictness = Strictness.LENIENT)
internal class BatchFileOrchestratorTest {

    lateinit var testedOrchestrator: FileOrchestrator

    @TempDir
    lateinit var tempDir: File

    @Mock
    lateinit var mockLogHandler: LogHandler

    @StringForgery
    lateinit var fakeRootDirName: String

    lateinit var fakeRootDir: File

    @BeforeEach
    fun `set up`() {
        fakeRootDir = File(tempDir, fakeRootDirName)
        fakeRootDir.mkdirs()
        testedOrchestrator = BatchFileOrchestrator(
            fakeRootDir,
            TEST_PERSISTENCE_CONFIG,
            Logger(mockLogHandler)
        )
    }

    // region getWritableFile

    @Test
    fun `𝕄 warn 𝕎 getWritableFile() {root is not a dir}`(
        @StringForgery fileName: String,
        @IntForgery(min = 1, max = MAX_ITEM_SIZE) dataSize: Int
    ) {
        // Given
        val notADir = File(fakeRootDir, fileName)
        notADir.createNewFile()
        testedOrchestrator = BatchFileOrchestrator(
            notADir,
            TEST_PERSISTENCE_CONFIG,
            Logger(mockLogHandler)
        )

        // When
        val result = testedOrchestrator.getWritableFile(dataSize)

        // Then
        assertThat(result).isNull()
        verify(mockLogHandler).handleLog(
            ERROR_WITH_TELEMETRY_LEVEL,
            BatchFileOrchestrator.ERROR_ROOT_NOT_DIR.format(Locale.US, notADir.path)
        )
    }

    @Test
    fun `𝕄 warn 𝕎 getWritableFile() {root can't be created}`(
        @IntForgery(min = 1, max = MAX_ITEM_SIZE) dataSize: Int
    ) {
        // Given
        val corruptedDir = mock<File>()
        whenever(corruptedDir.exists()).thenReturn(false)
        whenever(corruptedDir.mkdirs()).thenReturn(false)
        whenever(corruptedDir.path) doReturn fakeRootDir.path
        testedOrchestrator = BatchFileOrchestrator(
            corruptedDir,
            TEST_PERSISTENCE_CONFIG,
            Logger(mockLogHandler)
        )

        // When
        val result = testedOrchestrator.getWritableFile(dataSize)

        // Then
        assertThat(result).isNull()
        verify(mockLogHandler).handleLog(
            ERROR_WITH_TELEMETRY_LEVEL,
            BatchFileOrchestrator.ERROR_CANT_CREATE_ROOT.format(Locale.US, fakeRootDir.path)
        )
    }

    @Test
    fun `𝕄 warn 𝕎 getWritableFile() {root is not writeable}`(
        @IntForgery(min = 1, max = MAX_ITEM_SIZE) dataSize: Int
    ) {
        // Given
        val restrictedDir = mock<File>()
        whenever(restrictedDir.exists()).thenReturn(true)
        whenever(restrictedDir.isDirectory).thenReturn(true)
        whenever(restrictedDir.canWrite()).thenReturn(false)
        whenever(restrictedDir.path) doReturn fakeRootDir.path
        testedOrchestrator = BatchFileOrchestrator(
            restrictedDir,
            TEST_PERSISTENCE_CONFIG,
            Logger(mockLogHandler)
        )

        // When
        val result = testedOrchestrator.getWritableFile(dataSize)

        // Then
        assertThat(result).isNull()
        verify(mockLogHandler).handleLog(
            ERROR_WITH_TELEMETRY_LEVEL,
            BatchFileOrchestrator.ERROR_ROOT_NOT_WRITABLE.format(Locale.US, fakeRootDir.path)
        )
    }

    @Test
    fun `𝕄 warn 𝕎 getWritableFile() {data is too big}`(
        @IntForgery(min = MAX_ITEM_SIZE + 1, max = MAX_BATCH_SIZE) dataSize: Int
    ) {
        // When
        val result = testedOrchestrator.getWritableFile(dataSize)

        // Then
        assertThat(result).isNull()
        verify(mockLogHandler).handleLog(
            ERROR_WITH_TELEMETRY_LEVEL,
            BatchFileOrchestrator.ERROR_LARGE_DATA.format(Locale.US, dataSize, MAX_ITEM_SIZE)
        )
    }

    @Test
    fun `𝕄 create the rootDirectory 𝕎 getWritableFile() {root does not exist}`(
        @IntForgery(min = 1, max = MAX_ITEM_SIZE) logSize: Int
    ) {
        // Given
        fakeRootDir.deleteRecursively()

        // When
        testedOrchestrator.getWritableFile(logSize)

        // Then
        assertThat(fakeRootDir).exists().isDirectory()
    }

    @Test
    fun `𝕄 delete obsolete files 𝕎 getWritableFile()`(
        @IntForgery(min = 1, max = MAX_ITEM_SIZE) dataSize: Int,
        @LongForgery(min = OLD_FILE_THRESHOLD, max = Int.MAX_VALUE.toLong()) oldFileAge: Long
    ) {
        // Given
        assumeTrue(fakeRootDir.listFiles().isNullOrEmpty())
        val oldTimestamp = System.currentTimeMillis() - oldFileAge
        val oldFile = File(fakeRootDir, oldTimestamp.toString())
        oldFile.createNewFile()
        val youngTimestamp = System.currentTimeMillis() - RECENT_DELAY_MS - 1
        val youngFile = File(fakeRootDir, youngTimestamp.toString())
        youngFile.createNewFile()

        // When
        val start = System.currentTimeMillis()
        val result = testedOrchestrator.getWritableFile(dataSize)
        val end = System.currentTimeMillis()

        // Then
        checkNotNull(result)
        assertThat(result)
            .doesNotExist()
            .hasParent(fakeRootDir)
        assertThat(result.name.toLong())
            .isBetween(start, end)
        assertThat(oldFile).doesNotExist()
        assertThat(youngFile).exists()
    }

    @Test
    fun `𝕄 return new File 𝕎 getWritableFile() {no available file}`(
        @IntForgery(min = 1, max = MAX_ITEM_SIZE) dataSize: Int
    ) {
        // Given
        assumeTrue(fakeRootDir.listFiles().isNullOrEmpty())

        // When
        val start = System.currentTimeMillis()
        val result = testedOrchestrator.getWritableFile(dataSize)
        val end = System.currentTimeMillis()

        // Then
        checkNotNull(result)
        assertThat(result)
            .doesNotExist()
            .hasParent(fakeRootDir)
        assertThat(result.name.toLong())
            .isBetween(start, end)
    }

    @Test
    fun `𝕄 return existing File 𝕎 getWritableFile() {recent file exist with spare space}`(
        @StringForgery(size = SMALL_ITEM_SIZE) previousData: String,
        @IntForgery(min = 1, max = SMALL_ITEM_SIZE) dataSize: Int
    ) {
        // Given
        assumeTrue(fakeRootDir.listFiles().isNullOrEmpty())
        val previousFile = testedOrchestrator.getWritableFile(previousData.length)
        checkNotNull(previousFile)
        previousFile.writeText(previousData)
        Thread.sleep(1)

        // When
        val result = testedOrchestrator.getWritableFile(dataSize)

        // Then
        checkNotNull(result)
        assertThat(result).isEqualTo(previousFile)
        assertThat(previousFile.readText()).isEqualTo(previousData)
    }

    @Test
    fun `𝕄 return new File 𝕎 getWritableFile() {previous file is too old}`(
        @StringForgery(size = SMALL_ITEM_SIZE) previousData: String,
        @IntForgery(min = 1, max = SMALL_ITEM_SIZE) dataSize: Int
    ) {
        // Given
        assumeTrue(fakeRootDir.listFiles().isNullOrEmpty())
        val previousFile = testedOrchestrator.getWritableFile(previousData.length)
        checkNotNull(previousFile)
        previousFile.writeText(previousData)
        Thread.sleep(RECENT_DELAY_MS + 1)

        // When
        val start = System.currentTimeMillis()
        val result = testedOrchestrator.getWritableFile(dataSize)
        val end = System.currentTimeMillis()

        // Then
        checkNotNull(result)
        assertThat(result)
            .doesNotExist()
            .hasParent(fakeRootDir)
        assertThat(result.name.toLong())
            .isBetween(start, end)
        assertThat(previousFile.readText()).isEqualTo(previousData)
    }

    @Test
    fun `𝕄 return new File 𝕎 getWritableFile() {previous file is unknown}`(
        @StringForgery(size = SMALL_ITEM_SIZE) previousData: String,
        @IntForgery(min = 1, max = SMALL_ITEM_SIZE) dataSize: Int
    ) {
        // Given
        assumeTrue(fakeRootDir.listFiles().isNullOrEmpty())
        val previousFile = File(fakeRootDir, System.currentTimeMillis().toString())
        previousFile.writeText(previousData)
        Thread.sleep(1)

        // When
        val start = System.currentTimeMillis()
        val result = testedOrchestrator.getWritableFile(dataSize)
        val end = System.currentTimeMillis()

        // Then
        checkNotNull(result)
        assertThat(result)
            .doesNotExist()
            .hasParent(fakeRootDir)
        assertThat(result.name.toLong())
            .isBetween(start, end)
        assertThat(previousFile.readText()).isEqualTo(previousData)
    }

    @Test
    fun `𝕄 return new File 𝕎 getWritableFile() {previous file is deleted}`(
        @IntForgery(min = 1, max = SMALL_ITEM_SIZE) previousDataSize: Int,
        @IntForgery(min = 1, max = SMALL_ITEM_SIZE) dataSize: Int
    ) {
        // Given
        assumeTrue(fakeRootDir.listFiles().isNullOrEmpty())
        val previousFile = testedOrchestrator.getWritableFile(previousDataSize)
        checkNotNull(previousFile)
        previousFile.createNewFile()
        previousFile.delete()
        Thread.sleep(1)

        // When
        val start = System.currentTimeMillis()
        val result = testedOrchestrator.getWritableFile(dataSize)
        val end = System.currentTimeMillis()

        // Then
        checkNotNull(result)
        assertThat(result)
            .doesNotExist()
            .hasParent(fakeRootDir)
        assertThat(result.name.toLong())
            .isBetween(start, end)
        assertThat(previousFile).doesNotExist()
    }

    @Test
    fun `𝕄 return new File 𝕎 getWritableFile() {previous file is too large}`(
        @StringForgery(size = MAX_BATCH_SIZE) previousData: String,
        @IntForgery(min = 1, max = SMALL_ITEM_SIZE) dataSize: Int
    ) {
        // Given
        assumeTrue(fakeRootDir.listFiles().isNullOrEmpty())
        val previousFile = testedOrchestrator.getWritableFile(1)
        checkNotNull(previousFile)
        previousFile.writeText(previousData)
        Thread.sleep(1)

        // When
        val start = System.currentTimeMillis()
        val result = testedOrchestrator.getWritableFile(dataSize)
        val end = System.currentTimeMillis()

        // Then
        checkNotNull(result)
        assertThat(result)
            .doesNotExist()
            .hasParent(fakeRootDir)
        assertThat(result.name.toLong())
            .isBetween(start, end)
        assertThat(previousFile.readText()).isEqualTo(previousData)
    }

    @Test
    fun `𝕄 return new File 𝕎 getWritableFile() {previous file has too many items}`(
        @IntForgery(min = 1, max = MAX_ITEM_SIZE) dataSize: Int,
        forge: Forge
    ) {
        // Given
        assumeTrue(fakeRootDir.listFiles().isNullOrEmpty())
        var previousFile = testedOrchestrator.getWritableFile(dataSize)

        for (round in 1 until 5) {
            checkNotNull(previousFile)

            val previousData = forge.aList(MAX_ITEM_PER_BATCH) {
                forge.anAlphabeticalString(size = dataSize)
            }

            previousFile.writeText(previousData[0])

            for (i in 1 until MAX_ITEM_PER_BATCH) {
                val file = testedOrchestrator.getWritableFile(dataSize)
                assumeTrue(file == previousFile)
                file?.appendText(previousData[i])
            }

            // When
            val start = System.currentTimeMillis()
            val nextFile = testedOrchestrator.getWritableFile(dataSize)
            val end = System.currentTimeMillis()

            // Then
            checkNotNull(nextFile)
            assertThat(nextFile)
                .doesNotExist()
                .hasParent(fakeRootDir)
            assertThat(nextFile.name.toLong())
                .isBetween(start, end)
            assertThat(previousFile.readText())
                .isEqualTo(previousData.joinToString(separator = ""))

            previousFile = nextFile
        }
    }

    @Test
    fun `𝕄 discard File 𝕎 getWritableFile() {previous files take too much disk space}`(
        @StringForgery(size = MAX_BATCH_SIZE) previousData: String,
        @IntForgery(min = 1, max = MAX_ITEM_SIZE) dataSize: Int
    ) {
        // Given
        assumeTrue(fakeRootDir.listFiles().isNullOrEmpty())
        val filesCount = MAX_DISK_SPACE / MAX_BATCH_SIZE
        val files = (0..filesCount).map {
            val file = testedOrchestrator.getWritableFile(1)
            checkNotNull(file)
            file.writeText(previousData)
            Thread.sleep(1)
            file
        }

        // When
        val start = System.currentTimeMillis()
        val result = testedOrchestrator.getWritableFile(dataSize)
        val end = System.currentTimeMillis()

        // Then
        checkNotNull(result)
        assertThat(result)
            .doesNotExist()
            .hasParent(fakeRootDir)
        assertThat(result.name.toLong())
            .isBetween(start, end)
        assertThat(files.first()).doesNotExist()
        verify(mockLogHandler).handleLog(
            ERROR_WITH_TELEMETRY_LEVEL,
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
    fun `𝕄 warn 𝕎 getReadableFile() {root is not a dir}`(
        @StringForgery fileName: String
    ) {
        // Given
        val notADir = File(fakeRootDir, fileName)
        notADir.createNewFile()
        testedOrchestrator = BatchFileOrchestrator(
            notADir,
            TEST_PERSISTENCE_CONFIG,
            Logger(mockLogHandler)
        )

        // When
        val result = testedOrchestrator.getReadableFile(emptySet())

        // Then
        assertThat(result).isNull()
        verify(mockLogHandler).handleLog(
            ERROR_WITH_TELEMETRY_LEVEL,
            BatchFileOrchestrator.ERROR_ROOT_NOT_DIR.format(Locale.US, notADir.path)
        )
    }

    @Test
    fun `𝕄 warn 𝕎 getReadableFile() {root can't be created}`() {
        // Given
        val corruptedDir = mock<File>()
        whenever(corruptedDir.exists()).thenReturn(false)
        whenever(corruptedDir.mkdirs()).thenReturn(false)
        whenever(corruptedDir.path) doReturn fakeRootDir.path
        testedOrchestrator = BatchFileOrchestrator(
            corruptedDir,
            TEST_PERSISTENCE_CONFIG,
            Logger(mockLogHandler)
        )

        // When
        val result = testedOrchestrator.getReadableFile(emptySet())

        // Then
        assertThat(result).isNull()
        verify(mockLogHandler).handleLog(
            ERROR_WITH_TELEMETRY_LEVEL,
            BatchFileOrchestrator.ERROR_CANT_CREATE_ROOT.format(Locale.US, fakeRootDir.path)
        )
    }

    @Test
    fun `𝕄 warn 𝕎 getReadableFile() {root is not writeable}`() {
        // Given
        val restrictedDir = mock<File>()
        whenever(restrictedDir.exists()).thenReturn(true)
        whenever(restrictedDir.isDirectory).thenReturn(true)
        whenever(restrictedDir.canWrite()).thenReturn(false)
        whenever(restrictedDir.path) doReturn fakeRootDir.path
        testedOrchestrator = BatchFileOrchestrator(
            restrictedDir,
            TEST_PERSISTENCE_CONFIG,
            Logger(mockLogHandler)
        )

        // When
        val result = testedOrchestrator.getReadableFile(emptySet())

        // Then
        assertThat(result).isNull()
        verify(mockLogHandler).handleLog(
            ERROR_WITH_TELEMETRY_LEVEL,
            BatchFileOrchestrator.ERROR_ROOT_NOT_WRITABLE.format(Locale.US, fakeRootDir.path)
        )
    }

    @Test
    fun `𝕄 delete obsolete files 𝕎 getReadableFile()`(
        @LongForgery(min = OLD_FILE_THRESHOLD, max = Int.MAX_VALUE.toLong()) oldFileAge: Long
    ) {
        // Given
        assumeTrue(fakeRootDir.listFiles().isNullOrEmpty())
        val oldTimestamp = System.currentTimeMillis() - oldFileAge
        val oldFile = File(fakeRootDir, oldTimestamp.toString())
        oldFile.createNewFile()
        val youngTimestamp = System.currentTimeMillis() - RECENT_DELAY_MS - 1
        val youngFile = File(fakeRootDir, youngTimestamp.toString())
        youngFile.createNewFile()

        // When
        val result = testedOrchestrator.getReadableFile(emptySet())

        // Then
        assertThat(result).isNull()
        assertThat(oldFile).doesNotExist()
        assertThat(youngFile).exists()
    }

    @Test
    fun `𝕄 create the rootDirectory 𝕎 getReadableFile() {root does not exist}`() {
        // Given
        fakeRootDir.deleteRecursively()

        // When
        testedOrchestrator.getReadableFile(emptySet())

        // Then
        assertThat(fakeRootDir).exists().isDirectory()
    }

    @Test
    fun `𝕄 return null 𝕎 getReadableFile() {empty dir}`() {
        // Given
        assumeTrue(fakeRootDir.listFiles().isNullOrEmpty())

        // When
        val result = testedOrchestrator.getReadableFile(emptySet())

        // Then
        assertThat(result).isNull()
    }

    @Test
    fun `𝕄 return file 𝕎 getReadableFile() {existing old enough file}`() {
        // Given
        assumeTrue(fakeRootDir.listFiles().isNullOrEmpty())
        val timestamp = System.currentTimeMillis() - (RECENT_DELAY_MS * 2)
        val file = File(fakeRootDir, timestamp.toString())
        file.createNewFile()

        // When
        val result = testedOrchestrator.getReadableFile(emptySet())

        // Then
        assertThat(result)
            .isEqualTo(file)
            .exists()
            .hasContent("")
    }

    @Test
    fun `𝕄 return null 𝕎 getReadableFile() {file is too recent}`() {
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
    fun `𝕄 return null 𝕎 getReadableFile() {file is in exclude list}`() {
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
    fun `𝕄 warn 𝕎 getAllFiles() {root is not a dir}`(
        @StringForgery fileName: String
    ) {
        // Given
        val notADir = File(fakeRootDir, fileName)
        notADir.createNewFile()
        testedOrchestrator = BatchFileOrchestrator(
            notADir,
            TEST_PERSISTENCE_CONFIG,
            Logger(mockLogHandler)
        )

        // When
        val result = testedOrchestrator.getAllFiles()

        // Then
        assertThat(result).isEmpty()
        verify(mockLogHandler).handleLog(
            ERROR_WITH_TELEMETRY_LEVEL,
            BatchFileOrchestrator.ERROR_ROOT_NOT_DIR.format(Locale.US, notADir.path)
        )
    }

    @Test
    fun `𝕄 warn 𝕎 getAllFiles() {root can't be created}`() {
        // Given
        val corruptedDir = mock<File>()
        whenever(corruptedDir.exists()).thenReturn(false)
        whenever(corruptedDir.mkdirs()).thenReturn(false)
        whenever(corruptedDir.path) doReturn fakeRootDir.path
        testedOrchestrator = BatchFileOrchestrator(
            corruptedDir,
            TEST_PERSISTENCE_CONFIG,
            Logger(mockLogHandler)
        )

        // When
        val result = testedOrchestrator.getAllFiles()

        // Then
        assertThat(result).isEmpty()
        verify(mockLogHandler).handleLog(
            ERROR_WITH_TELEMETRY_LEVEL,
            BatchFileOrchestrator.ERROR_CANT_CREATE_ROOT.format(Locale.US, fakeRootDir.path)
        )
    }

    @Test
    fun `𝕄 warn 𝕎 getAllFiles() {root is not writeable}`() {
        // Given
        val restrictedDir = mock<File>()
        whenever(restrictedDir.exists()).thenReturn(true)
        whenever(restrictedDir.isDirectory).thenReturn(true)
        whenever(restrictedDir.canWrite()).thenReturn(false)
        whenever(restrictedDir.path) doReturn fakeRootDir.path
        testedOrchestrator = BatchFileOrchestrator(
            restrictedDir,
            TEST_PERSISTENCE_CONFIG,
            Logger(mockLogHandler)
        )

        // When
        val result = testedOrchestrator.getAllFiles()

        // Then
        assertThat(result).isEmpty()
        verify(mockLogHandler).handleLog(
            ERROR_WITH_TELEMETRY_LEVEL,
            BatchFileOrchestrator.ERROR_ROOT_NOT_WRITABLE.format(Locale.US, fakeRootDir.path)
        )
    }

    @Test
    fun `𝕄 create the rootDirectory 𝕎 getAllFiles() {root does not exist}`() {
        // Given
        fakeRootDir.deleteRecursively()

        // When
        val result = testedOrchestrator.getAllFiles()

        // Then
        assertThat(result).isEmpty()
        assertThat(fakeRootDir).exists().isDirectory()
    }

    @Test
    fun `𝕄 return empty list 𝕎 getAllFiles() {dir is empty}`() {
        // Given
        assumeTrue(fakeRootDir.listFiles().isNullOrEmpty())

        // When
        val result = testedOrchestrator.getAllFiles()

        // Then
        assertThat(result).isEmpty()
    }

    @Test
    fun `𝕄 return all files 𝕎 getAllFiles() {dir is not empty}`(
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
                File(fakeRootDir, (new + i).toString()).also { it.createNewFile() }
            )
            expectedFiles.add(
                File(fakeRootDir, (old - i).toString()).also { it.createNewFile() }
            )
        }

        // When
        val result = testedOrchestrator.getAllFiles()

        // Then
        assertThat(result).containsAll(expectedFiles)
    }

    @Test
    fun `𝕄 return empty list 𝕎 getAllFiles() {dir files don't match pattern}`(
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
    fun `𝕄 return all files 𝕎 getAllFlushableFiles() {dir is not empty}`(
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
                File(fakeRootDir, (new + i).toString()).also { it.createNewFile() }
            )
            expectedFiles.add(
                File(fakeRootDir, (old - i).toString()).also { it.createNewFile() }
            )
        }

        // When
        val result = testedOrchestrator.getFlushableFiles()

        // Then
        assertThat(result).containsAll(expectedFiles)
    }

    @Test
    fun `𝕄 return empty list 𝕎 getAllFlushableFiles() {dir is empty}`() {
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
    fun `𝕄 warn 𝕎 getRootDir() {root is not a dir}`(
        @StringForgery fileName: String
    ) {
        // Given
        val notADir = File(fakeRootDir, fileName)
        notADir.createNewFile()
        testedOrchestrator = BatchFileOrchestrator(
            notADir,
            TEST_PERSISTENCE_CONFIG,
            Logger(mockLogHandler)
        )

        // When
        val result = testedOrchestrator.getRootDir()

        // Then
        assertThat(result).isNull()
        verify(mockLogHandler).handleLog(
            ERROR_WITH_TELEMETRY_LEVEL,
            BatchFileOrchestrator.ERROR_ROOT_NOT_DIR.format(Locale.US, notADir.path)
        )
    }

    @Test
    fun `𝕄 warn 𝕎 getRootDir() {root can't be created}`() {
        // Given
        val corruptedDir = mock<File>()
        whenever(corruptedDir.exists()).thenReturn(false)
        whenever(corruptedDir.mkdirs()).thenReturn(false)
        whenever(corruptedDir.path) doReturn fakeRootDir.path
        testedOrchestrator = BatchFileOrchestrator(
            corruptedDir,
            TEST_PERSISTENCE_CONFIG,
            Logger(mockLogHandler)
        )

        // When
        val result = testedOrchestrator.getRootDir()

        // Then
        assertThat(result).isNull()
        verify(mockLogHandler).handleLog(
            ERROR_WITH_TELEMETRY_LEVEL,
            BatchFileOrchestrator.ERROR_CANT_CREATE_ROOT.format(Locale.US, fakeRootDir.path)
        )
    }

    @Test
    fun `𝕄 warn 𝕎 getRootDir() {root is not writeable}`() {
        // Given
        val restrictedDir = mock<File>()
        whenever(restrictedDir.exists()).thenReturn(true)
        whenever(restrictedDir.isDirectory).thenReturn(true)
        whenever(restrictedDir.canWrite()).thenReturn(false)
        whenever(restrictedDir.path) doReturn fakeRootDir.path
        testedOrchestrator = BatchFileOrchestrator(
            restrictedDir,
            TEST_PERSISTENCE_CONFIG,
            Logger(mockLogHandler)
        )

        // When
        val result = testedOrchestrator.getRootDir()

        // Then
        assertThat(result).isNull()
        verify(mockLogHandler).handleLog(
            ERROR_WITH_TELEMETRY_LEVEL,
            BatchFileOrchestrator.ERROR_ROOT_NOT_WRITABLE.format(Locale.US, fakeRootDir.path)
        )
    }

    @Test
    fun `𝕄 return root dir`() {
        // When
        val result = testedOrchestrator.getRootDir()

        // Then
        assertThat(result).isEqualTo(fakeRootDir)
        verifyZeroInteractions(mockLogHandler)
    }

    @Test
    fun `𝕄 return root dir { multithreaded }`() {
        // since getRootDir involves the creation of the directory structure,
        // we need to make sure that other threads won't try to create it again when it is already
        // created by some thread

        // Given
        fakeRootDir.deleteRecursively()
        val countDownLatch = CountDownLatch(4)
        val results = mutableListOf<File?>()

        // When
        repeat(4) {
            Thread {
                val result = testedOrchestrator.getRootDir()
                results.add(result)
                countDownLatch.countDown()
            }.start()
        }
        countDownLatch.await(2, TimeUnit.SECONDS)

        // Then
        assertThat(results).containsExactlyElementsOf(List(4) { fakeRootDir })
        verifyZeroInteractions(mockLogHandler)
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

        private val TEST_PERSISTENCE_CONFIG = FilePersistenceConfig(
            RECENT_DELAY_MS,
            MAX_BATCH_SIZE.toLong(),
            MAX_ITEM_SIZE.toLong(),
            MAX_ITEM_PER_BATCH,
            OLD_FILE_THRESHOLD,
            MAX_DISK_SPACE.toLong()
        )
    }
}
