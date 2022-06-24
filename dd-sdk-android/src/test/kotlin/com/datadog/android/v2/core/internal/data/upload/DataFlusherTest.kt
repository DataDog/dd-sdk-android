/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.v2.core.internal.data.upload

import com.datadog.android.core.internal.persistence.file.FileMover
import com.datadog.android.core.internal.persistence.file.FileOrchestrator
import com.datadog.android.core.internal.persistence.file.FileReader
import com.datadog.android.core.internal.persistence.file.batch.BatchFileReader
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.v2.api.context.DatadogContext
import com.datadog.android.v2.core.internal.ContextProvider
import com.datadog.android.v2.core.internal.net.DataUploader
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyZeroInteractions
import com.nhaarman.mockitokotlin2.whenever
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import java.io.File
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness

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
    lateinit var mockMetaFileReader: FileReader

    @Mock
    lateinit var mockFileMover: FileMover

    @Mock
    lateinit var mockContextProvider: ContextProvider

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
            mockFileMover
        )
    }

    @Test
    fun `M upload all the batches W flush`(
        forge: Forge
    ) {
        // Given
        val fakeFiles = forge.aList { mock<File>() }
        val fakeMetaFiles = forge.aList(fakeFiles.size) { forge.aNullable { mock<File>() } }
        val fakeBatches = forge
            .aList(fakeFiles.size) {
                forge
                    .aList {
                        forge.aString()
                    }
                    .map { it.toByteArray() }
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
                    .map { it.toByteArray() }
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
    fun `M do nothing W flush { no data available }`() {
        // Given
        whenever(mockFileOrchestrator.getFlushableFiles()).thenReturn(emptyList())

        // When
        testedFlusher.flush(mockDataUploader)

        // Then
        verifyZeroInteractions(mockFileReader)
        verifyZeroInteractions(mockDataUploader)
    }
}
