/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.persistence

import com.datadog.android.api.storage.EventBatchWriter
import com.datadog.android.api.storage.EventType
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

    @Forgery
    lateinit var fakeEventType: EventType

    @BeforeEach
    fun `set up`() {
        testedWriter = NoOpEventBatchWriter()
    }

    @Test
    fun `M return no metadata W currentMetadata()`() {
        assertThat(testedWriter.currentMetadata()).isNull()
    }

    @Test
    fun `M notify about successful write W write()`(
        @Forgery fakeData: RawBatchEvent,
        @StringForgery fakeMetadata: String,
        forge: Forge
    ) {
        // When
        val result = testedWriter.write(
            fakeData,
            forge.aNullable { fakeMetadata.toByteArray() },
            fakeEventType
        )

        // Then
        assertThat(result).isTrue
    }
}
