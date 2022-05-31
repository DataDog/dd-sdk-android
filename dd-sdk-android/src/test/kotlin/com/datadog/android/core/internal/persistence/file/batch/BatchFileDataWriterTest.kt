/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.persistence.file.batch

import androidx.annotation.WorkerThread
import com.datadog.android.core.internal.persistence.DataWriter
import com.datadog.android.core.internal.persistence.PayloadDecoration
import com.datadog.android.core.internal.persistence.Serializer
import com.datadog.android.core.internal.persistence.file.ChunkedFileHandler
import com.datadog.android.core.internal.persistence.file.FileOrchestrator
import com.datadog.android.core.internal.persistence.file.FilePersistenceConfig
import com.datadog.android.log.Logger
import com.datadog.android.log.internal.logger.LogHandler
import com.datadog.android.log.internal.utils.ERROR_WITH_TELEMETRY_LEVEL
import com.datadog.android.utils.forge.Configurator
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.doAnswer
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.isNull
import com.nhaarman.mockitokotlin2.same
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyZeroInteractions
import com.nhaarman.mockitokotlin2.whenever
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import java.io.File
import java.util.Locale
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness
import org.mockito.stubbing.Answer

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@ForgeConfiguration(Configurator::class)
@MockitoSettings(strictness = Strictness.LENIENT)
internal class BatchFileDataWriterTest {

    lateinit var testedWriter: DataWriter<String>

    @Mock
    lateinit var mockSerializer: Serializer<String>

    @Mock
    lateinit var mockOrchestrator: FileOrchestrator

    @Mock
    lateinit var mockFileHandler: ChunkedFileHandler

    @Mock
    lateinit var mockLogHandler: LogHandler

    @Forgery
    lateinit var fakeDecoration: PayloadDecoration

    @Forgery
    lateinit var fakeThrowable: Throwable

    @Forgery
    lateinit var fakeFilePersistenceConfig: FilePersistenceConfig

    private val successfulData: MutableList<String> = mutableListOf()
    private val failedData: MutableList<String> = mutableListOf()

    private val stubReverseSerializerAnswer = Answer { invocation ->
        (invocation.arguments[0] as String).reversed()
    }

    private val stubFailingSerializerAnswer = Answer<String?> { null }

    private val stubThrowingSerializerAnswer = Answer<String?> {
        throw fakeThrowable
    }

    @BeforeEach
    fun `set up`() {
        whenever(mockSerializer.serialize(any())).doAnswer(stubReverseSerializerAnswer)

        testedWriter = object : BatchFileDataWriter<String>(
            mockOrchestrator,
            mockSerializer,
            fakeDecoration,
            mockFileHandler,
            Logger(mockLogHandler),
            fakeFilePersistenceConfig.copy(maxItemSize = 0)
        ) {
            @WorkerThread
            override fun onDataWritten(data: String, rawData: ByteArray) {
                successfulData.add(data)
            }

            @WorkerThread
            override fun onDataWriteFailed(data: String) {
                failedData.add(data)
            }
        }
    }

    @AfterEach
    fun `tear down`() {
        successfulData.clear()
        failedData.clear()
    }

    @Test
    fun `𝕄 write element to file 𝕎 write(element)`(
        @StringForgery data: String,
        @Forgery file: File
    ) {
        // Given
        val serialized = data.reversed().toByteArray(Charsets.UTF_8)
        whenever(mockOrchestrator.getWritableFile()) doReturn file

        // When
        testedWriter.write(data)

        // Then
        verify(mockFileHandler)
            .writeData(
                file,
                serialized,
                append = true
            )
    }

    @Test
    fun `𝕄 write elements to file 𝕎 write(list)`(
        @StringForgery data: List<String>,
        @Forgery file: File
    ) {
        // Given
        whenever(mockOrchestrator.getWritableFile()) doReturn file

        // When
        testedWriter.write(data)

        // Then
        argumentCaptor<ByteArray> {
            verify(mockFileHandler, times(data.size))
                .writeData(
                    same(file),
                    capture(),
                    append = eq(true)
                )
            assertThat(allValues)
                .containsExactlyElementsOf(
                    data.map {
                        it.reversed().toByteArray(Charsets.UTF_8)
                    }
                )
        }
    }

    @Test
    fun `𝕄 notify success 𝕎 write(element)`(
        @StringForgery data: String,
        @Forgery file: File
    ) {
        // Given
        whenever(mockFileHandler.writeData(any(), any(), any())) doReturn true
        whenever(mockOrchestrator.getWritableFile()) doReturn file

        // When
        testedWriter.write(data)

        // Then
        assertThat(successfulData).containsExactly(data)
        assertThat(failedData).isEmpty()
    }

    @Test
    fun `𝕄 notify failure 𝕎 write(element) { writing failure }`(
        @StringForgery data: String,
        @Forgery file: File
    ) {
        // Given
        whenever(mockFileHandler.writeData(any(), any(), any())) doReturn false
        whenever(mockOrchestrator.getWritableFile()) doReturn file

        // When
        testedWriter.write(data)

        // Then
        assertThat(successfulData).isEmpty()
        assertThat(failedData).containsExactly(data)
    }

    @Test
    fun `𝕄 do nothing 𝕎 write(element) { serialization to null }`(
        @StringForgery data: String
    ) {
        // Given
        whenever(mockSerializer.serialize(data)) doAnswer stubFailingSerializerAnswer

        // When
        testedWriter.write(data)

        // Then
        assertThat(successfulData).isEmpty()
        assertThat(failedData).isEmpty()
        verifyZeroInteractions(mockFileHandler)
    }

    @Test
    fun `𝕄 do nothing 𝕎 write(element) { serialization exception }`(
        @StringForgery data: String
    ) {
        // Given
        whenever(mockSerializer.serialize(data)) doAnswer stubThrowingSerializerAnswer

        // When
        testedWriter.write(data)

        // Then
        assertThat(successfulData).isEmpty()
        assertThat(failedData).isEmpty()
        verifyZeroInteractions(mockFileHandler)
        verify(mockLogHandler).handleLog(
            eq(ERROR_WITH_TELEMETRY_LEVEL),
            eq(Serializer.ERROR_SERIALIZING.format(Locale.US, data.javaClass.simpleName)),
            same(fakeThrowable),
            eq(emptyMap()),
            eq(emptySet()),
            isNull()
        )
    }

    @Test
    fun `𝕄 do nothing 𝕎 write(element) { element is too big }`(
        @StringForgery data: String
    ) {
        // When
        val dataSize = data.toByteArray(Charsets.UTF_8).size
        val maxLimit = (dataSize - 1).toLong()

        // Given
        testedWriter = BatchFileDataWriter(
            mockOrchestrator,
            mockSerializer,
            fakeDecoration,
            mockFileHandler,
            Logger(mockLogHandler),
            fakeFilePersistenceConfig.copy(
                maxItemSize = maxLimit
            )
        )

        // When
        testedWriter.write(data)

        // Then
        verifyZeroInteractions(mockFileHandler)
        verify(mockLogHandler).handleLog(
            ERROR_WITH_TELEMETRY_LEVEL,
            BatchFileDataWriter.ERROR_LARGE_DATA.format(Locale.US, dataSize, maxLimit)
        )
    }
}
