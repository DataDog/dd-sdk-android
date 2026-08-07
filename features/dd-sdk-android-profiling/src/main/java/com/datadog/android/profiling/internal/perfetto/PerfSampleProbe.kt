/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.profiling.internal.perfetto

/**
 * Outcome of probing a capture. Only [NO_SAMPLES] means the profile is empty; the others mean the trace
 * could not be read, and must not be counted as empty.
 */
internal enum class PerfSampleVerdict(val value: String) {
    HAS_SAMPLES("has_samples"),
    NO_SAMPLES("no_samples"),
    COMPRESSED("compressed"),
    TRUNCATED("truncated"),
    MALFORMED("malformed")
}

/**
 * Reports whether a captured Perfetto trace holds any `TracePacket.perf_sample`.
 *
 * A profile can be empty while its file is megabytes large: the capture that prompted this is 1.19 MiB
 * of payload-free packets emitted at full cadence for 57s with no sample in it, so neither file size nor
 * packet count separates it from a healthy one.
 *
 * Only field tags and lengths are read; payloads are skipped over and the walk stops at the first
 * sample, around 800 bytes into a populated capture.
 *
 * Nothing acts on the verdict: it is reported on the profiling write metric so the rate can be compared
 * against the empty-profile count the backend derives independently. Temporary, and should be deleted
 * once the platform stops producing these.
 */
// ThrowingInternalException: ProbeAbort is control flow private to this file, caught in probe() below.
@Suppress("ThrowingInternalException")
internal object PerfSampleProbe {

    /** @param trace the raw bytes of a Perfetto trace. */
    fun probe(trace: ByteArray): PerfSampleVerdict {
        // Nothing to walk is not the same as walking it all and finding no sample.
        if (trace.isEmpty()) return PerfSampleVerdict.TRUNCATED
        return try {
            walk(TraceCursor(trace))
        } catch (e: ProbeAbort) {
            e.verdict
        } catch (@Suppress("TooGenericExceptionCaught") _: Throwable) {
            // Nothing may escape into the upload path. Reported as MALFORMED so that a defect here can
            // never be mistaken for an empty profile and drop a capture that had samples in it.
            PerfSampleVerdict.MALFORMED
        }
    }

    /** Walks the top-level `repeated TracePacket packet = 1` framing. */
    private fun walk(cursor: TraceCursor): PerfSampleVerdict {
        while (cursor.hasMore) {
            if (cursor.readVarint() != PACKET_TAG) throw ProbeAbort(PerfSampleVerdict.MALFORMED)
            val length = cursor.readVarint()
            if (length < 0 || length > MAX_PACKET_SIZE) throw ProbeAbort(PerfSampleVerdict.MALFORMED)
            if (walkPacket(cursor, cursor.position + length)) return PerfSampleVerdict.HAS_SAMPLES
        }
        return PerfSampleVerdict.NO_SAMPLES
    }

    /** Returns true once a `perf_sample` field is seen inside the packet ending at [packetEnd]. */
    private fun walkPacket(cursor: TraceCursor, packetEnd: Long): Boolean {
        while (cursor.position < packetEnd) {
            val tag = cursor.readVarint()
            // Range-checked as a Long before narrowing, or a tag encoding field 66 + 2^32 would alias
            // onto 66. `ushr` keeps a tag with bit 63 set positive, so it lands out of range.
            val field = tag ushr TAG_FIELD_SHIFT
            if (field <= 0L || field > MAX_FIELD_NUMBER) throw ProbeAbort(PerfSampleVerdict.MALFORMED)
            val found = skipField(cursor, (tag and WIRETYPE_MASK).toInt(), field.toInt(), packetEnd)
            // Checked before the positive is honoured, so a perf_sample tag whose length varint lies
            // outside the packet is MALFORMED rather than a false HAS_SAMPLES.
            if (cursor.position > packetEnd) throw ProbeAbort(PerfSampleVerdict.MALFORMED)
            if (found) return true
        }
        return false
    }

