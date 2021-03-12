/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.data.file

import android.os.Build
import com.datadog.android.core.internal.data.Orchestrator
import com.datadog.android.utils.forge.Configurator
import com.datadog.tools.unit.annotations.TestTargetApi
import com.datadog.tools.unit.extensions.ApiLevelExtension
import com.datadog.tools.unit.getFieldValue
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.inOrder
import com.nhaarman.mockitokotlin2.verifyNoMoreInteractions
import com.nhaarman.mockitokotlin2.whenever
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.assertj.core.api.Assertions.assertThat
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
    ExtendWith(ForgeExtension::class),
    ExtendWith(ApiLevelExtension::class)
)
@ForgeConfiguration(Configurator::class)
@MockitoSettings(strictness = Strictness.LENIENT)
internal class FileReaderTest {

    lateinit var testedReader: FileReader

    @TempDir
    lateinit var tempRootDir: File

    @Mock
    lateinit var mockOrchestrator: Orchestrator

    lateinit var fakePrefix: String
    lateinit var fakeSuffix: String

    @BeforeEach
    fun `set up`(forge: Forge) {
        fakePrefix = forge.anAsciiString(size = forge.anInt(min = 2, max = 8))
        fakeSuffix = forge.anAsciiString(size = forge.anInt(min = 2, max = 8))
        testedReader = FileReader(mockOrchestrator, tempRootDir, fakePrefix, fakeSuffix)
    }

    @Test
    fun `doesn't ask for the same batch twice in a row`(
        forge: Forge
    ) {
        val fileName = forge.anAlphabeticalString()
        val file = forgeTempFile(fileName)
        val data = forge.anAlphabeticalString()
        file.writeText(data)
        whenever(mockOrchestrator.getReadableFile(any())) doReturn null
        whenever(mockOrchestrator.getReadableFile(emptySet())) doReturn file

        val firstBatch = testedReader.readNextBatch()
        val secondBatch = testedReader.readNextBatch()
        checkNotNull(firstBatch)

        assertThat(String(firstBatch.data)).isEqualTo("$fakePrefix$data$fakeSuffix")
        assertThat(secondBatch).isNull()
        inOrder(mockOrchestrator) {
            verify(mockOrchestrator).getReadableFile(emptySet())
            verify(mockOrchestrator).getReadableFile(setOf(firstBatch.id))
            verifyNoMoreInteractions(mockOrchestrator)
        }
    }

    @Test
    fun `reads a batch that was previously read then released`(
        forge: Forge
    ) {
        val fileName = forge.anAlphabeticalString()
        val file = forgeTempFile(fileName)
        val data = forge.anAlphabeticalString()
        file.writeText(data)
        whenever(mockOrchestrator.getReadableFile(mutableSetOf())) doReturn file
        whenever(mockOrchestrator.getReadableFile(mutableSetOf(fileName))) doReturn null

        val firstBatch = testedReader.readNextBatch()
        val secondBatch = testedReader.readNextBatch()
        checkNotNull(firstBatch)
        testedReader.releaseBatch(firstBatch.id)
        val thirdBatch = testedReader.readNextBatch()
        checkNotNull(thirdBatch)

        assertThat(String(firstBatch.data)).isEqualTo("$fakePrefix$data$fakeSuffix")
        assertThat(secondBatch).isNull()
        assertThat(String(thirdBatch.data)).isEqualTo("$fakePrefix$data$fakeSuffix")
        inOrder(mockOrchestrator) {
            verify(mockOrchestrator).getReadableFile(emptySet())
            verify(mockOrchestrator).getReadableFile(setOf(firstBatch.id))
            verify(mockOrchestrator).getReadableFile(emptySet())
            verifyNoMoreInteractions(mockOrchestrator)
        }
    }

    @Test
    @TestTargetApi(Build.VERSION_CODES.O)
    fun `returns a valid batch if file exists and valid`(
        forge: Forge
    ) {
        // Given
        val fileName = forge.anAlphabeticalString()
        val file = forgeTempFile(fileName)
        val data = forge.anAlphabeticalString()
        file.writeText(data)
        whenever(mockOrchestrator.getReadableFile(any())).thenReturn(file)

        // When
        val nextBatch = testedReader.readNextBatch()
        checkNotNull(nextBatch)

        // Then
        val persistedData = String(nextBatch.data)
        assertThat(persistedData).isEqualTo("$fakePrefix$data$fakeSuffix")
    }

