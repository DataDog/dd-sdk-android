/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.persistence.file.batch

import android.util.Log
import com.datadog.android.core.internal.persistence.file.EncryptedFileHandler
import com.datadog.android.core.internal.persistence.file.FileHandler
import com.datadog.android.log.Logger
import com.datadog.android.log.internal.logger.LogHandler
import com.datadog.android.security.Encryption
import com.datadog.android.utils.forge.Configurator
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.isNull
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.BoolForgery
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
internal class BatchFileHandlerTest {

    lateinit var testedFileHandler: FileHandler

    @Mock
    lateinit var mockLogHandler: LogHandler

    @StringForgery(regex = "([a-z]+)-([a-z]+)")
    lateinit var fakeSrcDirName: String

    @StringForgery(regex = "([a-z]+)-([a-z]+)")
    lateinit var fakeDstDirName: String

    @TempDir
    lateinit var fakeRootDirectory: File

    lateinit var fakeSrcDir: File
    lateinit var fakeDstDir: File

    @BeforeEach
    fun `set up`() {
        fakeSrcDir = File(fakeRootDirectory, fakeSrcDirName)
        fakeDstDir = File(fakeRootDirectory, fakeDstDirName)
        testedFileHandler = BatchFileHandler(Logger(mockLogHandler))
    }

    // region writeData

    @Test
    fun `𝕄 write data in empty file 𝕎 writeData() {append=false, separator=null}`(
        @StringForgery fileName: String,
        @StringForgery content: String
    ) {
        // Given
        val file = File(fakeRootDirectory, fileName)
        file.createNewFile()

        // When
        val result = testedFileHandler.writeData(
            file,
            content.toByteArray(),
            append = false,
            separator = null
        )

        // Then
        assertThat(result).isTrue()
        assertThat(file).exists().hasContent(content)
    }

    @Test
    fun `𝕄 write data in empty file  𝕎 writeData() {append=true, separator=null}`(
        @StringForgery fileName: String,
        @StringForgery content: String
    ) {
        // Given
        val file = File(fakeRootDirectory, fileName)
        file.createNewFile()

        // When
        val result = testedFileHandler.writeData(
            file,
            content.toByteArray(),
            append = false,
            separator = null
        )

        // Then
        assertThat(result).isTrue()
        assertThat(file).exists().hasContent(content)
    }

    @Test
    fun `𝕄 write data in empty file 𝕎 writeData() {append=false, separator=non null}`(
        @StringForgery fileName: String,
        @StringForgery content: String,
        @StringForgery separator: String
    ) {
        // Given
        val file = File(fakeRootDirectory, fileName)
        file.createNewFile()

        // When
        val result = testedFileHandler.writeData(
            file,
            content.toByteArray(),
            append = false,
            separator = separator.toByteArray()
        )

        // Then
        assertThat(result).isTrue()
        assertThat(file).exists().hasContent(content)
    }

    @Test
    fun `𝕄 write data in empty file  𝕎 writeData() {append=true, separator=non null }`(
        @StringForgery fileName: String,
        @StringForgery content: String,
        @StringForgery separator: String
    ) {
        // Given
        val file = File(fakeRootDirectory, fileName)
        file.createNewFile()

        // When
        val result = testedFileHandler.writeData(
            file,
            content.toByteArray(),
            append = false,
            separator = separator.toByteArray()
        )

        // Then
        assertThat(result).isTrue()
        assertThat(file).exists().hasContent(content)
    }

    @Test
    fun `𝕄 overwrite data in non empty file 𝕎 writeData() {append=false, separator=null}`(
        @StringForgery fileName: String,
        @StringForgery previousContent: String,
        @StringForgery content: String
    ) {
        // Given
        val file = File(fakeRootDirectory, fileName)
        file.writeText(previousContent)

        // When
        val result = testedFileHandler.writeData(
            file,
            content.toByteArray(),
            append = false,
            separator = null
        )

        // Then
        assertThat(result).isTrue()
        assertThat(file).exists().hasContent(content)
    }

    @Test
    fun `𝕄 overwrite data in non empty file 𝕎 writeData() {append=false, separator=non null}`(
        @StringForgery fileName: String,
        @StringForgery previousContent: String,
        @StringForgery content: String,
        @StringForgery separator: String
    ) {
        // Given
        val file = File(fakeRootDirectory, fileName)
        file.writeText(previousContent)

        // When
        val result = testedFileHandler.writeData(
            file,
            content.toByteArray(),
            append = false,
            separator = separator.toByteArray()
        )

        // Then
        assertThat(result).isTrue()
        assertThat(file).exists().hasContent(content)
    }

