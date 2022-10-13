/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.persistence.file.batch

import androidx.annotation.WorkerThread
import com.datadog.android.core.internal.persistence.DataWriter
import com.datadog.android.core.internal.persistence.Serializer
import com.datadog.android.log.Logger
import com.datadog.android.log.internal.logger.LogHandler
import com.datadog.android.log.internal.utils.ERROR_WITH_TELEMETRY_LEVEL
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.v2.api.BatchWriterListener
import com.datadog.android.v2.api.context.DatadogContext
import com.datadog.android.v2.core.internal.ContextProvider
import com.datadog.android.v2.core.internal.storage.BatchWriter
import com.datadog.android.v2.core.internal.storage.Storage
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.anyOrNull
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
import java.util.Locale

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
    lateinit var mockLogHandler: LogHandler

    @Mock
    lateinit var mockStorage: Storage

    @Mock
    lateinit var mockContextProvider: ContextProvider

    @Mock
    lateinit var mockBatchWriter: BatchWriter

    @Forgery
    lateinit var fakeThrowable: Throwable

    @Forgery
    lateinit var fakeDatadogContext: DatadogContext

    private val successfulData: MutableList<String> = mutableListOf()
    private val failedData: MutableList<String> = mutableListOf()

    private val stubReverseSerializerAnswer = Answer { invocation ->
        (invocation.arguments[0] as String).reversed()
    }

    private val stubFailingSerializerAnswer = Answer<String?> { null }

    private val stubThrowingSerializerAnswer = Answer<String?> {
        throw fakeThrowable
    }

    private val stubSuccessfulWriteAnswer = Answer {
        whenever(mockBatchWriter.write(any(), any(), anyOrNull(), any())) doAnswer {
            val eventId = it.getArgument<String>(1)
            val listener = it.getArgument<BatchWriterListener>(3)
            listener.onDataWritten(eventId)
        }

        val callback = it.getArgument<(BatchWriter) -> Unit>(1)
        callback.invoke(mockBatchWriter)
    }

    private val stubFailingWriteAnswer = Answer {
        whenever(mockBatchWriter.write(any(), any(), anyOrNull(), any())) doAnswer {
            val eventId = it.getArgument<String>(1)
            val listener = it.getArgument<BatchWriterListener>(3)
            listener.onDataWriteFailed(eventId)
        }

        val callback = it.getArgument<(BatchWriter) -> Unit>(1)
        callback.invoke(mockBatchWriter)
    }

    @BeforeEach
    fun `set up`() {
        whenever(mockSerializer.serialize(any())).doAnswer(stubReverseSerializerAnswer)

        whenever(mockContextProvider.context) doReturn fakeDatadogContext

        testedWriter = object : BatchFileDataWriter<String>(
            mockStorage,
            mockContextProvider,
            mockSerializer,
            Logger(mockLogHandler)
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
        @StringForgery data: String
    ) {
        // Given
        val serialized = data.reversed().toByteArray(Charsets.UTF_8)
        whenever(mockStorage.writeCurrentBatch(eq(fakeDatadogContext), any()))
            .doAnswer(stubSuccessfulWriteAnswer)

        // When
        testedWriter.write(data)

        // Then
        verify(mockBatchWriter)
            .write(
                eq(serialized),
                any(),
                isNull(),
                any()
            )
    }

    @Test
    fun `𝕄 write elements to file 𝕎 write(list)`(
        @StringForgery data: List<String>
    ) {
        // Given
        whenever(mockStorage.writeCurrentBatch(eq(fakeDatadogContext), any()))
            .doAnswer(stubSuccessfulWriteAnswer)

        // When
        testedWriter.write(data)

        // Then
        argumentCaptor<ByteArray> {
            verify(mockBatchWriter, times(data.size))
                .write(
                    capture(),
                    any(),
                    isNull(),
                    any()
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
        @StringForgery data: String
    ) {
        // Given
        whenever(mockStorage.writeCurrentBatch(eq(fakeDatadogContext), any()))
            .doAnswer(stubSuccessfulWriteAnswer)

        // When
        testedWriter.write(data)

        // Then
        assertThat(successfulData).containsExactly(data)
        assertThat(failedData).isEmpty()
    }

    @Test
    fun `𝕄 notify failure 𝕎 write(element) { writing failure }`(
        @StringForgery data: String
    ) {
        // Given
        whenever(mockStorage.writeCurrentBatch(eq(fakeDatadogContext), any()))
            .doAnswer(stubFailingWriteAnswer)

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
        verifyZeroInteractions(mockStorage)
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
        verifyZeroInteractions(mockStorage)
        verify(mockLogHandler).handleLog(
            eq(ERROR_WITH_TELEMETRY_LEVEL),
            eq(Serializer.ERROR_SERIALIZING.format(Locale.US, data.javaClass.simpleName)),
            same(fakeThrowable),
            eq(emptyMap()),
            eq(emptySet()),
            isNull()
        )
    }
}
