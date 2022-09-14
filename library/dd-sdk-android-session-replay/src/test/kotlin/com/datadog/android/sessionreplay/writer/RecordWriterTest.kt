/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.writer

import com.datadog.android.sessionreplay.SerializedRecordWriter
import com.datadog.android.sessionreplay.processor.EnrichedRecord
import com.datadog.android.sessionreplay.utils.ForgeConfigurator
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.verify
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
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
@ForgeConfiguration(ForgeConfigurator::class)
internal class RecordWriterTest {

    lateinit var testedWriter: RecordWriter

    @Mock
    lateinit var mockSerializedRecordWriter: SerializedRecordWriter

    @BeforeEach
    fun `set up`() {
        testedWriter = RecordWriter(mockSerializedRecordWriter)
    }

    @Test
    fun `M delegate to the serializedRecordWriter W write`(
        @Forgery fakeEnrichedRecord: EnrichedRecord
    ) {
        // When
        testedWriter.write(fakeEnrichedRecord)

        // Then
        val argumentCaptor = argumentCaptor<String>()
        verify(mockSerializedRecordWriter).write(argumentCaptor.capture())
        assertThat(argumentCaptor.firstValue).isEqualTo(fakeEnrichedRecord.toJson())
    }
}
