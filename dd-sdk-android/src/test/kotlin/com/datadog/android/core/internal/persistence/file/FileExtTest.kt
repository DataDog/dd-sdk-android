/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.persistence.file

import com.datadog.android.utils.forge.Configurator
import fr.xgouchet.elmyr.annotation.BoolForgery
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.LongForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.junit.jupiter.api.io.TempDir
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.io.File
import java.io.FileFilter
import java.nio.charset.Charset

@Extensions(
    ExtendWith(
        MockitoExtension::class,
        ForgeExtension::class
    )
)
@ForgeConfiguration(value = Configurator::class)
@MockitoSettings(strictness = Strictness.LENIENT)
internal class FileExtTest {

    @TempDir
    lateinit var tempDir: File

    @Mock
    lateinit var mockFile: File

    @StringForgery
    lateinit var fakeFileName: String

    lateinit var fakeFile: File

    @BeforeEach
    fun `set up`() {
        fakeFile = File(tempDir, fakeFileName)
    }

    @Test
    fun `M return result W canWriteSafe()`(
        @BoolForgery result: Boolean
    ) {
        // Given
        whenever(mockFile.canWrite()) doReturn result

        // When
        val canWrite = mockFile.canWriteSafe()

        // Then
        assertThat(canWrite).isEqualTo(result)
    }

    @Test
    fun `M catch exception W canWriteSafe() {SecurityException}`(
        @StringForgery message: String
    ) {
        // Given
        whenever(mockFile.canWrite()) doThrow SecurityException(message)

        // When
        val canWrite = mockFile.canWriteSafe()

        // Then
        assertThat(canWrite).isFalse()
    }

    @Test
    fun `M return result W canReadSafe()`(
        @BoolForgery result: Boolean
    ) {
        // Given
        whenever(mockFile.canRead()) doReturn result

        // When
        val canRead = mockFile.canReadSafe()

        // Then
        assertThat(canRead).isEqualTo(result)
    }

    @Test
    fun `M catch exception W canReadSafe() {SecurityException}`(
        @StringForgery message: String
    ) {
        // Given
        whenever(mockFile.canRead()) doThrow SecurityException(message)

        // When
        val canRead = mockFile.canReadSafe()

        // Then
        assertThat(canRead).isFalse()
    }

    @Test
    fun `M return result W deleteSafe()`(
        @BoolForgery result: Boolean
    ) {
        // Given
        whenever(mockFile.delete()) doReturn result

        // When
        val delete = mockFile.deleteSafe()

        // Then
        assertThat(delete).isEqualTo(result)
    }

    @Test
    fun `M catch exception W deleteSafe() {SecurityException}`(
        @StringForgery message: String
    ) {
        // Given
        whenever(mockFile.delete()) doThrow SecurityException(message)

        // When
        val delete = mockFile.deleteSafe()

        // Then
        assertThat(delete).isFalse()
    }

    @Test
    fun `M return result W existsSafe()`(
        @BoolForgery result: Boolean
    ) {
        // Given
        whenever(mockFile.exists()) doReturn result

        // When
        val exists = mockFile.existsSafe()

        // Then
        assertThat(exists).isEqualTo(result)
    }

    @Test
    fun `M catch exception W existsSafe() {SecurityException}`(
        @StringForgery message: String
    ) {
        // Given
        whenever(mockFile.exists()) doThrow SecurityException(message)

        // When
        val exists = mockFile.existsSafe()

        // Then
        assertThat(exists).isFalse()
    }

    @Test
    fun `M return result W isFileSafe()`(
        @BoolForgery result: Boolean
    ) {
        // Given
        whenever(mockFile.isFile()) doReturn result

        // When
        val isFile = mockFile.isFileSafe()

        // Then
        assertThat(isFile).isEqualTo(result)
    }

    @Test
    fun `M catch exception W isFileSafe() {SecurityException}`(
        @StringForgery message: String
    ) {
        // Given
        whenever(mockFile.isFile()) doThrow SecurityException(message)

        // When
        val isFile = mockFile.isFileSafe()

        // Then
        assertThat(isFile).isFalse()
    }

    @Test
    fun `M return result W isDirectorySafe()`(
        @BoolForgery result: Boolean
    ) {
        // Given
        whenever(mockFile.isDirectory()) doReturn result

        // When
        val isDirectory = mockFile.isDirectorySafe()

        // Then
        assertThat(isDirectory).isEqualTo(result)
    }

    @Test
    fun `M catch exception W isDirectorySafe() {SecurityException}`(
        @StringForgery message: String
    ) {
        // Given
        whenever(mockFile.isDirectory()) doThrow SecurityException(message)

        // When
        val isDirectory = mockFile.isDirectorySafe()

        // Then
        assertThat(isDirectory).isFalse()
    }

