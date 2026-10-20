/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.profiling.internal.perfetto

import com.datadog.android.api.InternalLogger
import java.io.BufferedInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream

/**
 * [StackSampleDetector] reading the Perfetto trace format.
 *
 * Some versions of the `com.google.android.profiling` system package record stack sampling traces without any
 * callstack sample. Such traces can be megabytes large (symbol tables, sample packets without a callstack), but
 * the backend builds CPU samples only from `perf_sample` packets carrying a callstack.
 *
 * The trace is streamed one packet at a time: each packet is read into a buffer, and its fields are walked by tag
 * and length, only `perf_sample` fields being looked into. The walk stops at the first sample with a callstack,
 * which comes early in a populated trace.
 */
internal class PerfettoStackSampleDetector : StackSampleDetector {

    override fun hasNoStackSamples(traceFile: File, internalLogger: InternalLogger): Boolean {
        return try {
            BufferedInputStream(traceFile.inputStream()).use { hasNoStackSamples(TraceStreamReader(it)) }
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            // we are working with a pure byte stream without explicit .proto definition, so it is better
            // to be over-protective
            logUnreadableTrace(internalLogger, e)
            false
        }
    }

    private fun hasNoStackSamples(traceReader: TraceStreamReader): Boolean {
        var packet = ByteArray(INITIAL_PACKET_BUFFER_SIZE)
        var content = PacketContent.NO_STACK_SAMPLE
        var packetCount = 0
        var isEndReached = false
        while (!isEndReached && content == PacketContent.NO_STACK_SAMPLE) {
            val tag = traceReader.readVarint()
            val packetLength = if (tag == TRACE_PACKET_TAG) traceReader.readVarint() else MALFORMED_VARINT
            when {
                tag == END_OF_STREAM && packetCount > 0 -> isEndReached = true
                // `Trace` only holds `repeated TracePacket packet = 1`, anything else is not a Perfetto trace.
                packetLength !in 0..MAX_PACKET_SIZE -> content = PacketContent.UNREADABLE
                else -> {
                    val length = packetLength.toInt()
                    if (length > packet.size) packet = ByteArray(length)
                    content = if (traceReader.readFully(packet, length)) {
                        packetCount++
                        inspectPacket(packet, 0, length)
                    } else {
                        PacketContent.UNREADABLE
                    }
                }
            }
        }
        return content == PacketContent.NO_STACK_SAMPLE
    }

    private fun logUnreadableTrace(internalLogger: InternalLogger, throwable: Throwable) {
        internalLogger.log(
            level = InternalLogger.Level.WARN,
            target = InternalLogger.Target.MAINTAINER,
            messageBuilder = { LOG_UNREADABLE_TRACE },
            throwable = throwable
        )
    }

    private fun inspectPacket(trace: ByteArray, start: Int, end: Int): PacketContent {
        val reader = ProtoReader(trace, start, end)
        var content = PacketContent.NO_STACK_SAMPLE
        while (reader.hasRemaining && content == PacketContent.NO_STACK_SAMPLE) {
            val tag = reader.readTag()
            val field = tag ushr TAG_FIELD_SHIFT
            val wireType = (tag and TAG_WIRE_TYPE_MASK).toInt()
            when {
                reader.isMalformed -> Unit
                wireType != WIRE_TYPE_LENGTH_DELIMITED -> reader.skipField(wireType)
                field == FIELD_PERF_SAMPLE -> content = inspectPerfSample(trace, reader)
                // Samples inside a compressed block are not visible to this walk.
                field == FIELD_COMPRESSED_PACKETS || field == FIELD_ZSTD_COMPRESSED_PACKETS ->
                    content = PacketContent.UNREADABLE
                else -> reader.skipField(wireType)
            }
        }
        return if (reader.isMalformed) PacketContent.UNREADABLE else content
    }

    private fun inspectPerfSample(trace: ByteArray, packetReader: ProtoReader): PacketContent {
        val sampleLength = packetReader.readLength()
        val sampleStart = packetReader.position
        packetReader.skipBytes(sampleLength)
        return if (!packetReader.isMalformed && hasCallstack(trace, sampleStart, sampleStart + sampleLength)) {
            PacketContent.STACK_SAMPLE
        } else {
            PacketContent.NO_STACK_SAMPLE
        }
    }

    private fun hasCallstack(trace: ByteArray, start: Int, end: Int): Boolean {
        val reader = ProtoReader(trace, start, end)
        var hasCallstack = false
        while (reader.hasRemaining && !hasCallstack) {
            val tag = reader.readTag()
            val wireType = (tag and TAG_WIRE_TYPE_MASK).toInt()
            hasCallstack = !reader.isMalformed &&
                tag ushr TAG_FIELD_SHIFT == FIELD_CALLSTACK_IID &&
                wireType == WIRE_TYPE_VARINT
            if (!hasCallstack && !reader.isMalformed) reader.skipField(wireType)
        }
        // A sample which can't be read is considered as having a callstack, to never drop real data.
        return hasCallstack || reader.isMalformed
    }

    private enum class PacketContent {
        STACK_SAMPLE,
        NO_STACK_SAMPLE,
        UNREADABLE
    }

    /**
     * Reads the protobuf framing of [stream]: varints and packet bodies.
     */
    private class TraceStreamReader(private val stream: InputStream) {

