/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.trace.internal.domain.metrics

import java.io.ByteArrayOutputStream

/**
 * Sub-set of the MsgPack specification required to encode [StatsPayload].
 * See https://github.com/msgpack/msgpack/blob/master/spec.md
 */
@Suppress("TooManyFunctions")
internal class MsgPackEncoder {
    private val buffer = ByteArrayOutputStream()

    fun getBytes(): ByteArray = buffer.toByteArray()

    fun writeNull() {
        buffer.write(NULL)
    }

    fun writeBoolean(value: Boolean) {
        buffer.write(if (value) TRUE else FALSE)
    }

    fun writeString(str: String?) {
        if (str == null) {
            writeNull()
        } else {
            writeRawString(str.toByteArray(Charsets.UTF_8))
        }
    }

    /**
     * Writes the raw byte array of a String already in UTF-8 format.
     */
    fun writeRawString(utf8String: ByteArray) {
        writeStringHeader(utf8String.size)
        @Suppress("UnsafeThirdPartyFunctionCall") // safe - non-null array
        buffer.write(utf8String)
    }

    fun writeBinary(binary: ByteArray) {
        writeBinaryHeader(binary.size)
        @Suppress("UnsafeThirdPartyFunctionCall") // safe - non-null array
        buffer.write(binary)
    }

    fun writeInt(value: Int) {
        if (value < 0) {
            val numOfLeadingOnes = value.inv().countLeadingZeroBits()
            when {
                numOfLeadingOnes <= INT_NEG_INT32_THRESHOLD -> {
                    buffer.write(INT32)
                    putInt(value)
                }

                numOfLeadingOnes <= INT_NEG_INT16_THRESHOLD -> {
                    buffer.write(INT16)
                    putShort(value.toShort())
                }

                numOfLeadingOnes <= INT_NEG_INT8_THRESHOLD -> {
                    buffer.write(INT8)
                    buffer.write(value)
                }

                else -> {
                    buffer.write(NEGFIXNUM or value)
                }
            }
        } else {
            val numOfLeadingZeros = value.countLeadingZeroBits()
            when {
                numOfLeadingZeros <= INT_POS_UINT32_THRESHOLD -> {
                    buffer.write(UINT32)
                    putInt(value)
                }

                numOfLeadingZeros <= INT_POS_UINT16_THRESHOLD -> {
                    buffer.write(UINT16)
                    putShort(value.toShort())
                }

                numOfLeadingZeros <= INT_POS_UINT8_THRESHOLD -> {
                    buffer.write(UINT8)
                    buffer.write(value)
                }

                else -> {
                    buffer.write(value)
                }
            }
        }
    }

    fun writeLong(value: Long) {
        if (value < 0) {
            val numOfLeadingOnes = value.inv().countLeadingZeroBits()
            when {
                numOfLeadingOnes <= LONG_NEG_INT64_THRESHOLD -> {
                    buffer.write(INT64)
                    putLong(value)
                }

                numOfLeadingOnes <= LONG_NEG_INT32_THRESHOLD -> {
                    buffer.write(INT32)
                    putInt(value.toInt())
                }

                numOfLeadingOnes <= LONG_NEG_INT16_THRESHOLD -> {
                    buffer.write(INT16)
                    putShort(value.toShort())
                }

                numOfLeadingOnes <= LONG_NEG_INT8_THRESHOLD -> {
                    buffer.write(INT8)
                    buffer.write(value.toInt())
                }

                else -> {
                    buffer.write((NEGFIXNUM.toLong() or value).toInt())
                }
            }
        } else {
            val numOfLeadingZeros = value.countLeadingZeroBits()
            when {
                numOfLeadingZeros <= LONG_POS_UINT64_THRESHOLD -> {
                    buffer.write(UINT64)
                    putLong(value)
                }

                numOfLeadingZeros <= LONG_POS_UINT32_THRESHOLD -> {
                    buffer.write(UINT32)
                    putInt(value.toInt())
                }

                numOfLeadingZeros <= LONG_POS_UINT16_THRESHOLD -> {
                    buffer.write(UINT16)
                    putShort(value.toShort())
                }

                numOfLeadingZeros <= LONG_POS_UINT8_THRESHOLD -> {
                    buffer.write(UINT8)
                    buffer.write(value.toInt())
                }

                else -> {
                    buffer.write(value.toInt())
                }
            }
        }
    }

    fun startMap(elementCount: Int) {
        when {
            elementCount <= FIX_COLLECTION_MAX_SIZE -> {
                buffer.write(FIXMAP or elementCount)
            }

            elementCount <= TWO_BYTE_MAX_LENGTH -> {
                buffer.write(MAP16)
                putShort(elementCount.toShort())
            }

            else -> {
                buffer.write(MAP32)
                putInt(elementCount)
            }
        }
    }

