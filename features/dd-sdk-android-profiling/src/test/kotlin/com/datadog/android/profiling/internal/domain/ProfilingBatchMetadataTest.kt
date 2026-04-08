/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.profiling.internal.domain

import com.datadog.android.api.InternalLogger
import com.datadog.android.profiling.forge.Configurator
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
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
internal class ProfilingBatchMetadataTest {

    @Mock
    private lateinit var mockInternalLogger: InternalLogger

    @Test
    fun `M preserve perfetto bytes W toBytes + fromBytesOrNull`(forge: Forge) {
        // Given
        val fakePerfettoBytes = forge.aString().toByteArray()
        val fakeRumBytes = forge.aString().toByteArray()
        val original = ProfilingBatchMetadata(fakePerfettoBytes, fakeRumBytes)

        // When
        val restored = checkNotNull(ProfilingBatchMetadata.fromBytesOrNull(original.toBytes(), mockInternalLogger))

        // Then
        assertThat(restored.perfettoBytes).isEqualTo(fakePerfettoBytes)
    }

    @Test
    fun `M preserve rum mobile events bytes W toBytes + fromBytesOrNull`(forge: Forge) {
        // Given
        val fakePerfettoBytes = forge.aString().toByteArray()
        val fakeRumBytes = forge.aString().toByteArray()
        val original = ProfilingBatchMetadata(fakePerfettoBytes, fakeRumBytes)

        // When
        val restored = checkNotNull(ProfilingBatchMetadata.fromBytesOrNull(original.toBytes(), mockInternalLogger))

        // Then
        assertThat(restored.rumMobileEventsBytes).isEqualTo(fakeRumBytes)
    }

    @Test
    fun `M preserve all bytes W toBytes + fromBytesOrNull {large perfetto payload}`(forge: Forge) {
        // Given
        val fakePerfettoBytes = forge.aString(size = 100_000).toByteArray()
        val fakeRumBytes = forge.aString().toByteArray()
        val original = ProfilingBatchMetadata(fakePerfettoBytes, fakeRumBytes)

        // When
        val restored = checkNotNull(ProfilingBatchMetadata.fromBytesOrNull(original.toBytes(), mockInternalLogger))

        // Then
        assertThat(restored.perfettoBytes).isEqualTo(fakePerfettoBytes)
        assertThat(restored.rumMobileEventsBytes).isEqualTo(fakeRumBytes)
    }

    @Test
    fun `M preserve all bytes W toBytes + fromBytesOrNull {empty rum events}`(forge: Forge) {
        // Given
        val fakePerfettoBytes = forge.aString().toByteArray()
        val original = ProfilingBatchMetadata(fakePerfettoBytes, ByteArray(0))

        // When
        val restored = checkNotNull(ProfilingBatchMetadata.fromBytesOrNull(original.toBytes(), mockInternalLogger))

        // Then
        assertThat(restored.perfettoBytes).isEqualTo(fakePerfettoBytes)
        assertThat(restored.rumMobileEventsBytes).isEmpty()
    }

    @Test
    fun `M return non-null W fromBytesOrNull {bytes from toBytes}`(forge: Forge) {
        // Given
        val metadata = ProfilingBatchMetadata(
            forge.aString().toByteArray(),
            forge.aString().toByteArray()
        ).toBytes()

        // When / Then
        assertThat(ProfilingBatchMetadata.fromBytesOrNull(metadata, mockInternalLogger)).isNotNull()
    }

    @Test
    fun `M return null W fromBytesOrNull {empty bytes}`() {
        assertThat(ProfilingBatchMetadata.fromBytesOrNull(ByteArray(0), mockInternalLogger)).isNull()
    }

    @Test
    fun `M return null W fromBytesOrNull {perfettoLength exceeds remaining bytes}`(forge: Forge) {
        // Given — length field claims more bytes than the buffer actually holds
        val actualPayloadSize = forge.anInt(min = 0, max = 100)
        val claimedLength = actualPayloadSize + forge.anInt(min = 1, max = 100)
        val corrupt = ProfilingBatchMetadata.MAGIC +
            byteArrayOf(
                (claimedLength shr 24).toByte(),
                (claimedLength shr 16).toByte(),
                (claimedLength shr 8).toByte(),
                claimedLength.toByte()
            ) + ByteArray(actualPayloadSize)

        // When / Then
        assertThat(ProfilingBatchMetadata.fromBytesOrNull(corrupt, mockInternalLogger)).isNull()
    }

    @Test
    fun `M return null W fromBytesOrNull {negative perfettoLength}`() {
        // Given — length field set to -1 (0xFFFFFFFF in big-endian)
        val negativeLength = byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte())
        val corrupt = ProfilingBatchMetadata.MAGIC + negativeLength

        // When / Then
        assertThat(ProfilingBatchMetadata.fromBytesOrNull(corrupt, mockInternalLogger)).isNull()
    }

    @Test
    fun `M return null W fromBytesOrNull {buffer too short to read length field}`(forge: Forge) {
        // Given — magic prefix + fewer than 4 bytes, so the Int length field can't be read
        val truncatedAfterMagic = forge.anInt(min = 1, max = 3)
        val corrupt = ProfilingBatchMetadata.MAGIC + ByteArray(truncatedAfterMagic)

        // When / Then
        assertThat(ProfilingBatchMetadata.fromBytesOrNull(corrupt, mockInternalLogger)).isNull()
    }

    @Test
    fun `M return null W fromBytesOrNull {random bytes without magic prefix}`(forge: Forge) {
        // Given — first byte is anything except 0x44 ('D'), so it can never match the DDCP magic prefix
        val firstByte = forge.anElementFrom(
            (Byte.MIN_VALUE.toInt() until 0x44) + (0x45..Byte.MAX_VALUE.toInt())
        ).toByte()
        val randomBytes = byteArrayOf(firstByte) + forge.aString().toByteArray()

        // When / Then
        assertThat(ProfilingBatchMetadata.fromBytesOrNull(randomBytes, mockInternalLogger)).isNull()
    }
}
