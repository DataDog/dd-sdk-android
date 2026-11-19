/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.profiling.internal.domain

import com.datadog.android.profiling.forge.Configurator
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import java.io.ByteArrayInputStream
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

@Extensions(
    ExtendWith(ForgeExtension::class)
)
@ForgeConfiguration(Configurator::class)
internal class ProfilingBatchMetadataTest {

    @Test
    fun `M produce the legacy DDCP layout W encode()`(forge: Forge) {
        // Given
        val fakePerfettoBytes = forge.aString(size = forge.anInt(min = 1, max = 4096)).toByteArray()
        val fakeRumBytes = forge.aString().toByteArray()
        val legacyLayout = ByteBuffer.allocate(8 + fakePerfettoBytes.size + fakeRumBytes.size)
            .order(ByteOrder.BIG_ENDIAN)
            .put(ProfilingBatchMetadata.MAGIC)
            .putInt(fakePerfettoBytes.size)
            .put(fakePerfettoBytes)
            .put(fakeRumBytes)
            .array()

        // When
        val encoded = encode(fakePerfettoBytes, fakeRumBytes)

        // Then
        assertThat(encoded).isEqualTo(legacyLayout)
    }

    @Test
    fun `M read the input in chunks W encode() {input returns partial reads}`(forge: Forge) {
        // Given
        val fakePerfettoBytes = forge.aString(size = forge.anInt(min = 16, max = 4096)).toByteArray()
        val chunkedInput = object : FilterInputStream(ByteArrayInputStream(fakePerfettoBytes)) {
            override fun read(b: ByteArray, off: Int, len: Int): Int = super.read(b, off, minOf(len, 7))
        }

        // When
        val encoded = checkNotNull(ProfilingBatchMetadata.encode(chunkedInput, fakePerfettoBytes.size, ByteArray(0)))

        // Then
        assertThat(checkNotNull(ProfilingBatchMetadata.fromBytesOrNull(encoded)).perfetto())
            .isEqualTo(fakePerfettoBytes)
    }

    @Test
    fun `M return null W encode() {input shorter than declared length}`(forge: Forge) {
        // Given
        val fakePerfettoBytes = forge.aString(size = forge.anInt(min = 1, max = 100)).toByteArray()

        // When
        val encoded = ProfilingBatchMetadata.encode(
            ByteArrayInputStream(fakePerfettoBytes),
            fakePerfettoBytes.size + forge.anInt(min = 1, max = 100),
            ByteArray(0)
        )

        // Then
        assertThat(encoded).isNull()
    }

    @Test
    fun `M reference the original array W fromBytesOrNull()`(forge: Forge) {
        // Given
        val fakePerfettoBytes = forge.aString().toByteArray()
        val fakeRumBytes = forge.aString().toByteArray()
        val encoded = encode(fakePerfettoBytes, fakeRumBytes)

        // When
        val restored = checkNotNull(ProfilingBatchMetadata.fromBytesOrNull(encoded))

        // Then
        assertThat(restored.bytes).isSameAs(encoded)
        assertThat(restored.perfettoOffset).isEqualTo(8)
        assertThat(restored.perfettoLength).isEqualTo(fakePerfettoBytes.size)
        assertThat(restored.rumMobileEventsOffset).isEqualTo(8 + fakePerfettoBytes.size)
        assertThat(restored.rumMobileEventsLength).isEqualTo(fakeRumBytes.size)
    }

    @Test
    fun `M read a batch written by the previous implementation W fromBytesOrNull()`(forge: Forge) {
        // Given — a blob built the way the previous implementation wrote it (ByteBuffer + toBytes)
        val fakePerfettoBytes = forge.aString(size = forge.anInt(min = 1, max = 4096)).toByteArray()
        val fakeRumBytes = forge.aString().toByteArray()

        // When
        val restored = checkNotNull(
            ProfilingBatchMetadata.fromBytesOrNull(
                legacyBlob(fakePerfettoBytes, fakeRumBytes)
            )
        )

        // Then
        assertThat(restored.perfetto()).isEqualTo(fakePerfettoBytes)
        assertThat(restored.rumMobileEvents()).isEqualTo(fakeRumBytes)
    }

