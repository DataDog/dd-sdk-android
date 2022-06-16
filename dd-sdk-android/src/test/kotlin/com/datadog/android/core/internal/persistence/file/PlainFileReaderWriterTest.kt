/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.persistence.file

import com.datadog.android.log.Logger
import com.datadog.android.log.internal.utils.ERROR_WITH_TELEMETRY_LEVEL
import com.datadog.android.utils.config.LoggerTestConfiguration
import com.datadog.android.utils.forge.Configurator
import com.datadog.tools.unit.annotations.TestConfigurationsProvider
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import com.datadog.tools.unit.extensions.config.TestConfiguration
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.isNull
import com.nhaarman.mockitokotlin2.verify
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.BoolForgery
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
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class),
    ExtendWith(TestConfigurationExtension::class)
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

    private lateinit var fakeSrcDir: File
    private lateinit var fakeDstDir: File

    @BeforeEach
    fun `set up`() {
        fakeSrcDir = File(fakeRootDirectory, fakeSrcDirName)
        fakeDstDir = File(fakeRootDirectory, fakeDstDirName)
        testedFileReaderWriter = PlainFileReaderWriter(Logger(logger.mockSdkLogHandler))
    }

    // region writeData

    @Test
    fun `𝕄 write data in empty file 𝕎 writeData() {append=false}`(
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
    fun `𝕄 write data in empty file  𝕎 writeData() {append=true}`(
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
    fun `𝕄 overwrite data in non empty file 𝕎 writeData() {append=false}`(
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
    fun `𝕄 append data in non empty file 𝕎 writeData() {append=true}`(
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
    fun `𝕄 return false and warn 𝕎 writeData() {parent dir does not exist}`(
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
        verify(logger.mockSdkLogHandler).handleLog(
            eq(ERROR_WITH_TELEMETRY_LEVEL),
            eq(PlainFileReaderWriter.ERROR_WRITE.format(Locale.US, file.path)),
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
        verify(logger.mockSdkLogHandler).handleLog(
            eq(ERROR_WITH_TELEMETRY_LEVEL),
            eq(PlainFileReaderWriter.ERROR_WRITE.format(Locale.US, file.path)),
            any(),
            eq(emptyMap()),
            eq(emptySet()),
            isNull()
        )
    }

    // endregion

    // region readData

    @Test
    fun `𝕄 return empty array and warn 𝕎 readData() {file does not exist}`(
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
        verify(logger.mockSdkLogHandler).handleLog(
            eq(ERROR_WITH_TELEMETRY_LEVEL),
            eq(PlainFileReaderWriter.ERROR_READ.format(Locale.US, file.path)),
            isNull(),
            eq(emptyMap()),
            eq(emptySet()),
            isNull()
        )
    }

    @Test
    fun `𝕄 return empty array and warn 𝕎 readData() {file is not file}`(
        @StringForgery fileName: String
    ) {
        // Given
        val file = File(fakeRootDirectory, fileName)
        assumeFalse(file.exists())

        // When
        val result = testedFileReaderWriter.readData(file)

        // Then
        assertThat(result).isEmpty()
        verify(logger.mockSdkLogHandler).handleLog(
            eq(ERROR_WITH_TELEMETRY_LEVEL),
            eq(PlainFileReaderWriter.ERROR_READ.format(Locale.US, file.path)),
            isNull(),
            eq(emptyMap()),
            eq(emptySet()),
            isNull()
        )
    }

    @Test
    fun `𝕄 return file content 𝕎 readData() { single event }`(
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
    fun `𝕄 return file content 𝕎 readData() { multiple events }`(
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
    fun `𝕄 return file content 𝕎 writeData + readData() { append = false }`(
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
    fun `𝕄 return file content 𝕎 writeData + readData() { append = true }`(
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

    companion object {
        val logger = LoggerTestConfiguration()

        @TestConfigurationsProvider
        @JvmStatic
        fun getTestConfigurations(): List<TestConfiguration> {
            return listOf(logger)
        }
    }
}
