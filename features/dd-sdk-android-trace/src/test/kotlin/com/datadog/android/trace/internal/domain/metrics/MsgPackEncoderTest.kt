/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.trace.internal.domain.metrics

import com.datadog.android.utils.forge.Configurator
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.annotation.LongForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.msgpack.core.MessagePack
import org.msgpack.core.MessageUnpacker

@ExtendWith(ForgeExtension::class)
@ForgeConfiguration(Configurator::class)
internal class MsgPackEncoderTest {

    private lateinit var testedWriter: MsgPackEncoder

    @BeforeEach
    fun setUp() {
        testedWriter = MsgPackEncoder()
    }

    @Test
    fun `M encode nil W writeNull()`() {
        // When
        testedWriter.writeNull()

        // Then
        assertThat(testedWriter.firstByte()).isEqualTo(0xC0)
        assertThat(testedWriter.unpackMessage().unpackValue().isNilValue).isTrue()
    }

    @Test
    fun `M encode true W writeBoolean() {value is true}`() {
        // When
        testedWriter.writeBoolean(true)

        // Then
        assertThat(testedWriter.firstByte()).isEqualTo(0xC3)
        assertThat(testedWriter.unpackMessage().unpackBoolean()).isTrue()
    }

    @Test
    fun `M encode false W writeBoolean() {value is false}`() {
        // When
        testedWriter.writeBoolean(false)

        // Then
        assertThat(testedWriter.firstByte()).isEqualTo(0xC2)
        assertThat(testedWriter.unpackMessage().unpackBoolean()).isFalse()
    }

    @Test
    fun `M encode as fixstr W writeString() {1 to 31 bytes}`(
        @StringForgery(regex = "[a-z]{1,31}") fakeString: String
    ) {
        // When
        testedWriter.writeString(fakeString)

        // Then
        assertThat(testedWriter.firstByte()).isBetween(0xA0, 0xBF)
        assertThat(testedWriter.unpackMessage().unpackString()).isEqualTo(fakeString)
    }

    @Test
    fun `M encode as fixstr W writeString() {empty}`() {
        // When
        testedWriter.writeString("")

        // Then
        assertThat(testedWriter.firstByte()).isEqualTo(0xA0)
        assertThat(testedWriter.unpackMessage().unpackString()).isEmpty()
    }

    @Test
    fun `M encode as str8 W writeString() {multibyte chars crossing fixstr boundary}`(
        @StringForgery(regex = "[Ā-ǿ]{16,20}") fakeString: String
    ) {
        // When
        testedWriter.writeString(fakeString)

        // Then — 16-20 chars but 32-40 bytes, so str8 not fixstr
        assertThat(testedWriter.firstByte()).isEqualTo(0xD9)
        assertThat(testedWriter.unpackMessage().unpackString()).isEqualTo(fakeString)
    }

    @Test
    fun `M encode as str8 W writeString() {2^5 to 2^8 bytes}`(
        @StringForgery(regex = "[a-z]{32,255}") fakeString: String
    ) {
        // When
        testedWriter.writeString(fakeString)

        // Then
        assertThat(testedWriter.firstByte()).isEqualTo(0xD9)
        assertThat(testedWriter.unpackMessage().unpackString()).isEqualTo(fakeString)
    }

    @Test
    fun `M encode as str16 W writeString() {2^8 to 2^16 bytes}`(
        @StringForgery(regex = "[a-z]{256,1000}") fakeString: String
    ) {
        // When
        testedWriter.writeString(fakeString)

        // Then
        assertThat(testedWriter.firstByte()).isEqualTo(0xDA)
        assertThat(testedWriter.unpackMessage().unpackString()).isEqualTo(fakeString)
    }

    @Test
    fun `M encode as str32 W writeString() {gt 2^16 bytes}`(
        @IntForgery(min = 65536, max = 65540) fakeSize: Int
    ) {
        // When
        testedWriter.writeString("a".repeat(fakeSize))

        // Then
        assertThat(testedWriter.firstByte()).isEqualTo(0xDB)
    }

    @Test
    fun `M encode nil W writeString() {null}`() {
        // When
        testedWriter.writeString(null)

        // Then
        assertThat(testedWriter.firstByte()).isEqualTo(0xC0)
        assertThat(testedWriter.unpackMessage().unpackValue().isNilValue).isTrue()
    }

