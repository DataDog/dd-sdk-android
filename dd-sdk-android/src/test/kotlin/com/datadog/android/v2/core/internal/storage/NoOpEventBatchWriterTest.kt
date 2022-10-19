/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.v2.core.internal.storage

import com.datadog.android.v2.api.BatchWriterListener
import com.datadog.android.v2.api.EventBatchWriter
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyNoMoreInteractions
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(ForgeExtension::class),
)
@MockitoSettings(strictness = Strictness.LENIENT)
internal class NoOpEventBatchWriterTest {

    private lateinit var testedWriter: EventBatchWriter

    @BeforeEach
    fun `set up`() {
        testedWriter = NoOpEventBatchWriter()
    }

    @Test
    fun `𝕄 return no metadata 𝕎 currentMetadata()`() {
        assertThat(testedWriter.currentMetadata()).isNull()
    }

    @Test
    fun `𝕄 notify about successful write 𝕎 write()`(
        @StringForgery fakeData: String,
        @StringForgery fakeMetadata: String,
        @StringForgery fakeEventId: String,
        forge: Forge
    ) {
        // Given
        val mockListener = mock<BatchWriterListener>()

        // When
        testedWriter.write(
            fakeData.toByteArray(),
            fakeEventId,
            forge.aNullable { fakeMetadata.toByteArray() },
            mockListener
        )

        // Then
        verify(mockListener).onDataWritten(fakeEventId)
        verifyNoMoreInteractions(mockListener)
    }
}