        /**
         * @return the varint read, [END_OF_STREAM] if the stream ends before it, or [MALFORMED_VARINT] if the
         * stream ends inside it, it is too long, it overflows 64 bits or it is negative.
         */
        @Throws(IOException::class)
        fun readVarint(): Long {
            var result = 0L
            var shift = 0
            var byte = readByte()
            val isEndOfStream = byte < 0
            var isComplete = false
            var isOverflowing = false
            while (byte >= 0 && shift < MAX_VARINT_BITS && !isComplete) {
                val payload = byte and VARINT_PAYLOAD_MASK
                isOverflowing = shift == LAST_VARINT_BYTE_SHIFT && payload > 1
                result = result or (payload.toLong() shl shift)
                isComplete = byte and VARINT_CONTINUATION_BIT == 0
                shift += VARINT_SHIFT
                if (!isComplete) byte = readByte()
            }
            return when {
                isEndOfStream -> END_OF_STREAM
                // Tags and lengths are never negative: a negative value can only come from corrupted data.
                isComplete && !isOverflowing && result >= 0 -> result
                else -> MALFORMED_VARINT
            }
        }

        /**
         * @return true if [length] bytes were read into [buffer], false if the stream ended before.
         */
        @Throws(IOException::class)
        fun readFully(buffer: ByteArray, length: Int): Boolean {
            var size = 0
            var count = 0
            while (size < length && count >= 0) {
                count = readBytes(buffer, size, length - size)
                if (count > 0) size += count
            }
            return size == length
        }

        @Throws(IOException::class)
        @Suppress("UnsafeThirdPartyFunctionCall") // IOException is caught in hasNoStackSamples(File)
        private fun readByte(): Int = stream.read()

        @Throws(IOException::class)
        @Suppress("UnsafeThirdPartyFunctionCall") // IOException is caught in hasNoStackSamples(File)
        private fun readBytes(buffer: ByteArray, offset: Int, length: Int): Int = stream.read(buffer, offset, length)
    }

    /**
     * Reads protobuf wire format from [bytes] between [position] and [end]. Instead of throwing, any read
     * out of bounds sets [isMalformed], after which [hasRemaining] is false.
     */
    private class ProtoReader(
        private val bytes: ByteArray,
        var position: Int,
        private val end: Int
    ) {
        var isMalformed: Boolean = false
            private set

        val hasRemaining: Boolean
            get() = !isMalformed && position < end

        fun readVarint(): Long {
            var result = 0L
            var shift = 0
            while (position < end && shift < MAX_VARINT_BITS) {
                val byte = bytes[position++].toInt()
                val payload = byte and VARINT_PAYLOAD_MASK
                if (shift == LAST_VARINT_BYTE_SHIFT && payload > 1) break
                result = result or (payload.toLong() shl shift)
                if (byte and VARINT_CONTINUATION_BIT == 0) return result
                shift += VARINT_SHIFT
            }
            isMalformed = true
            return 0L
        }

        fun readTag(): Long {
            val tag = readVarint()
            if (tag ushr TAG_FIELD_SHIFT !in MIN_FIELD_NUMBER..MAX_FIELD_NUMBER) isMalformed = true
            return tag
        }

        fun readLength(): Int {
            val length = readVarint()
            if (isMalformed || length < 0 || length > end - position) {
                isMalformed = true
                return 0
            }
            return length.toInt()
        }

        fun skipBytes(count: Int) {
            if (count > end - position) {
                isMalformed = true
            } else {
                position += count
            }
        }

        fun skipField(wireType: Int) {
            when (wireType) {
                WIRE_TYPE_VARINT -> readVarint()
                WIRE_TYPE_FIXED64 -> skipBytes(FIXED64_SIZE)
                WIRE_TYPE_LENGTH_DELIMITED -> skipBytes(readLength())
                WIRE_TYPE_FIXED32 -> skipBytes(FIXED32_SIZE)
                // Groups (3, 4) are deprecated and not used by Perfetto, 6 and 7 are not valid.
                else -> isMalformed = true
            }
        }
    }

    private companion object {
        private const val INITIAL_PACKET_BUFFER_SIZE = 8 * 1024

        // Packets observed in traces collected in the field are below 512 KiB. A larger packet length is treated as
        // malformed, to never allocate a buffer from a corrupted length.
        private const val MAX_PACKET_SIZE = 1024L * 1024L

        private const val END_OF_STREAM = -1L
        private const val MALFORMED_VARINT = -2L

        private const val LOG_UNREADABLE_TRACE = "Unable to read the profiling trace to check its stack samples."

        // Field numbers from perfetto/trace/trace_packet.proto and perfetto/trace/profiling/profile_packet.proto.
        private const val TRACE_PACKET_TAG = 0x0AL
        private const val FIELD_COMPRESSED_PACKETS = 50L
        private const val FIELD_PERF_SAMPLE = 66L
        private const val FIELD_ZSTD_COMPRESSED_PACKETS = 133L
        private const val FIELD_CALLSTACK_IID = 4L

        private const val WIRE_TYPE_VARINT = 0
        private const val WIRE_TYPE_FIXED64 = 1
        private const val WIRE_TYPE_LENGTH_DELIMITED = 2
        private const val WIRE_TYPE_FIXED32 = 5

        private const val TAG_FIELD_SHIFT = 3
        private const val MIN_FIELD_NUMBER = 1L
        private const val MAX_FIELD_NUMBER = (1L shl 29) - 1
        private const val TAG_WIRE_TYPE_MASK = 0x07L
        private const val FIXED64_SIZE = 8
        private const val FIXED32_SIZE = 4

        private const val VARINT_PAYLOAD_MASK = 0x7F
        private const val VARINT_CONTINUATION_BIT = 0x80
        private const val VARINT_SHIFT = 7
        private const val MAX_VARINT_BITS = 64

        // A varint holds at most 64 bits: its tenth byte, shifted by 63 bits, can only carry the lowest bit.
        private const val LAST_VARINT_BYTE_SHIFT = 63
    }
}
