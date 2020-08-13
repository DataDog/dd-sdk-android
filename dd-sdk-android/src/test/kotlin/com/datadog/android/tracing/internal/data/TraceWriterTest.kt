/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.tracing.internal.data

import com.datadog.android.core.internal.data.Writer
import com.datadog.android.utils.forge.Configurator
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyZeroInteractions
import datadog.opentracing.DDSpan
import fr.xgouchet.elmyr.Forge
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
internal class TraceWriterTest {

    lateinit var testedWriter: TraceWriter

    @Mock
    lateinit var mockFilesWriter: Writer<DDSpan>

    @BeforeEach
    fun `set up`() {
        testedWriter = TraceWriter(mockFilesWriter)
    }

    @Test
    fun `when data received it will be handled by the wrapped file writer`(forge: Forge) {
        // Given
        val spansList = ArrayList<DDSpan>(2).apply {
            add(forge.getForgery())
            add(forge.getForgery())
        }

        // When
        testedWriter.write(spansList)

        // Then
        verify(mockFilesWriter).write(spansList)
    }

    @Test
    fun `when null data received it will do nothing`(forge: Forge) {
        // When
        testedWriter.write(null)

        // Then
        verifyZeroInteractions(mockFilesWriter)
    }
}
