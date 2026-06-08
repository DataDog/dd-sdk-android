/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.trace.internal.ddsketch

import com.datadog.android.utils.forge.Configurator
import com.google.protobuf.CodedOutputStream
import com.google.protobuf.WireFormat
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
import java.io.ByteArrayOutputStream

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
        // Given / When
        val result = DDSketchSerializer.doubleFieldSize(fakeField, 0.0)

        // Then: proto3 default value is omitted from the wire
        assertThat(result).isEqualTo(0)
    }

    @Test
    fun `M match CodedOutputStream W doubleFieldSize() {non-zero value}`(
        @IntForgery(min = 1, max = 15) fakeField: Int,
        @DoubleForgery(min = 0.001, max = 1000.0) fakeValue: Double
    ) {
        // Given / When
        val result = DDSketchSerializer.doubleFieldSize(fakeField, fakeValue)

        // Then
        assertThat(result).isEqualTo(CodedOutputStream.computeDoubleSize(fakeField, fakeValue))
    }

    // endregion

    // region signedIntFieldSize

    @Test
    fun `M return 0 W signedIntFieldSize() {value is 0}`(
        @IntForgery(min = 1, max = 15) fakeField: Int
    ) {
        // Given / When
        val result = DDSketchSerializer.signedIntFieldSize(fakeField, 0)

        // Then: proto3 default value is omitted from the wire
        assertThat(result).isEqualTo(0)
    }

    @Test
    fun `M match CodedOutputStream W signedIntFieldSize() {non-zero value}`(
        @IntForgery(min = 1, max = 15) fakeField: Int,
        @IntForgery(min = 1, max = 10000) fakeValue: Int
    ) {
        // Given / When / Then
        assertThat(DDSketchSerializer.signedIntFieldSize(fakeField, fakeValue))
            .isEqualTo(CodedOutputStream.computeSInt32Size(fakeField, fakeValue))
        assertThat(DDSketchSerializer.signedIntFieldSize(fakeField, -fakeValue))
            .isEqualTo(CodedOutputStream.computeSInt32Size(fakeField, -fakeValue))
    }

    // endregion

    // region sizeOfCompactDoubleArray

    @Test
    fun `M match CodedOutputStream W sizeOfCompactDoubleArray() {1 element}`(
        @IntForgery(min = 1, max = 15) fakeField: Int
    ) {
        // Given
        val refSize = packedDoubleFieldBytes(fakeField, doubleArrayOf(1.0)).size

        // When
        val result = DDSketchSerializer.sizeOfCompactDoubleArray(fakeField, 1)

        // Then
        assertThat(result).isEqualTo(refSize)
    }

    @Test
    fun `M scale with element count W sizeOfCompactDoubleArray()`(
        @IntForgery(min = 1, max = 15) fakeField: Int,
        @IntForgery(min = 1, max = 100) fakeCount: Int
    ) {
        // Given / When
        val size1 = DDSketchSerializer.sizeOfCompactDoubleArray(fakeField, fakeCount)
        val size2 = DDSketchSerializer.sizeOfCompactDoubleArray(fakeField, fakeCount + 1)

        // Then
        assertThat(size2 - size1).isEqualTo(8)
    }

    // endregion

    // region embeddedSize / embeddedFieldSize

    @Test
    fun `M return 1 W embeddedSize() {size 0}`() {
        // Given / When
        val result = DDSketchSerializer.embeddedSize(0)

        // Then: just the length varint byte, no payload
        assertThat(result).isEqualTo(1)
    }

    @Test
    fun `M return size plus 1 W embeddedSize() {small payload}`(
        @IntForgery(min = 1, max = 127) fakeSize: Int
    ) {
        // Given / When
        val result = DDSketchSerializer.embeddedSize(fakeSize)

        // Then: 1 byte for length varint + fakeSize payload bytes
        assertThat(result).isEqualTo(fakeSize + 1)
    }

    @Test
    fun `M return tag plus embedded W embeddedFieldSize()`(
        @IntForgery(min = 1, max = 15) fakeField: Int,
        @IntForgery(min = 1, max = 127) fakeSize: Int
    ) {
        // Given / When
        val result = DDSketchSerializer.embeddedFieldSize(fakeField, fakeSize)

        // Then
        assertThat(result)
            .isEqualTo(
                CodedOutputStream.computeTagSize(fakeField) +
                    CodedOutputStream.computeUInt32SizeNoTag(fakeSize) +
                    fakeSize
            )
    }

    // endregion

    // region writeDouble

    @Test
    fun `M write nothing W writeDouble() {value is 0}`(
        @IntForgery(min = 1, max = 15) fakeField: Int
    ) {
        // Given / When
        testedSerializer.writeDouble(fakeField, 0.0)

        // Then
        assertThat(testedSerializer.toByteArray()).isEmpty()
    }

    @Test
    fun `M produce identical bytes to CodedOutputStream W writeDouble() {non-zero value}`(
        @IntForgery(min = 1, max = 15) fakeField: Int,
        @DoubleForgery(min = 0.001, max = 1e10) fakeValue: Double
    ) {
        // Given
        val refBytes = protoBytes { it.writeDouble(fakeField, fakeValue) }

        // When
        testedSerializer.writeDouble(fakeField, fakeValue)

        // Then
        assertThat(testedSerializer.toByteArray()).isEqualTo(refBytes)
    }

    // endregion

    // region writeSignedInt32

    @Test
    fun `M write nothing W writeSignedInt32() {value 0}`(
        @IntForgery(min = 1, max = 15) fakeField: Int
    ) {
        // Given / When
        testedSerializer.writeSignedInt32(fakeField, 0)

        // Then
        assertThat(testedSerializer.toByteArray()).isEmpty()
    }

    @Test
    fun `M produce identical bytes to CodedOutputStream W writeSignedInt32() {positive value}`(
        @IntForgery(min = 1, max = 15) fakeField: Int,
        @IntForgery(min = 1, max = 10000) fakeValue: Int
    ) {
        // Given
        val refBytes = protoBytes { it.writeSInt32(fakeField, fakeValue) }

        // When
        testedSerializer.writeSignedInt32(fakeField, fakeValue)

        // Then
        assertThat(testedSerializer.toByteArray()).isEqualTo(refBytes)
    }

    @Test
    fun `M produce identical bytes to CodedOutputStream W writeSignedInt32() {negative value}`(
        @IntForgery(min = 1, max = 15) fakeField: Int,
        @IntForgery(min = -10000, max = -1) fakeValue: Int
    ) {
        // Given
        val refBytes = protoBytes { it.writeSInt32(fakeField, fakeValue) }

        // When
        testedSerializer.writeSignedInt32(fakeField, fakeValue)

        // Then
        assertThat(testedSerializer.toByteArray()).isEqualTo(refBytes)
    }

    // endregion

    // region writeCompactArray

    @Test
    fun `M produce identical bytes to CodedOutputStream W writeCompactArray() {single element}`(
        @IntForgery(min = 1, max = 15) fakeField: Int
    ) {
        // Given
        val values = doubleArrayOf(1.0)
        val refBytes = packedDoubleFieldBytes(fakeField, values)

        // When
        testedSerializer.writeCompactArray(fakeField, values, 0, 1)

        // Then
        assertThat(testedSerializer.toByteArray()).isEqualTo(refBytes)
    }

    @Test
    fun `M produce identical bytes to CodedOutputStream W writeCompactArray() {multiple elements}`(
        @IntForgery(min = 1, max = 15) fakeField: Int
    ) {
        // Given
        val values = doubleArrayOf(2.0, 3.0, 5.0)
        val refBytes = packedDoubleFieldBytes(fakeField, values)

        // When
        testedSerializer.writeCompactArray(fakeField, values, 0, 3)

        // Then
        assertThat(testedSerializer.toByteArray()).isEqualTo(refBytes)
    }

    @Test
    fun `M respect from offset W writeCompactArray()`(
        @IntForgery(min = 1, max = 15) fakeField: Int
    ) {
        // Given
        val refBytes = packedDoubleFieldBytes(fakeField, doubleArrayOf(2.0, 3.0))

        // When: index 0 (99.0) should be skipped; only indices 1 and 2 are written
        testedSerializer.writeCompactArray(fakeField, doubleArrayOf(99.0, 2.0, 3.0), 1, 2)

        // Then
        assertThat(testedSerializer.toByteArray()).isEqualTo(refBytes)
    }

    // endregion

    // region Helpers

    private fun protoBytes(write: (CodedOutputStream) -> Unit): ByteArray {
        val baos = ByteArrayOutputStream()
        val cos = CodedOutputStream.newInstance(baos)
        write(cos)
        cos.flush()
        return baos.toByteArray()
    }

    private fun packedDoubleFieldBytes(field: Int, values: DoubleArray): ByteArray {
        return protoBytes { cos ->
            cos.writeTag(field, WireFormat.WIRETYPE_LENGTH_DELIMITED)
            cos.writeUInt32NoTag(values.size * 8)
            values.forEach { cos.writeDoubleNoTag(it) }
        }
    }

    // endregion
}