    @Test
    fun `returns a null batch if the file was already sent`() {
        // Given
        whenever(mockOrchestrator.getReadableFile(any())).doReturn(null)

        // When
        val nextBatch = testedReader.readNextBatch()

        // Then
        assertThat(nextBatch).isNull()
    }

    @Test
    fun `returns a null batch if the data is corrupted`() {
        // Given
        whenever(mockOrchestrator.getReadableFile(any())).doReturn(null)

        // When
        val nextBatch = testedReader.readNextBatch()

        // Then
        assertThat(nextBatch).isNull()
    }

    @Test
    fun `M return null W readNextBatch() and orchestrator throws SecurityException`(
        forge: Forge
    ) {
        // Given
        val exception = SecurityException(forge.anAlphabeticalString())
        doThrow(exception).whenever(mockOrchestrator).getReadableFile(any())

        // When
        val nextBatch = testedReader.readNextBatch()

        // Then
        assertThat(nextBatch).isNull()
    }

    @Test
    fun `M returns null W readNextBatch() and orchestrator throws OutOfMemoryError`(
        forge: Forge
    ) {
        // Given
        val exception = OutOfMemoryError(forge.anAlphabeticalString())
        doThrow(exception).whenever(mockOrchestrator).getReadableFile(any())

        // When
        val nextBatch = testedReader.readNextBatch()

        // Then
        assertThat(nextBatch).isNull()
    }

    @Test
    fun `M returns empty batch W readNextBatch() and file doesn't exist`(
        @StringForgery dirName: String,
        @StringForgery fileName: String
    ) {
        // Given
        val file = File(File(tempRootDir, dirName), fileName)
        whenever(mockOrchestrator.getReadableFile(any())).thenReturn(file)

        // When
        val nextBatch = testedReader.readNextBatch()

        // Then
        assertThat(nextBatch?.data).isEmpty()
        assertThat(nextBatch?.id).isEqualTo(file.name)
    }

    @Test
    fun `M drop the batch W dropBatch() and file exists`(
        forge: Forge
    ) {
        // Given
        val fileName = forge.anAlphabeticalString()
        val file: File = forgeTempFile(fileName)
        whenever(mockOrchestrator.getReadableFile(any())).thenReturn(file)
        testedReader.readNextBatch()

        // Then
        testedReader.dropBatch(fileName)

        // Then
        val lockedFiles: MutableSet<String> = testedReader.getFieldValue("lockedFiles")
        assertThat(lockedFiles).isEmpty()
        assertThat(tempRootDir.listFiles()).isEmpty()
    }

    @Test
    fun `M do nothing W dropBatch() and file doesn't exist`(
        forge: Forge
    ) {
        // Given
        val fileName = forge.anAlphabeticalString()
        val file: File = forgeTempFile(fileName)
        whenever(mockOrchestrator.getReadableFile(any())).thenReturn(file)
        testedReader.readNextBatch()
        val notExistingFileName = forge.anAlphabeticalString()

        // When
        testedReader.dropBatch(notExistingFileName)

        // Then
        val lockedFiles: MutableSet<String> = testedReader.getFieldValue("lockedFiles")
        assertThat(lockedFiles).containsOnly(fileName)
    }

    @Test
    fun `M clean root folder W dropAllBatches()`(forge: Forge) {
        // Given
        val fileName1 = forge.anAlphabeticalString()
        val fileName2 = forge.anAlphabeticalString()
        val file1 = forgeTempFile(fileName1)
        val file2 = forgeTempFile(fileName2)
        whenever(mockOrchestrator.getAllFiles()).thenReturn(arrayOf(file1, file2))

        // When
        testedReader.dropAllBatches()

        // Then
        val lockedFiles: MutableSet<String> = testedReader.getFieldValue("lockedFiles")
        assertThat(tempRootDir.listFiles()).isEmpty()
        assertThat(lockedFiles).isEmpty()
    }

