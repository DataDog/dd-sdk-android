/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.persistence.tlvformat

import com.datadog.android.api.InternalLogger
import com.datadog.android.utils.forge.Configurator
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.nio.ByteBuffer

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class TLVBlockTest {
    private lateinit var testedTLVBlock: TLVBlock

    @Mock
    lateinit var mockTLVBlockType: TLVBlockType

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @Test
    fun `M return null W serialize() { empty data }`() {
        // Given
        testedTLVBlock = TLVBlock(
            type = mockTLVBlockType,
            data = ByteArray(0),
            internalLogger = mockInternalLogger
        )

        // When
        val block = testedTLVBlock.serialize()

        // Then
        assertThat(block).isNull()
    }

    @Test
    fun `M use appropriate TLV datatypes W serialize() { has data }`(
        @StringForgery fakeString: String,
        @IntForgery(min = 0, max = 10) fakeTypeAsInt: Int
    ) {
        // Given
        val fakeTLVType = fakeTypeAsInt.toShort()
        val fakeByteArray = fakeString.toByteArray(Charsets.UTF_8)
        whenever(mockTLVBlockType.rawValue).thenReturn(fakeTLVType.toUShort())

        testedTLVBlock = TLVBlock(
            type = mockTLVBlockType,
            data = fakeByteArray,
            internalLogger = mockInternalLogger
        )

        // When
        val block = testedTLVBlock.serialize()

        // Then
        assertThat(block?.size).isEqualTo(fakeString.length + 6)
        val type = block?.copyOfRange(0, 2)
        val length = block?.copyOfRange(2, 6)
        val data = block?.copyOfRange(6, block.size)
        val typeAsShort = type?.let { ByteBuffer.wrap(it).getShort() }
        val lengthAsInt = length?.let { ByteBuffer.wrap(it).getInt() }

        assertThat(typeAsShort).isEqualTo(fakeTLVType)
        assertThat(lengthAsInt).isEqualTo(data?.size)
    }

    @Test
    fun `M log error W serialize() { exceeds max entry size }`(
        @StringForgery fakeString: String,
        @IntForgery(min = 0, max = 10) fakeTypeAsInt: Int
    ) {
        // Given
        val fakeTLVType = fakeTypeAsInt.toShort()
        val fakeByteArray = fakeString.toByteArray(Charsets.UTF_8)
        whenever(mockTLVBlockType.rawValue).thenReturn(fakeTLVType.toUShort())

        testedTLVBlock = TLVBlock(
            type = mockTLVBlockType,
            data = fakeByteArray,
            internalLogger = mockInternalLogger
        )

        // When
        testedTLVBlock.serialize(1)

        // Then
        val stringCaptor = argumentCaptor<() -> String>()
        verify(mockInternalLogger).log(
            level = eq(InternalLogger.Level.WARN),
            target = eq(InternalLogger.Target.MAINTAINER),
            stringCaptor.capture(),
            anyOrNull(),
            anyOrNull(),
            anyOrNull()
        )
        assertThat(stringCaptor.firstValue.invoke()).startsWith("DataBlock length exceeds limit")
    }
}
