/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.data.upload

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.storage.RawBatchEvent
import com.datadog.android.core.internal.persistence.PayloadDecoration
import com.datadog.android.core.internal.persistence.file.FileMover
import com.datadog.android.core.internal.persistence.file.FileOrchestrator
import com.datadog.android.core.internal.persistence.file.batch.BatchFileReader
import com.datadog.android.utils.forge.Configurator
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.StringForgery
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

    lateinit var payloadDecoration: PayloadDecoration

    @Mock
    lateinit var mockFileReader: BatchFileReader

    @Mock
    lateinit var mockFileMover: FileMover

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @StringForgery
    lateinit var fakePrefix: String

    @StringForgery
    lateinit var fakeSuffix: String

    @StringForgery
    lateinit var fakeSeparator: String

    @BeforeEach
    fun `set up`() {
        payloadDecoration = PayloadDecoration(fakePrefix, fakeSuffix, fakeSeparator)
        testedFlusher = DataFlusher(
            mockFileOrchestrator,
            payloadDecoration,
            mockFileReader,
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
        }

        // When
        testedFlusher.flush(mockDataUploader)

        // Then
        fakeBatches.forEach {
            val expectedPayload =
                payloadDecoration.prefixBytes + it.map { it.data }.reduce { acc, bytes ->
                    acc + payloadDecoration.separatorBytes + bytes
                } + payloadDecoration.suffixBytes

            verify(mockDataUploader).upload(expectedPayload)
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
