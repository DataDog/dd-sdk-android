/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.net

import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.android.sessionreplay.internal.processor.EnrichedRecord
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import java.util.zip.Inflater
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(ForgeConfigurator::class)
internal class BytesCompressorTest {

    lateinit var testedBytesCompressor: BytesCompressor

    @BeforeEach
    fun `set up`() {
        testedBytesCompressor = BytesCompressor()
    }

    @RepeatedTest(20)
    fun `M compress the provided bytearray W compressBytes`(
        @Forgery fakeEnrichedRecord: EnrichedRecord
    ) {
        // Given
        val fakeData = fakeEnrichedRecord.toJson()
        val fakeDataAsByteArray = fakeData.toByteArray()

        // When
        val compressed = testedBytesCompressor.compressBytes(fakeDataAsByteArray)

        // Then
        // Decompress the bytes by removing the last fake checksum
        val decompressor = Inflater()
        decompressor.setInput(
            compressed.sliceArray(
                0..compressed.size -
                    BytesCompressor.CHECKSUM_FLAG_SIZE_IN_BYTES
            )
        )
        val result = ByteArray(fakeDataAsByteArray.size)
        val resultLength = decompressor.inflate(result)
        decompressor.end()
        val outputString = String(result, 0, resultLength)
        assertThat(fakeData).isEqualTo(outputString)
    }
}
