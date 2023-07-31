/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.persistence.file.single

import com.datadog.android.api.InternalLogger
import com.datadog.android.core.internal.persistence.DataWriter
import com.datadog.android.core.internal.persistence.file.FileOrchestrator
import com.datadog.android.core.internal.persistence.file.FilePersistenceConfig
import com.datadog.android.core.internal.persistence.file.FileWriter
import com.datadog.android.core.persistence.Serializer
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.utils.verifyLog
import fr.xgouchet.elmyr.annotation.Forgery
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
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import org.mockito.stubbing.Answer
import java.io.File
import java.util.Locale

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class SingleItemDataWriterTest {

    lateinit var testedWriter: DataWriter<String>

    @Mock
    lateinit var mockSerializer: Serializer<String>

    @Mock
    lateinit var mockOrchestrator: FileOrchestrator

    @Mock
    lateinit var mockFileWriter: FileWriter

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @Forgery
    lateinit var fakeThrowable: Throwable

    @Forgery
    lateinit var fakeFilePersistenceConfig: FilePersistenceConfig

    private val stubReverseSerializerAnswer = Answer { invocation ->
        (invocation.getArgument<String>(0)).reversed()
    }

    private val stubFailingSerializerAnswer = Answer<String?> { null }

    private val stubThrowingSerializerAnswer = Answer<String?> {
        throw fakeThrowable
    }

    @BeforeEach
    fun `set up`() {
        whenever(mockSerializer.serialize(any())).doAnswer(stubReverseSerializerAnswer)

        testedWriter = SingleItemDataWriter(
            mockOrchestrator,
            mockSerializer,
            mockFileWriter,
            mockInternalLogger,
            fakeFilePersistenceConfig.copy(maxItemSize = Long.MAX_VALUE)
        )
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
        verify(mockFileWriter)
            .writeData(
                file,
                serialized,
                append = false
            )
    }

    @Test
    fun `𝕄 write last element to file 𝕎 write(list)`(
        @StringForgery data: List<String>,
        @Forgery file: File
    ) {
        // Given
        val lastSerialized = data.last().reversed().toByteArray(Charsets.UTF_8)
        whenever(mockOrchestrator.getWritableFile()) doReturn file

        // When
        testedWriter.write(data)

        // Then
        verify(mockFileWriter)
            .writeData(
                file,
                lastSerialized,
                append = false
            )
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
        verifyNoInteractions(mockFileWriter)
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
        verifyNoInteractions(mockFileWriter)
        mockInternalLogger.verifyLog(
            InternalLogger.Level.ERROR,
            targets = listOf(InternalLogger.Target.USER, InternalLogger.Target.TELEMETRY),
            Serializer.ERROR_SERIALIZING.format(Locale.US, data.javaClass.simpleName),
            fakeThrowable
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
        testedWriter = SingleItemDataWriter(
            mockOrchestrator,
            mockSerializer,
            mockFileWriter,
            mockInternalLogger,
            fakeFilePersistenceConfig.copy(
                maxItemSize = maxLimit
            )
        )

        // When
        testedWriter.write(data)

        // Then
        verifyNoInteractions(mockFileWriter)
        mockInternalLogger.verifyLog(
            InternalLogger.Level.ERROR,
            targets = listOf(InternalLogger.Target.USER, InternalLogger.Target.TELEMETRY),
            SingleItemDataWriter.ERROR_LARGE_DATA.format(Locale.US, dataSize, maxLimit)
        )
    }
}