    @Test
    fun `𝕄 append data in non empty file 𝕎 writeData() {append=true, separator=null}`(
        @StringForgery fileName: String,
        @StringForgery previousContent: String,
        @StringForgery content: String
    ) {
        // Given
        val file = File(fakeRootDirectory, fileName)
        file.writeText(previousContent)

        // When
        val result = testedFileHandler.writeData(
            file,
            content.toByteArray(),
            append = true,
            separator = null
        )

        // Then
        assertThat(result).isTrue()
        assertThat(file).exists().hasContent(previousContent + content)
    }

    @Test
    fun `𝕄 append data in non empty file 𝕎 writeData() {append=true, separator=non null}`(
        @StringForgery fileName: String,
        @StringForgery previousContent: String,
        @StringForgery content: String,
        @StringForgery separator: String
    ) {
        // Given
        val file = File(fakeRootDirectory, fileName)
        file.writeText(previousContent)

        // When
        val result = testedFileHandler.writeData(
            file,
            content.toByteArray(),
            append = true,
            separator = separator.toByteArray()
        )

        // Then
        assertThat(result).isTrue()
        assertThat(file).exists().hasContent(previousContent + separator + content)
    }

    @Test
    fun `𝕄 return false and warn 𝕎 writeData() {parent dir does not exist}`(
        @StringForgery fileName: String,
        @StringForgery content: String,
        @BoolForgery append: Boolean,
        @StringForgery separator: String
    ) {
        // Given
        assumeFalse(fakeSrcDir.exists())
        val file = File(fakeSrcDir, fileName)

        // When
        val result = testedFileHandler.writeData(
            file,
            content.toByteArray(),
            append = append,
            separator = separator.toByteArray()
        )

        // Then
        assertThat(result).isFalse()
        assertThat(file).doesNotExist()
        verify(mockLogHandler).handleLog(
            eq(Log.ERROR),
            eq(BatchFileHandler.ERROR_WRITE.format(Locale.US, file.path)),
            any(),
            eq(emptyMap()),
            eq(emptySet()),
            isNull()
        )
    }

    @Test
    fun `𝕄 return false and warn 𝕎 writeData() {file is not file}`(
        @StringForgery fileName: String,
        @StringForgery content: String,
        @BoolForgery append: Boolean,
        @StringForgery separator: String
    ) {
        // Given
        val file = File(fakeRootDirectory, fileName)
        file.mkdirs()

        // When
        val result = testedFileHandler.writeData(
            file,
            content.toByteArray(),
            append = append,
            separator = separator.toByteArray()
        )

        // Then
        assertThat(result).isFalse()
        verify(mockLogHandler).handleLog(
            eq(Log.ERROR),
            eq(BatchFileHandler.ERROR_WRITE.format(Locale.US, file.path)),
            any(),
            eq(emptyMap()),
            eq(emptySet()),
            isNull()
        )
    }

    // endregion

    // region readData

    @Test
    fun `𝕄 return empty ByteArray and warn 𝕎 readData() {file does not exist}`(
        @StringForgery fileName: String,
        @StringForgery prefix: String,
        @StringForgery suffix: String,
        @StringForgery separator: String,
        forge: Forge
    ) {
        // Given
        val file = File(fakeRootDirectory, fileName)
        assumeFalse(file.exists())

        // When
        val result = testedFileHandler.readData(
            file,
            prefix.toByteArray(),
            suffix.toByteArray(),
            forge.aNullable { separator.toByteArray() }
        )

        // Then
        assertThat(result).isEmpty()
        assertThat(file).doesNotExist()
        verify(mockLogHandler).handleLog(
            eq(Log.ERROR),
            eq(BatchFileHandler.ERROR_READ.format(Locale.US, file.path)),
            any(),
            eq(emptyMap()),
            eq(emptySet()),
            isNull()
        )
    }

    @Test
    fun `𝕄 return empty ByteArray and warn 𝕎 readData() {file is not file}`(
        @StringForgery fileName: String,
        @StringForgery prefix: String,
        @StringForgery suffix: String,
        @StringForgery separator: String,
        forge: Forge
    ) {
        // Given
        val file = File(fakeRootDirectory, fileName)
        assumeFalse(file.exists())

        // When
        val result = testedFileHandler.readData(
            file,
            prefix.toByteArray(),
            suffix.toByteArray(),
            forge.aNullable { separator.toByteArray() }
        )

        // Then
        assertThat(result).isEmpty()
        verify(mockLogHandler).handleLog(
            eq(Log.ERROR),
            eq(BatchFileHandler.ERROR_READ.format(Locale.US, file.path)),
            any(),
            eq(emptyMap()),
            eq(emptySet()),
            isNull()
        )
    }

    @Test
    fun `𝕄 return file content 𝕎 readData() {postfix and suffix are null}`(
        @StringForgery fileName: String,
        @StringForgery content: String,
        @StringForgery separator: String,
        forge: Forge
    ) {
        // Given
        val file = File(fakeRootDirectory, fileName)
        file.writeText(content)

        // When
        val result = testedFileHandler.readData(
            file,
            null,
            null,
            forge.aNullable { separator.toByteArray() }
        )

        // Then
        assertThat(result).isEqualTo(content.toByteArray(Charsets.UTF_8))
    }