    @Test
    fun `M read a batch written by the previous implementation W fromBytesOrNull() {empty rum events}`(forge: Forge) {
        // Given
        val fakePerfettoBytes = forge.aString(size = forge.anInt(min = 1, max = 4096)).toByteArray()

        // When
        val restored = checkNotNull(
            ProfilingBatchMetadata.fromBytesOrNull(legacyBlob(fakePerfettoBytes, ByteArray(0)))
        )

        // Then
        assertThat(restored.perfetto()).isEqualTo(fakePerfettoBytes)
        assertThat(restored.rumMobileEvents()).isEmpty()
    }

    @Test
    fun `M read a batch written by the previous implementation W fromBytesOrNull() {large perfetto payload}`(
        forge: Forge
    ) {
        // Given
        val fakePerfettoBytes = forge.aString(size = 65_536 + forge.anInt(min = 1, max = 1024)).toByteArray()
        val fakeRumBytes = forge.aString().toByteArray()

        // When
        val restored = checkNotNull(
            ProfilingBatchMetadata.fromBytesOrNull(
                legacyBlob(fakePerfettoBytes, fakeRumBytes)
            )
        )

        // Then
        assertThat(restored.perfetto()).isEqualTo(fakePerfettoBytes)
        assertThat(restored.rumMobileEvents()).isEqualTo(fakeRumBytes)
    }

    @Test
    fun `M keep the golden DDCP layout W fromBytesOrNull() and encode()`() {
        // Given — perfetto [1, 2, 3] and rum events "[]", spelled out byte by byte
        val goldenBlob = byteArrayOf(
            0x44, 0x44, 0x43, 0x50, // "DDCP"
            0x00, 0x00, 0x00, 0x03, // perfetto length, big-endian
            0x01, 0x02, 0x03, // perfetto payload
            0x5B, 0x5D // "[]"
        )

        // When
        val restored = checkNotNull(ProfilingBatchMetadata.fromBytesOrNull(goldenBlob))

        // Then
        assertThat(restored.perfetto()).isEqualTo(byteArrayOf(0x01, 0x02, 0x03))
        assertThat(restored.rumMobileEvents()).isEqualTo("[]".toByteArray())
        assertThat(encode(byteArrayOf(0x01, 0x02, 0x03), "[]".toByteArray())).isEqualTo(goldenBlob)
    }

    @Test
    fun `M return null W encode() {input longer than declared length}`(forge: Forge) {
        // Given
        val fakePerfettoBytes = forge.aString(size = forge.anInt(min = 2, max = 100)).toByteArray()

        // When
        val encoded = ProfilingBatchMetadata.encode(
            ByteArrayInputStream(fakePerfettoBytes),
            fakePerfettoBytes.size - 1,
            ByteArray(0)
        )

        // Then
        assertThat(encoded).isNull()
    }

    @Test
    fun `M propagate IOException W encode() {input fails}`(forge: Forge) {
        // Given
        val fakeException = IOException(forge.anAlphabeticalString())
        val failingInput = object : InputStream() {
            override fun read(): Int = throw fakeException
            override fun read(b: ByteArray, off: Int, len: Int): Int = throw fakeException
        }

        // When / Then
        assertThatThrownBy {
            ProfilingBatchMetadata.encode(failingInput, forge.anInt(min = 1, max = 100), ByteArray(0))
        }.isSameAs(fakeException)
    }

    @Test
    fun `M preserve perfetto bytes W encode + fromBytesOrNull`(forge: Forge) {
        // Given
        val fakePerfettoBytes = forge.aString().toByteArray()
        val fakeRumBytes = forge.aString().toByteArray()
        val original = encode(fakePerfettoBytes, fakeRumBytes)

        // When
        val restored = checkNotNull(ProfilingBatchMetadata.fromBytesOrNull(original))

        // Then
        assertThat(restored.perfetto()).isEqualTo(fakePerfettoBytes)
    }

    @Test
    fun `M preserve rum mobile events bytes W encode + fromBytesOrNull`(forge: Forge) {
        // Given
        val fakePerfettoBytes = forge.aString().toByteArray()
        val fakeRumBytes = forge.aString().toByteArray()
        val original = encode(fakePerfettoBytes, fakeRumBytes)

        // When
        val restored = checkNotNull(ProfilingBatchMetadata.fromBytesOrNull(original))

        // Then
        assertThat(restored.rumMobileEvents()).isEqualTo(fakeRumBytes)
    }