    @Test
    fun `M return result W listFilesSafe()`(
        @Forgery result: List<File>
    ) {
        // Given
        whenever(mockFile.listFiles()) doReturn result.toTypedArray()

        // When
        val listFiles = mockFile.listFilesSafe()

        // Then
        assertThat(listFiles).containsAll(result)
    }

    @Test
    fun `M catch exception W listFilesSafe() {SecurityException}`(
        @StringForgery message: String
    ) {
        // Given
        whenever(mockFile.listFiles()) doThrow SecurityException(message)

        // When
        val listFiles = mockFile.listFilesSafe()

        // Then
        assertThat(listFiles).isNull()
    }

    @Test
    fun `M return result W listFilesSafe(filter)`(
        @Forgery result: List<File>
    ) {
        // Given
        val filter: FileFilter = mock()
        whenever(mockFile.listFiles(filter)) doReturn result.toTypedArray()

        // When
        val listFiles = mockFile.listFilesSafe(filter)

        // Then
        assertThat(listFiles).containsAll(result)
    }

    @Test
    fun `M catch exception W listFilesSafe(filter) {SecurityException}`(
        @StringForgery message: String
    ) {
        // Given
        val filter: FileFilter = mock()
        whenever(mockFile.listFiles(filter)) doThrow SecurityException(message)

        // When
        val listFiles = mockFile.listFilesSafe(filter)

        // Then
        assertThat(listFiles).isNull()
    }

    @Test
    fun `M return result W lengthSafe()`(
        @LongForgery result: Long
    ) {
        // Given
        whenever(mockFile.length()) doReturn result

        // When
        val length = mockFile.lengthSafe()

        // Then
        assertThat(length).isEqualTo(result)
    }

    @Test
    fun `M catch exception W lengthSafe() {SecurityException}`(
        @StringForgery message: String
    ) {
        // Given
        whenever(mockFile.length()) doThrow SecurityException(message)

        // When
        val length = mockFile.lengthSafe()

        // Then
        assertThat(length).isEqualTo(0L)
    }

    @Test
    fun `M return result W mkdirsSafe()`(
        @BoolForgery result: Boolean
    ) {
        // Given
        whenever(mockFile.mkdirs()) doReturn result

        // When
        val mkdirs = mockFile.mkdirsSafe()

        // Then
        assertThat(mkdirs).isEqualTo(result)
    }

    @Test
    fun `M catch exception W mkdirsSafe() {SecurityException}`(
        @StringForgery message: String
    ) {
        // Given
        whenever(mockFile.mkdirs()) doThrow SecurityException(message)

        // When
        val mkdirs = mockFile.mkdirsSafe()

        // Then
        assertThat(mkdirs).isFalse()
    }

    @Test
    fun `M return result W renameToSafe()`(
        @BoolForgery result: Boolean,
        @Forgery dest: File
    ) {
        // Given
        whenever(mockFile.renameTo(dest)) doReturn result

        // When
        val renameTo = mockFile.renameToSafe(dest)

        // Then
        assertThat(renameTo).isEqualTo(result)
    }

    @Test
    fun `M catch exception W renameToSafe() {SecurityException}`(
        @StringForgery message: String,
        @Forgery dest: File
    ) {
        // Given
        whenever(mockFile.renameTo(dest)) doThrow SecurityException(message)

        // When
        val renameTo = mockFile.renameToSafe(dest)

        // Then
        assertThat(renameTo).isFalse()
    }

    @Test
    fun `M return result W readTextSafe()`(
        @StringForgery result: String
    ) {
        // Given
        fakeFile.writeText(result)

        // When
        val readText = fakeFile.readTextSafe()

        // Then
        assertThat(readText).isEqualTo(result)
    }

    @Test
    fun `M return result W readTextSafe() {custom charset}`(
        @StringForgery result: String,
        @Forgery charset: Charset
    ) {
        // Given
        fakeFile.writeText(result, charset)

        // When
        val readText = fakeFile.readTextSafe(charset)

        // Then
        assertThat(readText).isEqualTo(result)
    }

    @Test
    fun `M return null W readTextSafe() {file doesn't exist}`() {
        // Given
        whenever(mockFile.exists()) doReturn false

        // When
        val readText = mockFile.readTextSafe()

        // Then
        assertThat(readText).isNull()
    }