    @Test
    fun `𝕄 return decorated content 𝕎 readData() {postfix and suffix are not null}`(
        @StringForgery fileName: String,
        @StringForgery content: String,
        @StringForgery prefix: String,
        @StringForgery suffix: String,
        @StringForgery separator: String,
        forge: Forge
    ) {
        // Given
        val file = File(fakeRootDirectory, fileName)
        file.writeText(content)

        // When
        val result = testedFileHandler.readData(
            file,
            prefix.toByteArray(),
            suffix.toByteArray(),
            forge.aNullable { separator.toByteArray() }
        )

        // Then
        assertThat(result).isEqualTo(
            (prefix + content + suffix).toByteArray(Charsets.UTF_8)
        )
    }

    @Test
    fun `𝕄 return decoration only 𝕎 readData() {empty file, postfix and suffix are not null}`(
        @StringForgery fileName: String,
        @StringForgery prefix: String,
        @StringForgery suffix: String,
        @StringForgery separator: String,
        forge: Forge
    ) {
        // Given
        val file = File(fakeRootDirectory, fileName)
        file.createNewFile()

        // When
        val result = testedFileHandler.readData(
            file,
            prefix.toByteArray(),
            suffix.toByteArray(),
            forge.aNullable { separator.toByteArray() }
        )

        // Then
        assertThat(result).isEqualTo(
            (prefix + suffix).toByteArray(Charsets.UTF_8)
        )
    }

    // endregion

    // region delete

    @Test
    fun `𝕄 delete file 𝕎 delete()`(
        @StringForgery fileName: String
    ) {
        // Given
        val file = File(fakeRootDirectory, fileName)
        file.createNewFile()

        // When
        val result = testedFileHandler.delete(file)

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
        val result = testedFileHandler.delete(dir)

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
        val result = testedFileHandler.delete(dir)

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
        val result = testedFileHandler.moveFiles(fakeSrcDir, fakeDstDir)

        // Then
        assertThat(result).isTrue()
        verify(mockLogHandler).handleLog(
            Log.INFO,
            BatchFileHandler.INFO_MOVE_NO_SRC.format(Locale.US, fakeSrcDir.path)
        )
    }

    @Test
    fun `𝕄 return false and warn 𝕎 moveFiles() {source dir is not a dir}`() {
        // Given
        fakeSrcDir.createNewFile()
        fakeDstDir.mkdirs()

        // When
        val result = testedFileHandler.moveFiles(fakeSrcDir, fakeDstDir)

        // Then
        assertThat(result).isFalse()
        verify(mockLogHandler).handleLog(
            Log.ERROR,
            BatchFileHandler.ERROR_MOVE_NOT_DIR.format(Locale.US, fakeSrcDir.path)
        )
    }

    @Test
    fun `𝕄 return false and warn 𝕎 moveFiles() {dest dir is not a dir}`() {
        // Given
        fakeSrcDir.mkdirs()
        fakeDstDir.createNewFile()

        // When
        val result = testedFileHandler.moveFiles(fakeSrcDir, fakeDstDir)

        // Then
        assertThat(result).isFalse()
        verify(mockLogHandler).handleLog(
            Log.ERROR,
            BatchFileHandler.ERROR_MOVE_NOT_DIR.format(Locale.US, fakeDstDir.path)
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
        val result = testedFileHandler.moveFiles(fakeSrcDir, fakeDstDir)

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
        val result = testedFileHandler.moveFiles(fakeSrcDir, fakeDstDir)

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
        val result = testedFileHandler.moveFiles(fakeSrcDir, fakeDstDir)

        // Then
        assertThat(result).isTrue()
        assertThat(fakeSrcDir.listFiles()).isEmpty()
        expectedFiles.forEach {
            assertThat(it).exists()
                .hasContent(it.name.reversed())
        }
    }

    // endregion

    // region Creation method

    @Test
    fun `𝕄 create BatchFileHandler 𝕎 create() { without encryption }`() {
        // When
        val fileHandler = BatchFileHandler.create(Logger(mockLogHandler), null)
        // Then
        assertThat(fileHandler)
            .isInstanceOf(BatchFileHandler::class.java)
    }

    @Test
    fun `𝕄 create BatchFileHandler 𝕎 create() { with encryption }`() {
        // When
        val mockEncryption = mock<Encryption>()
        val fileHandler = BatchFileHandler.create(Logger(mockLogHandler), mockEncryption)

        // Then
        assertThat(fileHandler)
            .isInstanceOf(EncryptedFileHandler::class.java)

        (fileHandler as EncryptedFileHandler).let {
            assertThat(it.delegate).isInstanceOf(BatchFileHandler::class.java)
            assertThat(it.encryption).isEqualTo(mockEncryption)
        }
    }

    // endregion
}
