/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.data.upload

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.context.DatadogContext
import com.datadog.android.api.storage.RawBatchEvent
import com.datadog.android.core.internal.ContextProvider
import com.datadog.android.core.internal.persistence.file.FileMover
import com.datadog.android.core.internal.persistence.file.FileOrchestrator
import com.datadog.android.core.internal.persistence.file.FileReader
import com.datadog.android.core.internal.persistence.file.batch.BatchFileReader
import com.datadog.android.utils.forge.Configurator
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.io.File

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class DataFlusherTest {
    lateinit var testedFlusher: DataFlusher

    @Mock
    lateinit var mockDataUploader: DataUploader

    @Mock
    lateinit var mockFileOrchestrator: FileOrchestrator

    @Mock
    lateinit var mockFileReader: BatchFileReader

    @Mock
    lateinit var mockMetaFileReader: FileReader<ByteArray>

    @Mock
    lateinit var mockFileMover: FileMover

    @Mock
    lateinit var mockContextProvider: ContextProvider

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @Forgery
    lateinit var fakeContext: DatadogContext

    @BeforeEach
    fun `set up`() {
        whenever(mockContextProvider.context) doReturn fakeContext

        testedFlusher = DataFlusher(
            mockContextProvider,
            mockFileOrchestrator,
            mockFileReader,
            mockMetaFileReader,
            mockFileMover,
            mockInternalLogger
        )
    }

    @Test
    fun `M upload all the batches W flush`(
        forge: Forge
    ) {
        // Given
        val fakeFiles = forge.aList { mock<File>() }
        val fakeMetaFiles =
            forge.aList(fakeFiles.size) {
                forge.aNullable { mock<File>().apply { whenever(exists()) doReturn true } }
            }
        val fakeBatches = forge
            .aList(fakeFiles.size) {
                forge
                    .aList {
                        forge.aString()
                    }
                    .map { RawBatchEvent(it.toByteArray()) }
            }
        val fakeMeta = fakeMetaFiles.map { if (it != null) forge.aString().toByteArray() else null }
        whenever(mockFileOrchestrator.getFlushableFiles()).thenReturn(fakeFiles)
        fakeFiles.forEachIndexed { index, file ->
            whenever(
                mockFileReader.readData(file)
            ).thenReturn(fakeBatches[index])
            val fakeMetaFile = fakeMetaFiles[index]
            whenever(
                mockFileOrchestrator.getMetadataFile(file)
            ).thenReturn(fakeMetaFile)
            if (fakeMetaFile != null) {
                whenever(mockMetaFileReader.readData(fakeMetaFile)).thenReturn(fakeMeta[index])
            }
        }

        // When
        testedFlusher.flush(mockDataUploader)

        // Then
        fakeBatches.forEachIndexed { index, batch ->
            verify(mockDataUploader).upload(fakeContext, batch, fakeMeta[index])
        }
    }

    @Test
    fun `M delete all the batches W flush`(
        forge: Forge
    ) {
        // Given
        val fakeFiles = forge.aList { mock<File>() }
        val fakeMetaFiles =
            forge.aList(fakeFiles.size) { mock<File>().apply { whenever(exists()) doReturn true } }

        val fakeBatches = forge
            .aList(fakeFiles.size) {
                forge
                    .aList {
                        forge.aString()
                    }
                    .map { RawBatchEvent(it.toByteArray()) }
            }
        whenever(mockFileOrchestrator.getFlushableFiles()).thenReturn(fakeFiles)
        fakeFiles.forEachIndexed { index, file ->
            whenever(
                mockFileReader.readData(file)
            ).thenReturn(fakeBatches[index])
            whenever(
                mockFileOrchestrator.getMetadataFile(file)
            ).thenReturn(fakeMetaFiles[index])
        }

        // When
        testedFlusher.flush(mockDataUploader)

        // Then
        fakeFiles.forEach {
            verify(mockFileMover).delete(it)
        }
        fakeMetaFiles.forEach {
            verify(mockFileMover).delete(it)
        }
    }

    @Test
    fun `M not attempt to read metadata W flush { meta doesn't exist }`(
        forge: Forge
    ) {
        // Given
        val fakeFiles = forge.aList { mock<File>() }
        val fakeBatches = forge
            .aList(fakeFiles.size) {
                forge
                    .aList {
                        forge.aString()
                    }
                    .map { RawBatchEvent(it.toByteArray()) }
            }
        whenever(mockFileOrchestrator.getFlushableFiles()).thenReturn(fakeFiles)
        fakeFiles.forEachIndexed { index, file ->
            whenever(
                mockFileReader.readData(file)
            ).thenReturn(fakeBatches[index])
            val fakeBatchFile =
                forge.aNullable { mock<File>().apply { whenever(exists()) doReturn false } }
            whenever(
                mockFileOrchestrator.getMetadataFile(file)
            ).thenReturn(fakeBatchFile)
        }

        // When
        testedFlusher.flush(mockDataUploader)

        // Then
        verifyNoInteractions(mockMetaFileReader)
    }

    @Test
    fun `M do nothing W flush { no data available }`() {
        // Given
        whenever(mockFileOrchestrator.getFlushableFiles()).thenReturn(emptyList())

        // When
        testedFlusher.flush(mockDataUploader)

        // Then
        verifyNoInteractions(mockFileReader)
        verifyNoInteractions(mockDataUploader)
    }
}