    @Test
    fun `M encode string W writeRawString() {multibyte UTF-8 bytes}`(
        @StringForgery(regex = "[Ā-ǿ]{1,10}") fakeString: String
    ) {
        // Given
        val fakeUtf8 = fakeString.toByteArray(Charsets.UTF_8)

        // When
        testedWriter.writeRawString(fakeUtf8)

        // Then
        assertThat(testedWriter.unpackMessage().unpackString()).isEqualTo(fakeString)
    }

    @Test
    fun `M write bytes verbatim W appendRawBytes()`(
        @StringForgery fakeString: String
    ) {
        // Given — pre-encode a known value so we can assert the outer envelope sees it correctly
        val preEncoded = MsgPackEncoder().also { it.writeString(fakeString) }.getBytes()

        // When
        testedWriter.appendRawBytes(preEncoded)

        // Then — bytes are written with no extra wrapper, decode as the original string
        assertThat(testedWriter.unpackMessage().unpackString()).isEqualTo(fakeString)
    }

    @Test
    fun `M encode binary W writeBinary()`(
        @StringForgery fakeString: String
    ) {
        // Given
        val fakeBinary = fakeString.toByteArray()

        // When
        testedWriter.writeBinary(fakeBinary)

        // Then
        assertThat(testedWriter.firstByte()).isEqualTo(0xC4)
        val unpacker = testedWriter.unpackMessage()
        assertThat(unpacker.readPayload(unpacker.unpackBinaryHeader())).isEqualTo(fakeBinary)
    }

    @Test
    fun `M encode as bin16 W writeBinary() {UByte MAX_VALUE + 1 to UShort MAX_VALUE bytes}`(
        @IntForgery(min = 256, max = 1000) fakeSize: Int
    ) {
        // When
        testedWriter.writeBinary(ByteArray(fakeSize))

        // Then
        assertThat(testedWriter.firstByte()).isEqualTo(0xC5)
    }

    @Test
    fun `M encode as bin32 W writeBinary() {UShort MAX_VALUE + 1 bytes}`(
        @IntForgery(min = 65536, max = 65540) fakeSize: Int
    ) {
        // When
        testedWriter.writeBinary(ByteArray(fakeSize))

        // Then
        assertThat(testedWriter.firstByte()).isEqualTo(0xC6)
    }

    @Test
    fun `M encode as fixint W writeInt() {0 to Byte MAX_VALUE}`(
        @IntForgery(min = 0, max = 128) fakeInt: Int
    ) {
        // When
        testedWriter.writeInt(fakeInt)

        // Then
        assertThat(testedWriter.firstByte()).isBetween(0x00, 0x7F)
        assertThat(testedWriter.unpackMessage().unpackInt()).isEqualTo(fakeInt)
    }

    @Test
    fun `M encode as uint8 W writeInt() {Byte MAX_VALUE + 1 to UByte MAX_VALUE}`(
        @IntForgery(min = 128, max = 256) fakeInt: Int
    ) {
        // When
        testedWriter.writeInt(fakeInt)

        // Then
        assertThat(testedWriter.firstByte()).isEqualTo(0xCC)
        assertThat(testedWriter.unpackMessage().unpackInt()).isEqualTo(fakeInt)
    }

    @Test
    fun `M encode as uint16 W writeInt() {UByte MAX_VALUE + 1 to UShort MAX_VALUE}`(
        @IntForgery(min = 256, max = 65536) fakeInt: Int
    ) {
        // When
        testedWriter.writeInt(fakeInt)

        // Then
        assertThat(testedWriter.firstByte()).isEqualTo(0xCD)
        assertThat(testedWriter.unpackMessage().unpackInt()).isEqualTo(fakeInt)
    }

    @Test
    fun `M encode as uint32 W writeInt() {UShort MAX_VALUE + 1 to Int MAX_VALUE}`(
        @IntForgery(min = 65536) fakeInt: Int
    ) {
        // When
        testedWriter.writeInt(fakeInt)

        // Then
        assertThat(testedWriter.firstByte()).isEqualTo(0xCE)
        assertThat(testedWriter.unpackMessage().unpackInt()).isEqualTo(fakeInt)
    }

    @Test
    fun `M encode as negfixnum W writeInt() {-32 to -1}`(
        @IntForgery(min = -32, max = 0) fakeInt: Int
    ) {
        // When
        testedWriter.writeInt(fakeInt)

        // Then
        assertThat(testedWriter.firstByte()).isBetween(0xE0, 0xFF)
        assertThat(testedWriter.unpackMessage().unpackInt()).isEqualTo(fakeInt)
    }

