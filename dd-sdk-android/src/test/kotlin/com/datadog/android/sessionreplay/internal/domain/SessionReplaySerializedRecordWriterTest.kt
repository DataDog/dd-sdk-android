/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.domain

import com.datadog.android.core.internal.persistence.DataWriter
import com.datadog.android.utils.forge.Configurator
import com.nhaarman.mockitokotlin2.verify
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
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class SessionReplaySerializedRecordWriterTest {

    lateinit var testedSessionReplayRecordWriter: SessionReplaySerializedRecordWriter

    @Mock
    lateinit var mockDataWriter: DataWriter<String>

    @BeforeEach
    fun `set up`() {
        testedSessionReplayRecordWriter = SessionReplaySerializedRecordWriter(mockDataWriter)
    }

    @Test
    fun `M delegate to the dataWriter W write`(@StringForgery fakeSerializedRecord: String) {
        // When
        testedSessionReplayRecordWriter.write(fakeSerializedRecord)

        // Then
        verify(mockDataWriter).write(fakeSerializedRecord)
    }
}