    private fun skipField(cursor: TraceCursor, wireType: Int, field: Int, packetEnd: Long): Boolean {
        when (wireType) {
            WIRETYPE_VARINT -> cursor.readVarint()
            WIRETYPE_FIXED64 -> cursor.skip(FIXED64_BYTES)
            WIRETYPE_FIXED32 -> cursor.skip(FIXED32_BYTES)
            WIRETYPE_LENGTH_DELIMITED -> return skipLengthDelimited(cursor, field, packetEnd)
            // Wiretypes 3 and 4 are the deprecated groups, 6 and 7 unassigned. None are valid here.
            else -> throw ProbeAbort(PerfSampleVerdict.MALFORMED)
        }
        return false
    }

    private fun skipLengthDelimited(cursor: TraceCursor, field: Int, packetEnd: Long): Boolean {
        val length = cursor.readVarint()
        if (field == FIELD_PERF_SAMPLE) return true
        // Samples inside a compressed block are invisible to this walk, so say so rather than guess.
        if (field == FIELD_COMPRESSED || field == FIELD_ZSTD_COMPRESSED) {
            throw ProbeAbort(PerfSampleVerdict.COMPRESSED)
        }
        // Subtraction rather than `position + length`, which a corrupt length would overflow.
        if (length < 0 || length > packetEnd - cursor.position) {
            throw ProbeAbort(PerfSampleVerdict.MALFORMED)
        }
        cursor.skip(length)
        return false
    }
}

// Field numbers from perfetto/trace/trace_packet.proto. Protobuf field numbers are the wire format and
// are never reused, so these hold across Perfetto versions; checked against v50.1 and v54.0.
private const val PACKET_TAG = 0x0AL
private const val FIELD_COMPRESSED = 50
private const val FIELD_PERF_SAMPLE = 66
private const val FIELD_ZSTD_COMPRESSED = 133

private const val WIRETYPE_VARINT = 0
private const val WIRETYPE_FIXED64 = 1
private const val WIRETYPE_LENGTH_DELIMITED = 2
private const val WIRETYPE_FIXED32 = 5

private const val TAG_FIELD_SHIFT = 3
private const val WIRETYPE_MASK = 0x07L
private const val FIXED64_BYTES = 8L
private const val FIXED32_BYTES = 4L

/** Largest legal protobuf field number, 2^29 - 1. */
private const val MAX_FIELD_NUMBER = 536_870_911L

/**
 * Sanity bound on one packet. Real packets top out under 4 KiB and the capture buffer is requested at
 * 5 MiB, so 1 MiB is already 250x the largest packet observed. Keeping it tight matters: a packet whose
 * declared length happens to reach exactly EOF would otherwise swallow the rest of the trace as one
 * opaque body, hiding every sample inside it and reporting the whole capture as empty.
 */
private const val MAX_PACKET_SIZE = 1L * 1024 * 1024

private const val VARINT_PAYLOAD_MASK = 0x7F
private const val VARINT_CONTINUATION_BIT = 0x80
private const val VARINT_SHIFT = 7
private const val MAX_VARINT_BITS = 64
private const val BYTE_MASK = 0xFF

private class ProbeAbort(val verdict: PerfSampleVerdict) : Exception() {
    // The stack trace carries no information here. The four-argument Exception constructor does the
    // same but only exists from API 24, and this module ships down to API 23.
    override fun fillInStackTrace(): Throwable = this
}

@Suppress("ThrowingInternalException")
private class TraceCursor(private val bytes: ByteArray) {

    var position = 0L
        private set

    val hasMore: Boolean get() = position < bytes.size

    fun readVarint(): Long {
        var result = 0L
        var shift = 0
        while (shift < MAX_VARINT_BITS) {
            if (position >= bytes.size) throw ProbeAbort(PerfSampleVerdict.TRUNCATED)
            val byte = bytes[position.toInt()].toInt() and BYTE_MASK
            position++
            result = result or ((byte and VARINT_PAYLOAD_MASK).toLong() shl shift)
            if (byte and VARINT_CONTINUATION_BIT == 0) return result
            shift += VARINT_SHIFT
        }
        throw ProbeAbort(PerfSampleVerdict.MALFORMED)
    }

    fun skip(count: Long) {
        if (count < 0) throw ProbeAbort(PerfSampleVerdict.MALFORMED)
        if (count > bytes.size - position) throw ProbeAbort(PerfSampleVerdict.TRUNCATED)
        position += count
    }
}