    @Test
    fun `M encode as int8 W writeInt() {Byte MIN_VALUE to -33}`(
        @IntForgery(min = -128, max = -32) fakeInt: Int
    ) {
        // When
        testedWriter.writeInt(fakeInt)

        // Then
        assertThat(testedWriter.firstByte()).isEqualTo(0xD0)
        assertThat(testedWriter.unpackMessage().unpackInt()).isEqualTo(fakeInt)
    }

    @Test
    fun `M encode as int16 W writeInt() {Short MIN_VALUE to Byte MIN_VALUE - 1}`(
        @IntForgery(min = -32768, max = -128) fakeInt: Int
    ) {
        // When
        testedWriter.writeInt(fakeInt)

        // Then
        assertThat(testedWriter.firstByte()).isEqualTo(0xD1)
        assertThat(testedWriter.unpackMessage().unpackInt()).isEqualTo(fakeInt)
    }

    @Test
    fun `M encode as int32 W writeInt() {Int MIN_VALUE to Short MIN_VALUE - 1}`(
        @IntForgery(max = -32768) fakeInt: Int
    ) {
        // When
        testedWriter.writeInt(fakeInt)

        // Then
        assertThat(testedWriter.firstByte()).isEqualTo(0xD2)
        assertThat(testedWriter.unpackMessage().unpackInt()).isEqualTo(fakeInt)
    }

    @Test
    fun `M encode as fixint W writeLong() {0 to Byte MAX_VALUE}`(
        @LongForgery(min = 0L, max = 128L) fakeLong: Long
    ) {
        // When
        testedWriter.writeLong(fakeLong)

        // Then
        assertThat(testedWriter.firstByte()).isBetween(0x00, 0x7F)
        assertThat(testedWriter.unpackMessage().unpackLong()).isEqualTo(fakeLong)
    }

    @Test
    fun `M encode as uint8 W writeLong() {Byte MAX_VALUE + 1 to UByte MAX_VALUE}`(
        @LongForgery(min = 128L, max = 256L) fakeLong: Long
    ) {
        // When
        testedWriter.writeLong(fakeLong)

        // Then
        assertThat(testedWriter.firstByte()).isEqualTo(0xCC)
        assertThat(testedWriter.unpackMessage().unpackLong()).isEqualTo(fakeLong)
    }

    @Test
    fun `M encode as uint16 W writeLong() {UByte MAX_VALUE + 1 to UShort MAX_VALUE}`(
        @LongForgery(min = 256L, max = 65536L) fakeLong: Long
    ) {
        // When
        testedWriter.writeLong(fakeLong)

        // Then
        assertThat(testedWriter.firstByte()).isEqualTo(0xCD)
        assertThat(testedWriter.unpackMessage().unpackLong()).isEqualTo(fakeLong)
    }

    @Test
    fun `M encode as uint32 W writeLong() {UShort MAX_VALUE + 1 to UInt MAX_VALUE}`(
        @LongForgery(min = 65536L, max = 4294967296L) fakeLong: Long
    ) {
        // When
        testedWriter.writeLong(fakeLong)

        // Then
        assertThat(testedWriter.firstByte()).isEqualTo(0xCE)
        assertThat(testedWriter.unpackMessage().unpackLong()).isEqualTo(fakeLong)
    }

    @Test
    fun `M encode as uint64 W writeLong() {UInt MAX_VALUE + 1 to Long MAX_VALUE}`(
        @LongForgery(min = 4294967296L) fakeLong: Long
    ) {
        // When
        testedWriter.writeLong(fakeLong)

        // Then
        assertThat(testedWriter.firstByte()).isEqualTo(0xCF)
        assertThat(testedWriter.unpackMessage().unpackLong()).isEqualTo(fakeLong)
    }

    @Test
    fun `M encode as negfixnum W writeLong() {-32 to -1}`(
        @LongForgery(min = -32L, max = 0L) fakeLong: Long
    ) {
        // When
        testedWriter.writeLong(fakeLong)

        // Then
        assertThat(testedWriter.firstByte()).isBetween(0xE0, 0xFF)
        assertThat(testedWriter.unpackMessage().unpackLong()).isEqualTo(fakeLong)
    }

