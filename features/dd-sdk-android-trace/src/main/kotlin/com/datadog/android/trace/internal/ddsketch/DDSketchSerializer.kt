/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.trace.internal.ddsketch

import java.io.ByteArrayOutputStream

/**
 * This class is used to perform protobuf serialization compliant with the official schema used to
 * generate protobuf bindings (DDSketch.proto) but does not require the weight of the protobuf-java
 * dependency nor the number of loaded classes required to use protobuf. As such, it can support low
 * overhead use cases such as tracers.
 */
internal class DDSketchSerializer(size: Int) {
    private val stream = ByteArrayOutputStream(size)

    fun toByteArray(): ByteArray = stream.toByteArray()

    fun writeHeader(fieldNumber: Int, length: Int) {
        writeTag(fieldNumber, LENGTH_DELIMITED)
        writeVarInt(length)
    }

    fun writeCompactArray(fieldIndex: Int, array: DoubleArray, from: Int, length: Int) {
        writeTag(fieldIndex, LENGTH_DELIMITED)
        writeVarInt(length * Double.SIZE_BYTES)
        for (i in from until from + length) {
            writeLE64(array[i])
        }
    }

    fun writeDouble(fieldIndex: Int, value: Double) {
        if (value != 0.0) {
            writeTag(fieldIndex, FIXED_64)
            writeLE64(value)
        }
    }

    fun writeSignedInt32(fieldIndex: Int, value: Int) {
        writeTag(fieldIndex, VARINT)
        writeVarInt(zigZag(value))
    }

    internal fun writeTag(fieldIndex: Int, wireType: Int) {
        writeVarInt((fieldIndex shl TAG_FIELD_SHIFT) or wireType)
    }

    private fun writeLE64(value: Double) {
        val bits = value.toRawBits()
        for (i in 0 until Double.SIZE_BYTES) {
            stream.write((bits ushr (i * Byte.SIZE_BITS)).toInt())
        }
    }

    private fun writeVarInt(value: Int) {
        var v = value
        val length = varIntLength(v)
        repeat(length) {
            stream.write((v and VARINT_DATA_MASK) or VARINT_CONTINUATION_BIT)
            v = v ushr VARINT_DATA_BITS
        }
        stream.write(v)
    }

    companion object {
        // any integer type including booleans
        private const val VARINT = 0

        // doubles
        private const val FIXED_64 = 1

        // strings, binary, arrays (i.e. repeated fields), embedded structs (i.e. messages)
        private const val LENGTH_DELIMITED = 2

        private const val TAG_FIELD_SHIFT = 3 // protobuf: tag = (fieldNumber << 3) | wireType
        private const val VARINT_DATA_BITS = 7 // data bits per varint byte
        private const val VARINT_DATA_MASK = 0x7F // mask for the 7 data bits
        private const val VARINT_CONTINUATION_BIT = 0x80 // set on all but the last varint byte
        private const val ZIGZAG_ENCODE_SHIFT = 1 // zigzag: left shift maps signed → unsigned
        private const val INT_MSB_SHIFT = 31 // arithmetic right shift to replicate sign bit

        private val VAR_INT_LENGTHS_32 = IntArray(33) { i -> (31 - i) / VARINT_DATA_BITS }

        fun embeddedSize(size: Int): Int {
            return varIntLength(size) + 1 + size
        }

        fun signedIntFieldSize(fieldIndex: Int, value: Int): Int {
            return tagSize(fieldIndex, VARINT) + varIntLength(zigZag(value)) + 1
        }

        fun doubleFieldSize(fieldIndex: Int, value: Double): Int {
            return if (value == 0.0) 0 else tagSize(fieldIndex, FIXED_64) + Double.SIZE_BYTES
        }

        fun embeddedFieldSize(fieldIndex: Int, size: Int): Int {
            return tagSize(fieldIndex, LENGTH_DELIMITED) + embeddedSize(size)
        }

        fun sizeOfCompactDoubleArray(fieldIndex: Int, size: Int): Int {
            return tagSize(fieldIndex, LENGTH_DELIMITED) + embeddedSize(size * Double.SIZE_BYTES)
        }

        private fun zigZag(signed: Int): Int {
            return (signed shl ZIGZAG_ENCODE_SHIFT) xor (signed shr INT_MSB_SHIFT)
        }

        private fun tagSize(tag: Int, type: Int): Int {
            return varIntLength((tag shl TAG_FIELD_SHIFT) or type) + 1
        }

        private fun varIntLength(value: Int): Int {
            return VAR_INT_LENGTHS_32[value.countLeadingZeroBits()]
        }
    }
}
