/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.persistence.file

import com.datadog.android.api.InternalLogger
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.utils.verifyLog
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import java.io.File
import java.util.Locale
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assumptions.assumeFalse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.junit.jupiter.api.io.TempDir
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@ForgeConfiguration(Configurator::class)
@MockitoSettings(strictness = Strictness.LENIENT)
internal class FileMoverTest {

    lateinit var testedFileMover: FileMover

    @StringForgery(regex = "([a-z]+)-([a-z]+)")
    lateinit var fakeSrcDirName: String

    @StringForgery(regex = "([a-z]+)-([a-z]+)")
    lateinit var fakeDstDirName: String

    @TempDir
    lateinit var fakeRootDirectory: File

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    private lateinit var fakeSrcDir: File
    private lateinit var fakeDstDir: File

    @BeforeEach
    fun setUp() {
        fakeSrcDir = File(fakeRootDirectory, fakeSrcDirName)
        fakeDstDir = File(fakeRootDirectory, fakeDstDirName)
        testedFileMover = FileMover(mockInternalLogger)
    }

    // region delete

    @Test
    fun `𝕄 delete file 𝕎 delete()`(
        @StringForgery fileName: String
    ) {
        // Given
        val file = File(fakeRootDirectory, fileName)
        file.createNewFile()

        // When
        val result = testedFileMover.delete(file)

        // Then
        assertThat(result).isTrue()
        assertThat(file).doesNotExist()
    }

    @Test
    fun `𝕄 delete folder recursively 𝕎 delete()`(
        @StringForgery dirName: String,
        @StringForgery fileName: String,
        @IntForgery(1, 64) fileCount: Int
    ) {
        // Given
        val dir = File(fakeRootDirectory, dirName)
        dir.mkdir()
        for (i in 0 until fileCount) {
            File(dir, "$fileName$i").createNewFile()
        }

        // When
        val result = testedFileMover.delete(dir)

        // Then
        assertThat(result).isTrue()
        assertThat(dir).doesNotExist()
    }

    @Test
    fun `𝕄 delete folder recursively 𝕎 delete() {nested dirs}`(
        @StringForgery dirName: String,
        @StringForgery fileName: String,
        @IntForgery(1, 10) fileCount: Int
    ) {
        // Given
        val dir = File(fakeRootDirectory, dirName)
        dir.mkdir()
        var parent = dir
        for (i in 1 until fileCount) {
            parent = File(parent, "$dirName$i")
            parent.mkdir()
        }
        val file = File(parent, fileName)
        file.createNewFile()

        // When
        val result = testedFileMover.delete(dir)

        // Then
        assertThat(result).isTrue()
        assertThat(dir).doesNotExist()
        assertThat(file).doesNotExist()
    }

    // endregion

    // region moveFiles

    @Test
    fun `𝕄 return true and warn 𝕎 moveFiles() {source dir does not exist}`() {
        // Given
        assumeFalse(fakeSrcDir.exists())
        fakeDstDir.mkdirs()

        // When
        val result = testedFileMover.moveFiles(fakeSrcDir, fakeDstDir)

        // Then
        assertThat(result).isTrue()
        mockInternalLogger.verifyLog(
            InternalLogger.Level.INFO,
            InternalLogger.Target.MAINTAINER,
            FileMover.INFO_MOVE_NO_SRC.format(Locale.US, fakeSrcDir.path)
        )
    }

    @Test
    fun `𝕄 return false and warn 𝕎 moveFiles() {source dir is not a dir}`() {
        // Given
        fakeSrcDir.createNewFile()
        fakeDstDir.mkdirs()

        // When
        val result = testedFileMover.moveFiles(fakeSrcDir, fakeDstDir)

        // Then
        assertThat(result).isFalse()
        mockInternalLogger.verifyLog(
            InternalLogger.Level.ERROR,
            targets = listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
            FileMover.ERROR_MOVE_NOT_DIR.format(Locale.US, fakeSrcDir.path)
        )
    }

    @Test
    fun `𝕄 return false and warn 𝕎 moveFiles() {dest dir is not a dir}`() {
        // Given
        fakeSrcDir.mkdirs()
        fakeDstDir.createNewFile()

        // When
        val result = testedFileMover.moveFiles(fakeSrcDir, fakeDstDir)

        // Then
        assertThat(result).isFalse()
        mockInternalLogger.verifyLog(
            InternalLogger.Level.ERROR,
            targets = listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
            FileMover.ERROR_MOVE_NOT_DIR.format(Locale.US, fakeDstDir.path)
        )
    }

    @Test
    fun `𝕄 move all files and return true 𝕎 moveFiles()`(
        @StringForgery fileNames: List<String>
    ) {
        // Given
        fakeSrcDir.mkdirs()
        fakeDstDir.mkdirs()
        fileNames.forEach { name ->
            File(fakeSrcDir, name).writeText(name.reversed())
        }
        val expectedFiles = fileNames.map { name ->
            File(fakeDstDir, name)
        }

        // When
        val result = testedFileMover.moveFiles(fakeSrcDir, fakeDstDir)

        // Then
        assertThat(result).isTrue()
        assertThat(fakeSrcDir.listFiles()).isEmpty()
        expectedFiles.forEach {
            assertThat(it).exists()
                .hasContent(it.name.reversed())
        }
    }

    @Test
    fun `𝕄 do nothing and return true 𝕎 moveFiles() {source dir is empty}`() {
        // Given
        fakeSrcDir.mkdirs()
        fakeDstDir.mkdirs()

        // When
        val result = testedFileMover.moveFiles(fakeSrcDir, fakeDstDir)

        // Then
        assertThat(result).isTrue()
        assertThat(fakeSrcDir.listFiles()).isEmpty()
        assertThat(fakeDstDir.listFiles()).isEmpty()
    }

    @Test
    fun `𝕄 create dest, move all files and return true 𝕎 moveFiles() {dest dir does not exist}`(
        @StringForgery fileNamesInput: List<String>
    ) {
        // Given
        fakeSrcDir.mkdirs()
        assumeFalse(fakeDstDir.exists())

        // in case of file system is not case-sensitive, we need to drop all duplicates
        val fileNames = fileNamesInput.distinctBy { it.lowercase(Locale.US) }
        fileNames.forEach { name ->
            File(fakeSrcDir, name).writeText(name.reversed())
        }
        val expectedFiles = fileNames.map { name ->
            File(fakeDstDir, name)
        }

        // When
        val result = testedFileMover.moveFiles(fakeSrcDir, fakeDstDir)

        // Then
        assertThat(result).isTrue()
        assertThat(fakeSrcDir.listFiles()).isEmpty()
        expectedFiles.forEach {
            assertThat(it).exists()
                .hasContent(it.name.reversed())
        }
    }

    // endregion
}
