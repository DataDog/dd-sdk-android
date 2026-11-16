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
import org.mockito.kotlin.mock
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
        assertThat(checkNotNull(ProfilingBatchMetadata.fromBytesOrNull(encoded, mock())).perfetto())
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
        val restored = checkNotNull(ProfilingBatchMetadata.fromBytesOrNull(original, mock()))

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
        val restored = checkNotNull(ProfilingBatchMetadata.fromBytesOrNull(original, mock()))

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
        val restored = checkNotNull(ProfilingBatchMetadata.fromBytesOrNull(original, mock()))

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
        val restored = checkNotNull(ProfilingBatchMetadata.fromBytesOrNull(original, mock()))

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
        assertThat(ProfilingBatchMetadata.fromBytesOrNull(metadata, mock())).isNotNull()
    }

    @Test
    fun `M return null W fromBytesOrNull {empty bytes}`() {
        assertThat(ProfilingBatchMetadata.fromBytesOrNull(ByteArray(0), mock())).isNull()
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
        assertThat(ProfilingBatchMetadata.fromBytesOrNull(corrupt, mock())).isNull()
    }

    @Test
    fun `M return null W fromBytesOrNull {negative perfettoLength}`() {
        // Given — length field set to -1 (0xFFFFFFFF in big-endian)
        val negativeLength = byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte())
        val corrupt = ProfilingBatchMetadata.MAGIC + negativeLength

        // When / Then
        assertThat(ProfilingBatchMetadata.fromBytesOrNull(corrupt, mock())).isNull()
    }

    @Test
    fun `M return null W fromBytesOrNull {buffer too short to read length field}`(forge: Forge) {
        // Given — magic prefix + fewer than 4 bytes, so the Int length field can't be read
        val truncatedAfterMagic = forge.anInt(min = 1, max = 3)
        val corrupt = ProfilingBatchMetadata.MAGIC + ByteArray(truncatedAfterMagic)

        // When / Then
        assertThat(ProfilingBatchMetadata.fromBytesOrNull(corrupt, mock())).isNull()
    }

    @Test
    fun `M return null W fromBytesOrNull {random bytes without magic prefix}`(forge: Forge) {
        // Given — first byte is anything except 0x44 ('D'), so it can never match the DDCP magic prefix
        val firstByte = forge.anElementFrom(
            (Byte.MIN_VALUE.toInt() until 0x44) + (0x45..Byte.MAX_VALUE.toInt())
        ).toByte()
        val randomBytes = byteArrayOf(firstByte) + forge.aString().toByteArray()

        // When / Then
        assertThat(ProfilingBatchMetadata.fromBytesOrNull(randomBytes, mock())).isNull()
    }

    private fun encode(perfettoBytes: ByteArray, rumBytes: ByteArray): ByteArray =
        checkNotNull(ProfilingBatchMetadata.encode(ByteArrayInputStream(perfettoBytes), perfettoBytes.size, rumBytes))

    private fun ProfilingBatchMetadata.perfetto() = perfettoBytes

    private fun ProfilingBatchMetadata.rumMobileEvents() = rumMobileEventsBytes
}
