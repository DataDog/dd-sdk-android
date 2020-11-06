/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.data.file

import android.util.Log
import com.datadog.android.log.internal.logger.LogHandler
import com.datadog.android.utils.mockSdkLogHandler
import com.datadog.android.utils.restoreSdkLogHandler
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeExtension
import java.io.File
import java.lang.NullPointerException
import java.lang.RuntimeException
import org.assertj.core.api.Assertions.assertThat
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
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
internal class FileHandlerTest {

    @TempDir
    lateinit var fakeRootDirectory: File

    lateinit var fakeSourceDirectory: File
    lateinit var fakeDestinationDirectory: File

    lateinit var testedFileHandler: FileHandler

    @StringForgery(regex = "([a-z]+)-([a-z]+)")
    lateinit var fakeSourceDirName: String

    @StringForgery(regex = "([a-z]+)-([a-z]+)")
    lateinit var fakeDestinationDirName: String

    @BeforeEach
    fun `set up`() {
        testedFileHandler = FileHandler()
        fakeSourceDirectory = File(fakeRootDirectory, fakeSourceDirName)
        fakeDestinationDirectory = File(fakeRootDirectory, fakeDestinationDirName)
    }

    // region MoveFiles

    @Test
    fun `M return false W moveFiles { source directory does not exist}`(
        forge: Forge
    ) {
        // WHEN
        val success =
            testedFileHandler.moveFiles(
                fakeSourceDirectory,
                fakeDestinationDirectory
            )

        // THEN
        assertThat(success).isFalse()
    }

    @Test
    fun `M send an error log W moveFiles { source directory does not exist}`(
        forge: Forge
    ) {
        // GIVEN
        val mockLogHandler: LogHandler = mock()
        val originalLogHandler: LogHandler = mockSdkLogHandler(mockLogHandler)

        // WHEN
        testedFileHandler.moveFiles(
            fakeSourceDirectory,
            fakeDestinationDirectory
        )

        // THEN
        verify(mockLogHandler).handleLog(
            Log.ERROR,
            "Unable to move the files. " +
                "There is no directory at this path: [${fakeSourceDirectory.absolutePath}]"
        )
        restoreSdkLogHandler(originalLogHandler)
    }

    @Test
    fun `M return false W moveFiles { source directory is not a directory}`(
        forge: Forge
    ) {
        // GIVEN
        fakeSourceDirectory.createNewFile()

        // WHEN
        val success =
            testedFileHandler.moveFiles(
                fakeSourceDirectory,
                fakeDestinationDirectory
            )

        // THEN
        assertThat(success).isFalse()
    }

    @Test
    fun `M send an error log W moveFiles { source directory is not a directory}`(
        forge: Forge
    ) {
        // GIVEN
        val mockLogHandler: LogHandler = mock()
        val originalLogHandler: LogHandler = mockSdkLogHandler(mockLogHandler)
        fakeSourceDirectory.createNewFile()

        // WHEN
        testedFileHandler.moveFiles(
            fakeSourceDirectory,
            fakeDestinationDirectory
        )

        // THEN
        verify(mockLogHandler).handleLog(
            Log.ERROR,
            "Unable to move the files." +
                "[${fakeSourceDirectory.absolutePath}] is not a directory."
        )
        restoreSdkLogHandler(originalLogHandler)
    }

    @Test
    fun `M move all the files W moveFiles`(forge: Forge) {
        // GIVEN
        fakeSourceDirectory.mkdirs()
        fakeDestinationDirectory.mkdirs()
        val files = forge.aList {
            File(fakeSourceDirectory, forge.anAlphabeticalString())
        }
        files.forEach { it.createNewFile() }

        // WHEN
        val success = testedFileHandler.moveFiles(
            fakeSourceDirectory,
            fakeDestinationDirectory
        )

        // THEN
        assertThat(success).isTrue()
        assertThat(fakeSourceDirectory.listFiles()).isEmpty()
        val destinationDirectoryFiles = fakeDestinationDirectory.listFiles()
        assertThat(destinationDirectoryFiles?.map { it.name })
            .containsOnly(*(files.map { it.name }.toTypedArray()))
    }

    @Test
    fun `M return true W moveFiles { sourceDirectory is empty }`(forge: Forge) {
        // GIVEN
        fakeSourceDirectory.mkdirs()
        fakeDestinationDirectory.mkdirs()

        // WHEN
        val success = testedFileHandler.moveFiles(
            fakeSourceDirectory,
            fakeDestinationDirectory
        )

        // THEN
        assertThat(success).isTrue()
        val destinationDirectoryFiles = fakeDestinationDirectory.listFiles()
        assertThat(destinationDirectoryFiles).isEmpty()
    }

