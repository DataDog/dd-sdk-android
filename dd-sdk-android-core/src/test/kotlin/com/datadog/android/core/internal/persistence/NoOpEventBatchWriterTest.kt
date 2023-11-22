/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.persistence

import com.datadog.android.api.storage.EventBatchWriter
import com.datadog.android.api.storage.RawBatchEvent
import com.datadog.android.utils.forge.Configurator
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
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
        @Forgery fakeData: RawBatchEvent,
        @StringForgery fakeMetadata: String,
        forge: Forge
    ) {
        // When
        val result = testedWriter.write(
            fakeData,
            forge.aNullable { fakeMetadata.toByteArray() }
        )

        // Then
        assertThat(result).isTrue
    }
}