    @Test
    fun `M return null W readTextSafe() {file can't be read}`() {
        // Given
        whenever(mockFile.exists()) doReturn true
        whenever(mockFile.canRead()) doReturn false

        // When
        val readText = mockFile.readTextSafe()

        // Then
        assertThat(readText).isNull()
    }

    @Test
    fun `M catch exception W readTextSafe() {SecurityException}`(
        @StringForgery message: String
    ) {
        // Given
        whenever(mockFile.name) doThrow SecurityException(message)

        // When
        val readText = mockFile.readTextSafe()

        // Then
        assertThat(readText).isNull()
    }

    @Test
    fun `M catch exception W readTextSafe() {FileNotFoundException}`() {
        // Given
        whenever(mockFile.exists()) doReturn true
        whenever(mockFile.canRead()) doReturn true
        whenever(mockFile.name) doReturn fakeFileName

        // When
        val readText = mockFile.readTextSafe()

        // Then
        assertThat(readText).isNull()
    }

    @Test
    fun `M return result W readBytesSafe()`(
        @StringForgery result: String
    ) {
        // Given
        fakeFile.writeText(result)

        // When
        val readBytes = fakeFile.readBytesSafe()

        // Then
        assertThat(readBytes).isEqualTo(result.toByteArray())
    }

    @Test
    fun `M return result W readBytesSafe() {custom charset}`(
        @StringForgery result: String,
        @Forgery charset: Charset
    ) {
        // Given
        fakeFile.writeText(result, charset)

        // When
        val readBytes = fakeFile.readBytesSafe()

        // Then
        assertThat(readBytes).isEqualTo(result.toByteArray(charset))
    }

    @Test
    fun `M return null W readBytesSafe() {file doesn't exist}`() {
        // Given
        whenever(mockFile.exists()) doReturn false

        // When
        val readBytes = mockFile.readBytesSafe()

        // Then
        assertThat(readBytes).isNull()
    }

    @Test
    fun `M return null W readBytesSafe() {file can't be read}`() {
        // Given
        whenever(mockFile.exists()) doReturn true
        whenever(mockFile.canRead()) doReturn false

        // When
        val readBytes = mockFile.readBytesSafe()

        // Then
        assertThat(readBytes).isNull()
    }

    @Test
    fun `M catch exception W readBytesSafe() {SecurityException}`(
        @StringForgery message: String
    ) {
        // Given
        whenever(mockFile.name) doThrow SecurityException(message)

        // When
        val readBytes = mockFile.readBytesSafe()

        // Then
        assertThat(readBytes).isNull()
    }

    @Test
    fun `M catch exception W readBytesSafe() {FileNotFoundException}`() {
        // Given
        whenever(mockFile.exists()) doReturn true
        whenever(mockFile.canRead()) doReturn true
        whenever(mockFile.name) doReturn fakeFileName

        // When
        val readBytes = mockFile.readBytesSafe()

        // Then
        assertThat(readBytes).isNull()
    }

    @Test
    fun `M return result W readLinesSafe()`(
        @StringForgery result: List<String>
    ) {
        // Given
        fakeFile.writeText(result.joinToString("\n"))

        // When
        val readLines = fakeFile.readLinesSafe()

        // Then
        assertThat(readLines).isEqualTo(result)
    }

    @Test
    fun `M return result W readLinesSafe() {custom charset}`(
        @StringForgery result: List<String>,
        @Forgery charset: Charset
    ) {
        // Given
        fakeFile.writeText(result.joinToString("\n"), charset)

        // When
        val readLines = fakeFile.readLinesSafe(charset)

        // Then
        assertThat(readLines).isEqualTo(result)
    }

    @Test
    fun `M return null W readLinesSafe() {file doesn't exist}`() {
        // Given
        whenever(mockFile.exists()) doReturn false

        // When
        val readLines = mockFile.readLinesSafe()

        // Then
        assertThat(readLines).isNull()
    }

    @Test
    fun `M return null W readLinesSafe() {file can't be read}`() {
        // Given
        whenever(mockFile.exists()) doReturn true
        whenever(mockFile.canRead()) doReturn false

        // When
        val readLines = mockFile.readLinesSafe()

        // Then
        assertThat(readLines).isNull()
    }

    @Test
    fun `M catch exception W readLinesSafe() {SecurityException}`(
        @StringForgery message: String
    ) {
        // Given
        whenever(mockFile.name) doThrow SecurityException(message)

        // When
        val readLines = mockFile.readLinesSafe()

        // Then
        assertThat(readLines).isNull()
    }

    @Test
    fun `M catch exception W readLinesSafe() {FileNotFoundException}`() {
        // When
        val readLines = fakeFile.readLinesSafe()

        // Then
        assertThat(readLines).isNull()
    }
}