    @Test
    fun `it will do nothing if the only available file to be sent is locked`(forge: Forge) {
        // Given
        val inProgressFileName = forge.anAlphabeticalString()
        val inProgressFile = forgeTempFile(inProgressFileName)
        val countDownLatch = CountDownLatch(2)
        whenever(mockOrchestrator.getReadableFile(emptySet()))
            .thenReturn(inProgressFile)
            .thenReturn(null)
        whenever(mockOrchestrator.getReadableFile(setOf(inProgressFileName))).thenReturn(null)

        var batch1: Batch? = null
        var batch2: Batch? = null

        // When
        Thread {
            batch1 = testedReader.readNextBatch()
            Thread {
                batch2 = testedReader.readNextBatch()
                countDownLatch.countDown()
            }.start()
            countDownLatch.countDown()
        }.start()

        // Then
        countDownLatch.await(5, TimeUnit.SECONDS)
        assertThat(batch1?.id).isEqualTo(inProgressFileName)
        assertThat(batch2).isNull()
    }

    @Test
    fun `it will return the next file if the current one is locked`(forge: Forge) {
        // Given
        val inProgressFileName = forge.anAlphabeticalString()
        val nextFileName = inProgressFileName + "_next"
        val inProgressFile = forgeTempFile(inProgressFileName)
        val nextFile = forgeTempFile(nextFileName)
        val countDownLatch = CountDownLatch(2)
        whenever(mockOrchestrator.getReadableFile(emptySet()))
            .thenReturn(inProgressFile)
            .thenReturn(null)
        whenever(mockOrchestrator.getReadableFile(setOf(inProgressFileName))).thenReturn(nextFile)

        var batch1: Batch? = null
        var batch2: Batch? = null

        // When
        Thread {
            batch1 = testedReader.readNextBatch()
            Thread {
                batch2 = testedReader.readNextBatch()
                countDownLatch.countDown()
            }.start()
            countDownLatch.countDown()
        }.start()

        // Then
        countDownLatch.await(5, TimeUnit.SECONDS)
        assertThat(batch1?.id).isEqualTo(inProgressFileName)
        assertThat(batch2?.id).isEqualTo(nextFileName)
    }

    @Test
    fun `it will return the released file`(forge: Forge) {
        // Given
        val inProgressFileName = forge.anAlphabeticalString()
        val nextFileName = inProgressFileName + "_next"
        val inProgressFile = forgeTempFile(inProgressFileName)
        val nextFile = forgeTempFile(nextFileName)
        val countDownLatch = CountDownLatch(2)
        whenever(mockOrchestrator.getReadableFile(emptySet()))
            .thenReturn(inProgressFile)
        whenever(mockOrchestrator.getReadableFile(setOf(inProgressFileName))).thenReturn(nextFile)

        var batch2: Batch? = null

        // When
        Thread {
            val batch1 = testedReader.readNextBatch()
            Thread {
                Thread.sleep(500) // give timet o first thread to release the batch
                batch2 = testedReader.readNextBatch()
                countDownLatch.countDown()
            }.start()
            batch1?.let {
                testedReader.releaseBatch(it.id)
            }
            countDownLatch.countDown()
        }.start()

        // Then
        countDownLatch.await(5, TimeUnit.SECONDS)
        assertThat(batch2?.id).isEqualTo(inProgressFileName)
    }

    @Test
    fun `it will not throw exception in case of concurrent access`(forge: Forge) {
        val file1 = forgeTempFile(forge.anAlphabeticalString())
        val file2 = forgeTempFile(forge.anAlphabeticalString())
        val file3 = forgeTempFile(forge.anAlphabeticalString())
        val file4 = forgeTempFile(forge.anAlphabeticalString())
        whenever(mockOrchestrator.getReadableFile(any()))
            .thenReturn(file1)
            .thenReturn(file2)
            .thenReturn(file3)
            .thenReturn(file4)
        val countDownLatch = CountDownLatch(4)
        repeat(4) {
            Thread {
                testedReader.readNextBatch()?.let { testedReader.releaseBatch(it.id) }
                countDownLatch.countDown()
            }.start()
        }

        countDownLatch.await(5, TimeUnit.SECONDS)
    }

    // region Internal

    private fun forgeTempFile(fileName: String): File {
        val file = File(tempRootDir, fileName)
        file.createNewFile()
        return file
    }

    // endregion
}