    @Test
    fun `M preserve all bytes W encode + fromBytesOrNull {large perfetto payload}`(forge: Forge) {
        // Given
        val fakePerfettoBytes = forge.aString(size = 100_000).toByteArray()
        val fakeRumBytes = forge.aString().toByteArray()
        val original = encode(fakePerfettoBytes, fakeRumBytes)

        // When
        val restored = checkNotNull(ProfilingBatchMetadata.fromBytesOrNull(original))

        // Then
        assertThat(restored.perfetto()).isEqualTo(fakePerfettoBytes)
        assertThat(restored.rumMobileEvents()).isEqualTo(fakeRumBytes)
    }

    @Test
    fun `M preserve all bytes W encode + fromBytesOrNull {empty rum events}`(forge: Forge) {
        // Given
        val fakePerfettoBytes = forge.aString().toByteArray()
        val original = encode(fakePerfettoBytes, ByteArray(0))

        // When
        val restored = checkNotNull(ProfilingBatchMetadata.fromBytesOrNull(original))

        // Then
        assertThat(restored.perfetto()).isEqualTo(fakePerfettoBytes)
        assertThat(restored.rumMobileEvents()).isEmpty()
    }

    @Test
    fun `M return non-null W fromBytesOrNull {bytes from encode}`(forge: Forge) {
        // Given
        val metadata = encode(
            forge.aString().toByteArray(),
            forge.aString().toByteArray()
        )

        // When / Then
        assertThat(ProfilingBatchMetadata.fromBytesOrNull(metadata)).isNotNull()
    }

    @Test
    fun `M return null W fromBytesOrNull {empty bytes}`() {
        assertThat(ProfilingBatchMetadata.fromBytesOrNull(ByteArray(0))).isNull()
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
        assertThat(ProfilingBatchMetadata.fromBytesOrNull(corrupt)).isNull()
    }

    @Test
    fun `M return null W fromBytesOrNull {negative perfettoLength}`() {
        // Given — length field set to -1 (0xFFFFFFFF in big-endian)
        val negativeLength = byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte())
        val corrupt = ProfilingBatchMetadata.MAGIC + negativeLength

        // When / Then
        assertThat(ProfilingBatchMetadata.fromBytesOrNull(corrupt)).isNull()
    }

    @Test
    fun `M return null W fromBytesOrNull {buffer too short to read length field}`(forge: Forge) {
        // Given — magic prefix + fewer than 4 bytes, so the Int length field can't be read
        val truncatedAfterMagic = forge.anInt(min = 1, max = 3)
        val corrupt = ProfilingBatchMetadata.MAGIC + ByteArray(truncatedAfterMagic)

        // When / Then
        assertThat(ProfilingBatchMetadata.fromBytesOrNull(corrupt)).isNull()
    }

    @Test
    fun `M return null W fromBytesOrNull {random bytes without magic prefix}`(forge: Forge) {
        // Given — first byte is anything except 0x44 ('D'), so it can never match the DDCP magic prefix
        val firstByte = forge.anElementFrom(
            (Byte.MIN_VALUE.toInt() until 0x44) + (0x45..Byte.MAX_VALUE.toInt())
        ).toByte()
        val randomBytes = byteArrayOf(firstByte) + forge.aString().toByteArray()

        // When / Then
        assertThat(ProfilingBatchMetadata.fromBytesOrNull(randomBytes)).isNull()
    }

    // Spelled out independently of the production code, the way the previous implementation built it.
    private fun legacyBlob(perfettoBytes: ByteArray, rumBytes: ByteArray): ByteArray =
        ByteBuffer.allocate(4 + Int.SIZE_BYTES + perfettoBytes.size + rumBytes.size)
            .order(ByteOrder.BIG_ENDIAN)
            .put(byteArrayOf(0x44, 0x44, 0x43, 0x50))
            .putInt(perfettoBytes.size)
            .put(perfettoBytes)
            .put(rumBytes)
            .array()

    private fun encode(perfettoBytes: ByteArray, rumBytes: ByteArray): ByteArray =
        checkNotNull(ProfilingBatchMetadata.encode(ByteArrayInputStream(perfettoBytes), perfettoBytes.size, rumBytes))

    private fun ProfilingBatchMetadata.perfetto() =
        bytes.copyOfRange(perfettoOffset, perfettoOffset + perfettoLength)

    private fun ProfilingBatchMetadata.rumMobileEvents() =
        bytes.copyOfRange(rumMobileEventsOffset, rumMobileEventsOffset + rumMobileEventsLength)
}