    @Test
    fun `M encode as int8 W writeLong() {Byte MIN_VALUE to -33}`(
        @LongForgery(min = -128L, max = -32L) fakeLong: Long
    ) {
        // When
        testedWriter.writeLong(fakeLong)

        // Then
        assertThat(testedWriter.firstByte()).isEqualTo(0xD0)
        assertThat(testedWriter.unpackMessage().unpackLong()).isEqualTo(fakeLong)
    }

    @Test
    fun `M encode as int16 W writeLong() {Short MIN_VALUE to Byte MIN_VALUE - 1}`(
        @LongForgery(min = -32768L, max = -128L) fakeLong: Long
    ) {
        // When
        testedWriter.writeLong(fakeLong)

        // Then
        assertThat(testedWriter.firstByte()).isEqualTo(0xD1)
        assertThat(testedWriter.unpackMessage().unpackLong()).isEqualTo(fakeLong)
    }

    @Test
    fun `M encode as int32 W writeLong() {Int MIN_VALUE to Short MIN_VALUE - 1}`(
        @LongForgery(min = -2147483648L, max = -32768L) fakeLong: Long
    ) {
        // When
        testedWriter.writeLong(fakeLong)

        // Then
        assertThat(testedWriter.firstByte()).isEqualTo(0xD2)
        assertThat(testedWriter.unpackMessage().unpackLong()).isEqualTo(fakeLong)
    }

    @Test
    fun `M encode as int64 W writeLong() {Long MIN_VALUE to Int MIN_VALUE - 1}`(
        @LongForgery(max = -2147483648L) fakeLong: Long
    ) {
        // When
        testedWriter.writeLong(fakeLong)

        // Then
        assertThat(testedWriter.firstByte()).isEqualTo(0xD3)
        assertThat(testedWriter.unpackMessage().unpackLong()).isEqualTo(fakeLong)
    }

    @Test
    fun `M encode fixarray header W startArray() {count less than 16}`(
        @IntForgery(min = 0, max = 16) fakeCount: Int
    ) {
        // When
        testedWriter.startArray(fakeCount)

        // Then
        assertThat(testedWriter.firstByte()).isBetween(0x90, 0x9F)
        assertThat(testedWriter.unpackMessage().unpackArrayHeader()).isEqualTo(fakeCount)
    }

    @Test
    fun `M encode array16 header W startArray() {16 to 65535}`(
        @IntForgery(min = 16, max = 65536) fakeCount: Int
    ) {
        // When
        testedWriter.startArray(fakeCount)

        // Then
        assertThat(testedWriter.firstByte()).isEqualTo(0xDC)
        assertThat(testedWriter.unpackMessage().unpackArrayHeader()).isEqualTo(fakeCount)
    }

    @Test
    fun `M encode array32 header W startArray() {65536 and above}`(
        @IntForgery(min = 65536) fakeCount: Int
    ) {
        // When
        testedWriter.startArray(fakeCount)

        // Then
        assertThat(testedWriter.firstByte()).isEqualTo(0xDD)
        assertThat(testedWriter.unpackMessage().unpackArrayHeader()).isEqualTo(fakeCount)
    }

    @Test
    fun `M encode fixmap header W startMap() {count less than 16}`(
        @IntForgery(min = 0, max = 16) fakeCount: Int
    ) {
        // When
        testedWriter.startMap(fakeCount)

        // Then
        assertThat(testedWriter.firstByte()).isBetween(0x80, 0x8F)
        assertThat(testedWriter.unpackMessage().unpackMapHeader()).isEqualTo(fakeCount)
    }

    @Test
    fun `M encode map16 header W startMap() {16 to 65535}`(
        @IntForgery(min = 16, max = 65536) fakeCount: Int
    ) {
        // When
        testedWriter.startMap(fakeCount)

        // Then
        assertThat(testedWriter.firstByte()).isEqualTo(0xDE)
        assertThat(testedWriter.unpackMessage().unpackMapHeader()).isEqualTo(fakeCount)
    }

    @Test
    fun `M encode map32 header W startMap() {65536 and above}`(
        @IntForgery(min = 65536) fakeCount: Int
    ) {
        // When
        testedWriter.startMap(fakeCount)

        // Then
        assertThat(testedWriter.firstByte()).isEqualTo(0xDF)
        assertThat(testedWriter.unpackMessage().unpackMapHeader()).isEqualTo(fakeCount)
    }

    private fun MsgPackEncoder.firstByte(): Int = getBytes()[0].toInt() and 0xFF

    private fun MsgPackEncoder.unpackMessage(): MessageUnpacker =
        MessagePack.newDefaultUnpacker(getBytes())
}