    fun startArray(elementCount: Int) {
        when {
            elementCount <= FIX_COLLECTION_MAX_SIZE -> {
                buffer.write(FIXARRAY or elementCount)
            }

            elementCount <= TWO_BYTE_MAX_LENGTH -> {
                buffer.write(ARRAY16)
                putShort(elementCount.toShort())
            }

            else -> {
                buffer.write(ARRAY32)
                putInt(elementCount)
            }
        }
    }

    private fun writeStringHeader(length: Int) {
        when {
            length <= FIX_STR_MAX_BYTES -> {
                buffer.write(FIXSTR or length)
            }

            length <= ONE_BYTE_MAX_LENGTH -> {
                buffer.write(STR8)
                buffer.write(length)
            }

            length <= TWO_BYTE_MAX_LENGTH -> {
                buffer.write(STR16)
                putShort(length.toShort())
            }

            else -> {
                buffer.write(STR32)
                putInt(length)
            }
        }
    }

    private fun writeBinaryHeader(length: Int) {
        when {
            length <= ONE_BYTE_MAX_LENGTH -> {
                buffer.write(BIN8)
                buffer.write(length)
            }

            length <= TWO_BYTE_MAX_LENGTH -> {
                buffer.write(BIN16)
                putShort(length.toShort())
            }

            else -> {
                buffer.write(BIN32)
                putInt(length)
            }
        }
    }

    private fun putShort(value: Short) {
        val v = value.toInt()
        buffer.write(v ushr SHIFT_8)
        buffer.write(v)
    }

    private fun putInt(value: Int) {
        buffer.write(value ushr SHIFT_24)
        buffer.write(value ushr SHIFT_16)
        buffer.write(value ushr SHIFT_8)
        buffer.write(value)
    }

    private fun putLong(value: Long) {
        putInt((value ushr SHIFT_32).toInt())
        putInt(value.toInt())
    }

    private companion object {
        private const val NULL = 0xC0

        // Bit-shift amounts for big-endian byte extraction
        private const val SHIFT_8 = 8
        private const val SHIFT_16 = 16
        private const val SHIFT_24 = 24
        private const val SHIFT_32 = 32

        // Number of leading same-sign bits at which the 32-bit integer encoding widens
        private const val INT_NEG_INT32_THRESHOLD = 16
        private const val INT_NEG_INT16_THRESHOLD = 24
        private const val INT_NEG_INT8_THRESHOLD = 26
        private const val INT_POS_UINT32_THRESHOLD = 15
        private const val INT_POS_UINT16_THRESHOLD = 23
        private const val INT_POS_UINT8_THRESHOLD = 24

        // Number of leading same-sign bits at which the 64-bit integer encoding widens
        private const val LONG_NEG_INT64_THRESHOLD = 32
        private const val LONG_NEG_INT32_THRESHOLD = 48
        private const val LONG_NEG_INT16_THRESHOLD = 56
        private const val LONG_NEG_INT8_THRESHOLD = 58
        private const val LONG_POS_UINT64_THRESHOLD = 31
        private const val LONG_POS_UINT32_THRESHOLD = 47
        private const val LONG_POS_UINT16_THRESHOLD = 55
        private const val LONG_POS_UINT8_THRESHOLD = 56

        // Inclusive maximum element/byte counts per format
        private const val FIX_COLLECTION_MAX_SIZE = 15
        private const val FIX_STR_MAX_BYTES = 31
        private const val ONE_BYTE_MAX_LENGTH = 255
        private const val TWO_BYTE_MAX_LENGTH = 65535

        private const val FALSE = 0xC2
        private const val TRUE = 0xC3

        private const val UINT8 = 0xCC
        private const val UINT16 = 0xCD
        private const val UINT32 = 0xCE
        private const val UINT64 = 0xCF

        private const val INT8 = 0xD0
        private const val INT16 = 0xD1
        private const val INT32 = 0xD2
        private const val INT64 = 0xD3

        private const val STR8 = 0xD9
        private const val STR16 = 0xDA
        private const val STR32 = 0xDB

        private const val BIN8 = 0xC4
        private const val BIN16 = 0xC5
        private const val BIN32 = 0xC6

        private const val ARRAY16 = 0xDC
        private const val ARRAY32 = 0xDD

        private const val MAP16 = 0xDE
        private const val MAP32 = 0xDF
        private const val FIXMAP = 0x80

        private const val NEGFIXNUM = 0xE0
        private const val FIXSTR = 0xA0
        private const val FIXARRAY = 0x90
    }
}