    @Test
    fun `M return false W moveFiles { renameFile fails with NPE }`(forge: Forge) {
        // GIVEN
        val fakeNpe = NullPointerException(forge.anAlphabeticalString())
        val mockedFilesPair = mockedFilesWithSpecialMock(forge) {
            whenever(it.renameTo(any())).doThrow(fakeNpe)
        }
        val mockLogHandler: LogHandler = mock()
        val originalLogHandler: LogHandler = mockSdkLogHandler(mockLogHandler)
        val brokenFile = mockedFilesPair.second
        val mockedSourceDirectory: File = mock {
            whenever(it.isDirectory).thenReturn(true)
            whenever(it.exists()).thenReturn(true)
            whenever(it.listFiles()).thenReturn(mockedFilesPair.first)
        }

        // WHEN
        val success = testedFileHandler.moveFiles(
            mockedSourceDirectory,
            fakeDestinationDirectory
        )

        // THEN
        assertThat(success).isFalse()
        verify(mockLogHandler).handleLog(
            Log.ERROR,
            "Unable to move file: [${brokenFile.absolutePath}]" +
                " to new file: " +
                "[${fakeDestinationDirectory.absolutePath}/${brokenFile.name}]",
            fakeNpe
        )
        restoreSdkLogHandler(originalLogHandler)
    }

    @Test
    fun `M return false W moveFiles { renameFile fails with SecurityException }`(forge: Forge) {
        // GIVEN
        val fakeSecException = SecurityException(forge.anAlphabeticalString())
        val mockedFilesPair = mockedFilesWithSpecialMock(forge) {
            whenever(it.renameTo(any())).doThrow(fakeSecException)
        }
        val mockLogHandler: LogHandler = mock()
        val originalLogHandler: LogHandler = mockSdkLogHandler(mockLogHandler)
        val brokenFile = mockedFilesPair.second
        val mockedSourceDirectory: File = mock {
            whenever(it.isDirectory).thenReturn(true)
            whenever(it.exists()).thenReturn(true)
            whenever(it.listFiles()).thenReturn(mockedFilesPair.first)
        }

        // WHEN
        val success = testedFileHandler.moveFiles(
            mockedSourceDirectory,
            fakeDestinationDirectory
        )

        // THEN
        assertThat(success).isFalse()
        verify(mockLogHandler).handleLog(
            Log.ERROR,
            "Unable to move file: [${brokenFile.absolutePath}]" +
                " to new file: " +
                "[${fakeDestinationDirectory.absolutePath}/${brokenFile.name}]",
            fakeSecException
        )
        restoreSdkLogHandler(originalLogHandler)
    }

    // endregion

    // region CleanDirectory

    @Test
    fun `M clear the file W required`() {
        // GIVEN
        val mockDirectory: File = mock {
            whenever(it.deleteRecursively()).thenReturn(true)
        }

        // WHEN
        val success = testedFileHandler.clearFile(mockDirectory)

        // THEN
        assertThat(success).isTrue()
    }

    @Test
    fun `M return false W clearFile { throws Exception }`(forge: Forge) {
        // GIVEN
        val fakeException = RuntimeException(forge.anAlphabeticalString())
        val mockFile: File = mock {
            whenever(it.deleteRecursively()).thenThrow(fakeException)
        }

        // WHEN
        val success = testedFileHandler.clearFile(mockFile)

        // THEN
        assertThat(success).isFalse()
    }

    @Test
    fun `M send an error log W clearFile { throws Exception }`(forge: Forge) {
        // GIVEN
        val mockLogHandler: LogHandler = mock()
        val originalLogHandler: LogHandler = mockSdkLogHandler(mockLogHandler)
        val fakeException = RuntimeException(forge.anAlphabeticalString())
        val mockFile: File = mock {
            whenever(it.deleteRecursively()).thenThrow(fakeException)
            whenever(it.absolutePath).thenReturn(forge.anAlphabeticalString())
        }

        // WHEN
        testedFileHandler.clearFile(mockFile)

        // THEN
        verify(mockLogHandler).handleLog(
            Log.ERROR,
            "Unable to clear the file at [${mockFile.absolutePath}]",
            fakeException
        )
        restoreSdkLogHandler(originalLogHandler)
    }

    // endregion

    // region Internal

    private fun mockedFilesWithSpecialMock(
        forge: Forge,
        specialMockBlock: (File) -> Unit
    ): Pair<Array<File>, File> {
        val filesLength = forge.anInt(1, 10)
        val specialMockIndex = forge.anInt(0, filesLength)
        val mockedFiles = Array<File>(filesLength) { index ->
            mock {
                if (index == specialMockIndex) {
                    specialMockBlock(it)
                } else {
                    whenever(it.renameTo(any())).thenReturn(true)
                }
                val fakeFileName = forge.anAlphabeticalString()
                whenever(it.name).thenReturn(fakeFileName)
                whenever(it.absolutePath)
                    .thenReturn(forge.aStringMatching("([a-z]+)/([a-z]+)/") + fakeFileName)
            }
        }
        return mockedFiles to mockedFiles[specialMockIndex]
    }

    // endregion
}
