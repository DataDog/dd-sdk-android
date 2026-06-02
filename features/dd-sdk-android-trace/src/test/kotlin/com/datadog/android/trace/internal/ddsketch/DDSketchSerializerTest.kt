/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.trace.internal.ddsketch

import com.datadog.android.utils.forge.Configurator
import fr.xgouchet.elmyr.annotation.DoubleForgery
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness
import java.nio.ByteBuffer
import java.nio.ByteOrder

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class DDSketchSerializerTest {

    lateinit var testedSerializer: DDSketchSerializer

    @BeforeEach
    fun setUp() {
        testedSerializer = DDSketchSerializer(1024)
    }

    // region doubleFieldSize

    @Test
    fun `M return 0 W doubleFieldSize() {value is 0}`(
        @IntForgery(min = 1, max = 15) fakeField: Int
    ) {
        assertThat(DDSketchSerializer.doubleFieldSize(fakeField, 0.0)).isEqualTo(0)
    }

    @Test
    fun `M return 9 W doubleFieldSize() {non-zero value, single-byte tag field}`(
        @DoubleForgery(min = 0.001, max = 1000.0) fakeValue: Double
    ) {
        // tag (1 byte for field 1) + 8 bytes for the double
        assertThat(DDSketchSerializer.doubleFieldSize(1, fakeValue)).isEqualTo(9)
    }

    // endregion

    // region signedIntFieldSize

    @Test
    fun `M return 2 W signedIntFieldSize() {small value}`(
        @IntForgery(min = -62, max = 62) fakeValue: Int
    ) {
        // tag (1 byte) + zigzag varint (1 byte for values where zigzag fits in 7 bits)
        assertThat(DDSketchSerializer.signedIntFieldSize(1, fakeValue)).isEqualTo(2)
    }

    // endregion

    // region sizeOfCompactDoubleArray

    @Test
    fun `M return 10 W sizeOfCompactDoubleArray() {1 element, field 2}`() {
        // tag(1) + varint(8)(1) + 8 bytes = 10
        assertThat(DDSketchSerializer.sizeOfCompactDoubleArray(2, 1)).isEqualTo(10)
    }

    @Test
    fun `M scale with element count W sizeOfCompactDoubleArray()`(
        @IntForgery(min = 1, max = 100) fakeCount: Int
    ) {
        val size1 = DDSketchSerializer.sizeOfCompactDoubleArray(1, fakeCount)
        val size2 = DDSketchSerializer.sizeOfCompactDoubleArray(1, fakeCount + 1)
        assertThat(size2 - size1).isEqualTo(8)
    }

    // endregion

    // region embeddedSize / embeddedFieldSize

    @Test
    fun `M return 1 W embeddedSize() {size 0}`() {
        // Just the length varint byte, no payload
        assertThat(DDSketchSerializer.embeddedSize(0)).isEqualTo(1)
    }

    @Test
    fun `M return size plus 1 W embeddedSize() {small payload}`(
        @IntForgery(min = 1, max = 127) fakeSize: Int
    ) {
        // 1 byte for length varint + fakeSize payload bytes
        assertThat(DDSketchSerializer.embeddedSize(fakeSize)).isEqualTo(fakeSize + 1)
    }

    @Test
    fun `M return tag plus embedded W embeddedFieldSize()`(
        @IntForgery(min = 1, max = 127) fakeSize: Int
    ) {
        // tag (1 byte for field 1) + embeddedSize(fakeSize)
        assertThat(DDSketchSerializer.embeddedFieldSize(1, fakeSize))
            .isEqualTo(1 + DDSketchSerializer.embeddedSize(fakeSize))
    }

    // endregion

    // region writeDouble

    @Test
    fun `M write nothing W writeDouble() {value is 0}`() {
        // When
        testedSerializer.writeDouble(1, 0.0)

        // Then
        assertThat(testedSerializer.toByteArray()).isEmpty()
    }

    @Test
    fun `M write FIXED_64 tag then LE double W writeDouble() {non-zero value}`() {
        // When
        testedSerializer.writeDouble(1, 1.0)

        // Then: tag = 0x09 (field=1, wire=FIXED_64), then 1.0 as LE double
        val bytes = testedSerializer.toByteArray()
        assertThat(bytes[0]).isEqualTo(0x09.toByte())
        val value = ByteBuffer.wrap(bytes, 1, 8).order(ByteOrder.LITTLE_ENDIAN).double
        assertThat(value).isEqualTo(1.0)
    }

    @Test
    fun `M round-trip value W writeDouble()`(
        @DoubleForgery(min = 0.001, max = 1e10) fakeValue: Double
    ) {
        // When
        testedSerializer.writeDouble(1, fakeValue)

        // Then: skip the 1-byte tag, read the double
        val bytes = testedSerializer.toByteArray()
        val parsed = ByteBuffer.wrap(bytes, 1, 8).order(ByteOrder.LITTLE_ENDIAN).double
        assertThat(parsed).isEqualTo(fakeValue)
    }

    // endregion

    // region writeSignedInt32

    @Test
    fun `M write VARINT tag then zigzag 0 W writeSignedInt32() {value 0}`() {
        // When
        testedSerializer.writeSignedInt32(1, 0)

        // Then: tag = 0x08 (field=1, wire=VARINT), zigzag(0) = 0x00
        val bytes = testedSerializer.toByteArray()
        assertThat(bytes[0]).isEqualTo(0x08.toByte())
        assertThat(bytes[1]).isEqualTo(0x00.toByte())
    }

    @Test
    fun `M write zigzag-encoded positive int W writeSignedInt32() {value 1}`() {
        // When
        testedSerializer.writeSignedInt32(1, 1)

        // Then: zigzag(1) = 2 = 0x02
        val bytes = testedSerializer.toByteArray()
        assertThat(bytes[0]).isEqualTo(0x08.toByte())
        assertThat(bytes[1]).isEqualTo(0x02.toByte())
    }

    @Test
    fun `M write zigzag-encoded negative int W writeSignedInt32() {value -1}`() {
        // When
        testedSerializer.writeSignedInt32(1, -1)

        // Then: zigzag(-1) = 1 = 0x01
        val bytes = testedSerializer.toByteArray()
        assertThat(bytes[0]).isEqualTo(0x08.toByte())
        assertThat(bytes[1]).isEqualTo(0x01.toByte())
    }

    // endregion

    // region writeHeader

    @Test
    fun `M write LENGTH_DELIMITED tag then length varint W writeHeader()`() {
        // When
        testedSerializer.writeHeader(1, 8)

        // Then: tag = 0x0A (field=1, wire=LENGTH_DELIMITED), varint(8) = 0x08
        val bytes = testedSerializer.toByteArray()
        assertThat(bytes[0]).isEqualTo(0x0A.toByte())
        assertThat(bytes[1]).isEqualTo(0x08.toByte())
    }

    @Test
    fun `M write multi-byte varint W writeHeader() {length requires 2 varint bytes}`() {
        // When: varint(128) encodes as 0x80 0x01 (continuation bit set on first byte)
        testedSerializer.writeHeader(1, 128)

        // Then: tag = 0x0A, then 128 as 2-byte varint
        val bytes = testedSerializer.toByteArray()
        assertThat(bytes[0]).isEqualTo(0x0A.toByte())
        assertThat(bytes[1]).isEqualTo(0x80.toByte()) // low 7 bits of 128 with continuation bit
        assertThat(bytes[2]).isEqualTo(0x01.toByte()) // remaining bits
    }

    // endregion

    // region writeCompactArray

    @Test
    fun `M write LENGTH_DELIMITED tag, byte count, then LE doubles W writeCompactArray() {single element}`() {
        // Given
        val array = doubleArrayOf(0.0, 1.0, 0.0)

        // When
        testedSerializer.writeCompactArray(2, array, 1, 1)

        // Then: tag=0x12, varint(8)=0x08, then 1.0 as LE double
        val bytes = testedSerializer.toByteArray()
        assertThat(bytes[0]).isEqualTo(0x12.toByte())
        assertThat(bytes[1]).isEqualTo(0x08.toByte())
        val value = ByteBuffer.wrap(bytes, 2, 8).order(ByteOrder.LITTLE_ENDIAN).double
        assertThat(value).isEqualTo(1.0)
    }

    @Test
    fun `M write one double per element W writeCompactArray() {multiple elements}`() {
        // Given
        val array = doubleArrayOf(2.0, 3.0, 5.0)

        // When
        testedSerializer.writeCompactArray(1, array, 0, 3)

        // Then: tag=0x0A, varint(24)=0x18, then three LE doubles
        val bytes = testedSerializer.toByteArray()
        assertThat(bytes[0]).isEqualTo(0x0A.toByte())
        assertThat(bytes[1]).isEqualTo(0x18.toByte())
        val buf = ByteBuffer.wrap(bytes, 2, 24).order(ByteOrder.LITTLE_ENDIAN)
        assertThat(buf.double).isEqualTo(2.0)
        assertThat(buf.double).isEqualTo(3.0)
        assertThat(buf.double).isEqualTo(5.0)
    }

    @Test
    fun `M respect from offset W writeCompactArray()`() {
        // Given: array has 3 elements but we only serialize index 1..2
        val array = doubleArrayOf(99.0, 2.0, 3.0)

        // When
        testedSerializer.writeCompactArray(1, array, 1, 2)

        // Then: only 2.0 and 3.0 are serialized, not 99.0
        val bytes = testedSerializer.toByteArray()
        val buf = ByteBuffer.wrap(bytes, 2, 16).order(ByteOrder.LITTLE_ENDIAN)
        assertThat(buf.double).isEqualTo(2.0)
        assertThat(buf.double).isEqualTo(3.0)
    }

    // endregion
}
