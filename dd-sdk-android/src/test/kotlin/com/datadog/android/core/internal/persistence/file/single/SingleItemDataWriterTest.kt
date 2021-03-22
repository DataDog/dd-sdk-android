/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.persistence.file.single

import com.datadog.android.core.internal.persistence.DataWriter
import com.datadog.android.core.internal.persistence.Serializer
import com.datadog.android.core.internal.persistence.file.FileHandler
import com.datadog.android.core.internal.persistence.file.FileOrchestrator
import com.datadog.android.utils.forge.Configurator
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doAnswer
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyZeroInteractions
import com.nhaarman.mockitokotlin2.whenever
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.StringForgery
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
import org.mockito.stubbing.Answer

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
    lateinit var mockFileHandler: FileHandler

    private val stubReverseSerializerAnswer = Answer<String?> { invocation ->
        (invocation.getArgument<String>(0)).reversed()
    }

    private val stubFailingSerializerAnswer = Answer<String?> { null }

    @BeforeEach
    fun `set up`() {
        whenever(mockSerializer.serialize(any())).doAnswer(stubReverseSerializerAnswer)

        testedWriter = SingleItemDataWriter(
            mockOrchestrator,
            mockSerializer,
            mockFileHandler
        )
    }

    @Test
    fun `𝕄 write element to file 𝕎 write(element)`(
        @StringForgery data: String,
        @Forgery file: File
    ) {
        // Given
        val serialized = data.reversed().toByteArray(Charsets.UTF_8)
        whenever(mockOrchestrator.getWritableFile(any())) doReturn file

        // When
        testedWriter.write(data)

        // Then
        verify(mockFileHandler)
            .writeData(
                file,
                serialized,
                append = false,
                separator = null
            )
    }

    @Test
    fun `𝕄 write last element to file 𝕎 write(list)`(
        @StringForgery data: List<String>,
        @Forgery file: File
    ) {
        // Given
        val lastSerialized = data.last().reversed().toByteArray(Charsets.UTF_8)
        whenever(mockOrchestrator.getWritableFile(any())) doReturn file

        // When
        testedWriter.write(data)

        // Then
        verify(mockFileHandler)
            .writeData(
                file,
                lastSerialized,
                append = false,
                separator = null
            )
    }

    @Test
    fun `𝕄 do nothing 𝕎 write(element) { serialization failure }`(
        @StringForgery data: String
    ) {
        // Given
        whenever(mockSerializer.serialize(data)) doAnswer stubFailingSerializerAnswer

        // When
        testedWriter.write(data)

        // Then
        verifyZeroInteractions(mockFileHandler)
    }
}
