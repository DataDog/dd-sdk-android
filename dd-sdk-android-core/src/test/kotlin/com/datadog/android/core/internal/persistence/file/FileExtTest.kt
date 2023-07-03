/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.persistence.file

import com.datadog.android.api.InternalLogger
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

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @StringForgery
    lateinit var fakeFileName: String

    lateinit var fakeFile: File

    @BeforeEach
    fun `set up`() {
        fakeFile = File(tempDir, fakeFileName)
    }

    @Test
    fun `M return result W canWriteSafe(mockInternalLogger)`(
        @BoolForgery result: Boolean
    ) {
        // Given
        whenever(mockFile.canWrite()) doReturn result

        // When
        val canWrite = mockFile.canWriteSafe(mockInternalLogger)

        // Then
        assertThat(canWrite).isEqualTo(result)
    }

    @Test
    fun `M catch exception W canWriteSafe(mockInternalLogger) {SecurityException}`(
        @StringForgery message: String
    ) {
        // Given
        whenever(mockFile.canWrite()) doThrow SecurityException(message)

        // When
        val canWrite = mockFile.canWriteSafe(mockInternalLogger)

        // Then
        assertThat(canWrite).isFalse()
    }

    @Test
    fun `M return result W canReadSafe(mockInternalLogger)`(
        @BoolForgery result: Boolean
    ) {
        // Given
        whenever(mockFile.canRead()) doReturn result

        // When
        val canRead = mockFile.canReadSafe(mockInternalLogger)

        // Then
        assertThat(canRead).isEqualTo(result)
    }

    @Test
    fun `M catch exception W canReadSafe(mockInternalLogger) {SecurityException}`(
        @StringForgery message: String
    ) {
        // Given
        whenever(mockFile.canRead()) doThrow SecurityException(message)

        // When
        val canRead = mockFile.canReadSafe(mockInternalLogger)

        // Then
        assertThat(canRead).isFalse()
    }

    @Test
    fun `M return result W deleteSafe(mockInternalLogger)`(
        @BoolForgery result: Boolean
    ) {
        // Given
        whenever(mockFile.delete()) doReturn result

        // When
        val delete = mockFile.deleteSafe(mockInternalLogger)

        // Then
        assertThat(delete).isEqualTo(result)
    }

    @Test
    fun `M catch exception W deleteSafe(mockInternalLogger) {SecurityException}`(
        @StringForgery message: String
    ) {
        // Given
        whenever(mockFile.delete()) doThrow SecurityException(message)

        // When
        val delete = mockFile.deleteSafe(mockInternalLogger)

        // Then
        assertThat(delete).isFalse()
    }

    @Test
    fun `M return result W existsSafe(mockInternalLogger)`(
        @BoolForgery result: Boolean
    ) {
        // Given
        whenever(mockFile.exists()) doReturn result

        // When
        val exists = mockFile.existsSafe(mockInternalLogger)

        // Then
        assertThat(exists).isEqualTo(result)
    }

    @Test
    fun `M catch exception W existsSafe(mockInternalLogger) {SecurityException}`(
        @StringForgery message: String
    ) {
        // Given
        whenever(mockFile.exists()) doThrow SecurityException(message)

        // When
        val exists = mockFile.existsSafe(mockInternalLogger)

        // Then
        assertThat(exists).isFalse()
    }

    @Test
    fun `M return result W isFileSafe(mockInternalLogger)`(
        @BoolForgery result: Boolean
    ) {
        // Given
        whenever(mockFile.isFile()) doReturn result

        // When
        val isFile = mockFile.isFileSafe(mockInternalLogger)

        // Then
        assertThat(isFile).isEqualTo(result)
    }

    @Test
    fun `M catch exception W isFileSafe(mockInternalLogger) {SecurityException}`(
        @StringForgery message: String
    ) {
        // Given
        whenever(mockFile.isFile()) doThrow SecurityException(message)

        // When
        val isFile = mockFile.isFileSafe(mockInternalLogger)

        // Then
        assertThat(isFile).isFalse()
    }

    @Test
    fun `M return result W isDirectorySafe(mockInternalLogger)`(
        @BoolForgery result: Boolean
    ) {
        // Given
        whenever(mockFile.isDirectory()) doReturn result

        // When
        val isDirectory = mockFile.isDirectorySafe(mockInternalLogger)

        // Then
        assertThat(isDirectory).isEqualTo(result)
    }

    @Test
    fun `M catch exception W isDirectorySafe(mockInternalLogger) {SecurityException}`(
        @StringForgery message: String
    ) {
        // Given
        whenever(mockFile.isDirectory()) doThrow SecurityException(message)

        // When
        val isDirectory = mockFile.isDirectorySafe(mockInternalLogger)

        // Then
        assertThat(isDirectory).isFalse()
    }

    @Test
    fun `M return result W listFilesSafe(mockInternalLogger)`(
        @Forgery result: List<File>
    ) {
        // Given
        whenever(mockFile.listFiles()) doReturn result.toTypedArray()

        // When
        val listFiles = mockFile.listFilesSafe(mockInternalLogger)

        // Then
        assertThat(listFiles).containsAll(result)
    }

    @Test
    fun `M catch exception W listFilesSafe(mockInternalLogger) {SecurityException}`(
        @StringForgery message: String
    ) {
        // Given
        whenever(mockFile.listFiles()) doThrow SecurityException(message)

        // When
        val listFiles = mockFile.listFilesSafe(mockInternalLogger)

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
        val listFiles = mockFile.listFilesSafe(filter, mockInternalLogger)

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
        val listFiles = mockFile.listFilesSafe(filter, mockInternalLogger)

        // Then
        assertThat(listFiles).isNull()
    }

    @Test
    fun `M return result W lengthSafe(mockInternalLogger)`(
        @LongForgery result: Long
    ) {
        // Given
        whenever(mockFile.length()) doReturn result

        // When
        val length = mockFile.lengthSafe(mockInternalLogger)

        // Then
        assertThat(length).isEqualTo(result)
    }

    @Test
    fun `M catch exception W lengthSafe(mockInternalLogger) {SecurityException}`(
        @StringForgery message: String
    ) {
        // Given
        whenever(mockFile.length()) doThrow SecurityException(message)

        // When
        val length = mockFile.lengthSafe(mockInternalLogger)

        // Then
        assertThat(length).isEqualTo(0L)
    }

    @Test
    fun `M return result W mkdirsSafe(mockInternalLogger)`(
        @BoolForgery result: Boolean
    ) {
        // Given
        whenever(mockFile.mkdirs()) doReturn result

        // When
        val mkdirs = mockFile.mkdirsSafe(mockInternalLogger)

        // Then
        assertThat(mkdirs).isEqualTo(result)
    }

    @Test
    fun `M catch exception W mkdirsSafe(mockInternalLogger) {SecurityException}`(
        @StringForgery message: String
    ) {
        // Given
        whenever(mockFile.mkdirs()) doThrow SecurityException(message)

        // When
        val mkdirs = mockFile.mkdirsSafe(mockInternalLogger)

        // Then
        assertThat(mkdirs).isFalse()
    }

    @Test
    fun `M return result W renameToSafe(mockInternalLogger)`(
        @BoolForgery result: Boolean,
        @Forgery dest: File
    ) {
        // Given
        whenever(mockFile.renameTo(dest)) doReturn result

        // When
        val renameTo = mockFile.renameToSafe(dest, mockInternalLogger)

        // Then
        assertThat(renameTo).isEqualTo(result)
    }

    @Test
    fun `M catch exception W renameToSafe(mockInternalLogger) {SecurityException}`(
        @StringForgery message: String,
        @Forgery dest: File
    ) {
        // Given
        whenever(mockFile.renameTo(dest)) doThrow SecurityException(message)

        // When
        val renameTo = mockFile.renameToSafe(dest, mockInternalLogger)

        // Then
        assertThat(renameTo).isFalse()
    }

    @Test
    fun `M return result W readTextSafe(mockInternalLogger)`(
        @StringForgery result: String
    ) {
        // Given
        fakeFile.writeText(result)

        // When
        val readText = fakeFile.readTextSafe(internalLogger = mockInternalLogger)

        // Then
        assertThat(readText).isEqualTo(result)
    }

    @Test
    fun `M return result W readTextSafe(mockInternalLogger) {custom charset}`(
        @StringForgery result: String,
        @Forgery charset: Charset
    ) {
        // Given
        fakeFile.writeText(result, charset)

        // When
        val readText = fakeFile.readTextSafe(charset, mockInternalLogger)

        // Then
        assertThat(readText).isEqualTo(result)
    }

    @Test
    fun `M return null W readTextSafe(mockInternalLogger) {file doesn't exist}`() {
        // Given
        whenever(mockFile.exists()) doReturn false

        // When
        val readText = mockFile.readTextSafe(internalLogger = mockInternalLogger)

        // Then
        assertThat(readText).isNull()
    }

    @Test
    fun `M return null W readTextSafe(mockInternalLogger) {file can't be read}`() {
        // Given
        whenever(mockFile.exists()) doReturn true
        whenever(mockFile.canRead()) doReturn false

        // When
        val readText = mockFile.readTextSafe(internalLogger = mockInternalLogger)

        // Then
        assertThat(readText).isNull()
    }

    @Test
    fun `M catch exception W readTextSafe(mockInternalLogger) {SecurityException}`(
        @StringForgery message: String
    ) {
        // Given
        whenever(mockFile.name) doThrow SecurityException(message)

        // When
        val readText = mockFile.readTextSafe(internalLogger = mockInternalLogger)

        // Then
        assertThat(readText).isNull()
    }

    @Test
    fun `M catch exception W readTextSafe(mockInternalLogger) {FileNotFoundException}`() {
        // Given
        whenever(mockFile.exists()) doReturn true
        whenever(mockFile.canRead()) doReturn true
        whenever(mockFile.name) doReturn fakeFileName

        // When
        val readText = mockFile.readTextSafe(internalLogger = mockInternalLogger)

        // Then
        assertThat(readText).isNull()
    }

    @Test
    fun `M return result W readBytesSafe(mockInternalLogger)`(
        @StringForgery result: String
    ) {
        // Given
        fakeFile.writeText(result)

        // When
        val readBytes = fakeFile.readBytesSafe(mockInternalLogger)

        // Then
        assertThat(readBytes).isEqualTo(result.toByteArray())
    }

    @Test
    fun `M return result W readBytesSafe(mockInternalLogger) {custom charset}`(
        @StringForgery result: String,
        @Forgery charset: Charset
    ) {
        // Given
        fakeFile.writeText(result, charset)

        // When
        val readBytes = fakeFile.readBytesSafe(mockInternalLogger)

        // Then
        assertThat(readBytes).isEqualTo(result.toByteArray(charset))
    }

    @Test
    fun `M return null W readBytesSafe(mockInternalLogger) {file doesn't exist}`() {
        // Given
        whenever(mockFile.exists()) doReturn false

        // When
        val readBytes = mockFile.readBytesSafe(mockInternalLogger)

        // Then
        assertThat(readBytes).isNull()
    }

    @Test
    fun `M return null W readBytesSafe(mockInternalLogger) {file can't be read}`() {
        // Given
        whenever(mockFile.exists()) doReturn true
        whenever(mockFile.canRead()) doReturn false

        // When
        val readBytes = mockFile.readBytesSafe(mockInternalLogger)

        // Then
        assertThat(readBytes).isNull()
    }

    @Test
    fun `M catch exception W readBytesSafe(mockInternalLogger) {SecurityException}`(
        @StringForgery message: String
    ) {
        // Given
        whenever(mockFile.name) doThrow SecurityException(message)

        // When
        val readBytes = mockFile.readBytesSafe(mockInternalLogger)

        // Then
        assertThat(readBytes).isNull()
    }

    @Test
    fun `M catch exception W readBytesSafe(mockInternalLogger) {FileNotFoundException}`() {
        // Given
        whenever(mockFile.exists()) doReturn true
        whenever(mockFile.canRead()) doReturn true
        whenever(mockFile.name) doReturn fakeFileName

        // When
        val readBytes = mockFile.readBytesSafe(mockInternalLogger)

        // Then
        assertThat(readBytes).isNull()
    }

    @Test
    fun `M return result W readLinesSafe(mockInternalLogger)`(
        @StringForgery result: List<String>
    ) {
        // Given
        fakeFile.writeText(result.joinToString("\n"))

        // When
        val readLines = fakeFile.readLinesSafe(internalLogger = mockInternalLogger)

        // Then
        assertThat(readLines).isEqualTo(result)
    }

    @Test
    fun `M return result W readLinesSafe(mockInternalLogger) {custom charset}`(
        @StringForgery result: List<String>,
        @Forgery charset: Charset
    ) {
        // Given
        fakeFile.writeText(result.joinToString("\n"), charset)

        // When
        val readLines = fakeFile.readLinesSafe(charset, mockInternalLogger)

        // Then
        assertThat(readLines).isEqualTo(result)
    }

    @Test
    fun `M return null W readLinesSafe(mockInternalLogger) {file doesn't exist}`() {
        // Given
        whenever(mockFile.exists()) doReturn false

        // When
        val readLines = mockFile.readLinesSafe(internalLogger = mockInternalLogger)

        // Then
        assertThat(readLines).isNull()
    }

    @Test
    fun `M return null W readLinesSafe(mockInternalLogger) {file can't be read}`() {
        // Given
        whenever(mockFile.exists()) doReturn true
        whenever(mockFile.canRead()) doReturn false

        // When
        val readLines = mockFile.readLinesSafe(internalLogger = mockInternalLogger)

        // Then
        assertThat(readLines).isNull()
    }

    @Test
    fun `M catch exception W readLinesSafe(mockInternalLogger) {SecurityException}`(
        @StringForgery message: String
    ) {
        // Given
        whenever(mockFile.name) doThrow SecurityException(message)

        // When
        val readLines = mockFile.readLinesSafe(internalLogger = mockInternalLogger)

        // Then
        assertThat(readLines).isNull()
    }

    @Test
    fun `M catch exception W readLinesSafe(mockInternalLogger) {FileNotFoundException}`() {
        // When
        val readLines = fakeFile.readLinesSafe(internalLogger = mockInternalLogger)

        // Then
        assertThat(readLines).isNull()
    }
}
