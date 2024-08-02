/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.persistence.file

import com.datadog.android.api.InternalLogger
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.utils.verifyLog
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.BoolForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
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
import java.io.File
import java.io.IOException
import java.util.Locale

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@ForgeConfiguration(Configurator::class)
@MockitoSettings(strictness = Strictness.LENIENT)
internal class PlainFileReaderWriterTest {

    private lateinit var testedFileReaderWriter: PlainFileReaderWriter

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
    fun `set up`() {
        fakeSrcDir = File(fakeRootDirectory, fakeSrcDirName)
        fakeDstDir = File(fakeRootDirectory, fakeDstDirName)
        testedFileReaderWriter = PlainFileReaderWriter(mockInternalLogger)
    }

    // region writeData

    @Test
    fun `M write data in empty file W writeData() {append=false}`(
        @StringForgery fileName: String,
        @StringForgery content: String
    ) {
        // Given
        val file = File(fakeRootDirectory, fileName)
        file.createNewFile()
        val contentBytes = content.toByteArray()

        // When
        val result = testedFileReaderWriter.writeData(
            file,
            contentBytes,
            append = false
        )

        // Then
        assertThat(result).isTrue()
        assertThat(file).exists().hasBinaryContent(contentBytes)
    }

    @Test
    fun `M write data in empty file  W writeData() {append=true}`(
        @StringForgery fileName: String,
        @StringForgery content: String
    ) {
        // Given
        val file = File(fakeRootDirectory, fileName)
        file.createNewFile()
        val contentBytes = content.toByteArray()

        // When
        val result = testedFileReaderWriter.writeData(
            file,
            contentBytes,
            append = false
        )

        // Then
        assertThat(result).isTrue()
        assertThat(file).exists().hasBinaryContent(contentBytes)
    }

    @Test
    fun `M overwrite data in non empty file W writeData() {append=false}`(
        @StringForgery fileName: String,
        @StringForgery previousContent: String,
        @StringForgery content: String
    ) {
        // Given
        val file = File(fakeRootDirectory, fileName)
        file.writeText(previousContent)
        val contentBytes = content.toByteArray()

        // When
        val result = testedFileReaderWriter.writeData(
            file,
            contentBytes,
            append = false
        )

        // Then
        assertThat(result).isTrue()
        assertThat(file).exists().hasBinaryContent(contentBytes)
    }

    @Test
    fun `M append data in non empty file W writeData() {append=true}`(
        @StringForgery fileName: String,
        @StringForgery previousContent: String,
        @StringForgery content: String
    ) {
        // Given
        val file = File(fakeRootDirectory, fileName)
        val previousData = previousContent.toByteArray()
        file.writeBytes(previousData)
        val contentBytes = content.toByteArray()

        // When
        val result = testedFileReaderWriter.writeData(
            file,
            contentBytes,
            append = true
        )

        // Then
        assertThat(result).isTrue()
        assertThat(file).exists()
            .hasBinaryContent(
                previousData + contentBytes
            )
    }

    @Test
    fun `M return false and warn W writeData() {parent dir does not exist}`(
        @StringForgery fileName: String,
        @StringForgery content: String,
        @BoolForgery append: Boolean
    ) {
        // Given
        assumeFalse(fakeSrcDir.exists())
        val file = File(fakeSrcDir, fileName)

        // When
        val result = testedFileReaderWriter.writeData(
            file,
            content.toByteArray(),
            append = append
        )

        // Then
        assertThat(result).isFalse()
        assertThat(file).doesNotExist()
        mockInternalLogger.verifyLog(
            InternalLogger.Level.ERROR,
            listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
            PlainFileReaderWriter.ERROR_WRITE.format(Locale.US, file.path),
            IOException::class.java
        )
    }

    @Test
    fun `M return false and warn W writeData() {file is not file}`(
        @StringForgery fileName: String,
        @StringForgery content: String,
        @BoolForgery append: Boolean
    ) {
        // Given
        val file = File(fakeRootDirectory, fileName)
        file.mkdirs()

        // When
        val result = testedFileReaderWriter.writeData(
            file,
            content.toByteArray(),
            append = append
        )

        // Then
        assertThat(result).isFalse()
        mockInternalLogger.verifyLog(
            InternalLogger.Level.ERROR,
            listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
            PlainFileReaderWriter.ERROR_WRITE.format(Locale.US, file.path),
            IOException::class.java
        )
    }

    // endregion

    // region readData

    @Test
    fun `M return empty array and warn W readData() {file does not exist}`(
        @StringForgery fileName: String
    ) {
        // Given
        val file = File(fakeRootDirectory, fileName)
        assumeFalse(file.exists())

        // When
        val result = testedFileReaderWriter.readData(file)

        // Then
        assertThat(result).isEmpty()
        assertThat(file).doesNotExist()
        mockInternalLogger.verifyLog(
            InternalLogger.Level.ERROR,
            listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
            PlainFileReaderWriter.ERROR_READ.format(Locale.US, file.path),
            null
        )
    }

    @Test
    fun `M return empty array and warn W readData() {file is not file}`(
        @StringForgery fileName: String
    ) {
        // Given
        val file = File(fakeRootDirectory, fileName)
        assumeFalse(file.exists())

        // When
        val result = testedFileReaderWriter.readData(file)

        // Then
        assertThat(result).isEmpty()
        mockInternalLogger.verifyLog(
            InternalLogger.Level.ERROR,
            listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
            PlainFileReaderWriter.ERROR_READ.format(Locale.US, file.path),
            null
        )
    }

    @Test
    fun `M return file content W readData() { single event }`(
        @StringForgery fileName: String,
        @StringForgery event: String
    ) {
        // Given
        val file = File(fakeRootDirectory, fileName)
        val eventBytes = event.toByteArray()
        file.writeBytes(eventBytes)

        // When
        val result = testedFileReaderWriter.readData(file)

        // Then
        assertThat(result).isEqualTo(eventBytes)
    }

    @Test
    fun `M return file content W readData() { multiple events }`(
        @StringForgery fileName: String,
        forge: Forge
    ) {
        // Given
        val file = File(fakeRootDirectory, fileName)
        val events = forge.aList {
            aString().toByteArray()
        }
        val data = events.reduce { acc, bytes -> acc + bytes }
        file.writeBytes(data)

        // When
        val result = testedFileReaderWriter.readData(file)

        // Then
        assertThat(result).isEqualTo(data)
    }

    // endregion

    // region writeData + readData

    @Test
    fun `M return file content W writeData + readData() { append = false }`(
        @StringForgery fileName: String,
        @StringForgery content: String
    ) {
        // Given
        val file = File(fakeRootDirectory, fileName)

        // When
        val writeResult = testedFileReaderWriter.writeData(file, content.toByteArray(), false)
        val readResult = testedFileReaderWriter.readData(file)

        // Then
        assertThat(writeResult).isTrue()
        assertThat(readResult).isEqualTo(content.toByteArray())
    }

    @Test
    fun `M return file content W writeData + readData() { append = true }`(
        @StringForgery fileName: String,
        forge: Forge
    ) {
        // Given
        val file = File(fakeRootDirectory, fileName)

        val data = forge.aList {
            aString().toByteArray()
        }

        // When
        var writeResult = true
        data.forEach {
            writeResult = writeResult && testedFileReaderWriter.writeData(
                file,
                it,
                true
            )
        }
        val readResult = testedFileReaderWriter.readData(file)

        // Then
        assertThat(writeResult).isTrue()
        assertThat(readResult).isEqualTo(data.reduce { acc, bytes -> acc + bytes })
    }

    // endregion
}
