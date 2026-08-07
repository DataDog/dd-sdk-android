/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.profiling.internal.perfetto

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.Random

internal class PerfSampleProbeTest {

    @Test
    fun `M report NO_SAMPLES W probe() {packets without a perf_sample}`() {
        assertThat(probe(HUSK + HUSK + HUSK)).isEqualTo(PerfSampleVerdict.NO_SAMPLES)
    }

    @Test
    fun `M report HAS_SAMPLES W probe() {packet with a perf_sample}`() {
        assertThat(probe(HUSK + SAMPLE + HUSK)).isEqualTo(PerfSampleVerdict.HAS_SAMPLES)
    }

    @Test
    fun `M report NO_SAMPLES W probe() {non-minimal length varint}`() {
        // Given — protozero reserves a fixed-width size field, so real captures pad length varints.
        // 732 of the 45882 packets in the empty reference capture encode a 4-byte length this way.
        val padded = bytes(0x0A, 0x84, 0x80, 0x80, 0x00, 0x08, 0x01, 0x18, 0x02)

        // When + Then
        assertThat(probe(padded)).isEqualTo(PerfSampleVerdict.NO_SAMPLES)
    }

    @Test
    fun `M report HAS_SAMPLES W probe() {non-minimal perf_sample length varint}`() {
        // Given — 227 of the 51304 samples in the healthy reference capture pad the field-66 length to
        // 4 bytes, so rejecting redundant continuation bytes here would misread real traces as empty
        val trace = bytes(0x0A, 0x0A, 0x08, 0x01, 0x92, 0x04, 0x82, 0x80, 0x80, 0x00, 0xAA, 0xBB)

        // When + Then
        assertThat(probe(trace)).isEqualTo(PerfSampleVerdict.HAS_SAMPLES)
    }

    @Test
    fun `M report HAS_SAMPLES W probe() {perf_sample with a zero-length payload}`() {
        // Given — the tag's presence is the signal; its payload is never read
        val trace = bytes(0x0A, 0x05, 0x08, 0x01, 0x92, 0x04, 0x00)

        // When + Then
        assertThat(probe(trace)).isEqualTo(PerfSampleVerdict.HAS_SAMPLES)
    }

    @Test
    fun `M report HAS_SAMPLES W probe() {sample is the last field of the last packet}`() {
        // Given — the sample's length varint ends exactly on the packet boundary, which is the edge the
        // boundary check in walkPacket sits on
        val trace = HUSK + bytes(0x0A, 0x05, 0x08, 0x01, 0x92, 0x04, 0x00)

        // When + Then
        assertThat(probe(trace)).isEqualTo(PerfSampleVerdict.HAS_SAMPLES)
    }

    @Test
    fun `M report TRUNCATED W probe() {empty input}`() {
        // Given — no bytes at all is not the same as bytes that were walked and held no sample
        assertThat(probe(ByteArray(0))).isEqualTo(PerfSampleVerdict.TRUNCATED)
    }

    @Test
    fun `M report COMPRESSED W probe() {deflate compressed packets}`() {
        // Given — field 50, wiretype 2. Samples would be inside the block, so no verdict is possible.
        val trace = bytes(0x0A, 0x05, 0x92, 0x03, 0x02, 0xAA, 0xBB)

        // When + Then
        assertThat(probe(trace)).isEqualTo(PerfSampleVerdict.COMPRESSED)
    }

    @Test
    fun `M report COMPRESSED W probe() {zstd compressed packets}`() {
        // Given — field 133, wiretype 2. Without this branch the block is skipped as an unknown field
        // and the trace is reported empty.
        val trace = bytes(0x0A, 0x05, 0xAA, 0x08, 0x02, 0xAA, 0xBB)

        // When + Then
        assertThat(probe(trace)).isEqualTo(PerfSampleVerdict.COMPRESSED)
    }

    @Test
    fun `M report MALFORMED W probe() {field number aliasing perf_sample above 32 bits}`() {
        // Given — field 66 + 2^32, wiretype 2. Narrowed to an Int without a range check this truncates
        // to exactly 66 and reports a sample that does not exist.
        val trace = bytes(0x0A, 0x07, 0x92, 0x84, 0x80, 0x80, 0x80, 0x01, 0x00)

        // When + Then
        assertThat(probe(trace)).isEqualTo(PerfSampleVerdict.MALFORMED)
    }

    @Test
    fun `M report MALFORMED W probe() {perf_sample tag crossing the packet boundary}`() {
        // Given — a 1-byte body holding only 0x92; the 0x04 completing the field-66 tag and the length
        // varint after it lie outside the packet. Corrupt framing, not a sample.
        val trace = bytes(0x0A, 0x01, 0x92, 0x04, 0x00)

        // When + Then
        assertThat(probe(trace)).isEqualTo(PerfSampleVerdict.MALFORMED)
    }

    @Test
    fun `M report MALFORMED W probe() {non-packet top level tag}`() {
        // Given — 0x12 is field 2, wiretype 2, not Trace.packet
        assertThat(probe(bytes(0x12, 0x02, 0x00, 0x00))).isEqualTo(PerfSampleVerdict.MALFORMED)
    }

    @Test
    fun `M report TRUNCATED W probe() {stream ends mid packet}`() {
        // Given — the packet declares a 4-byte body and only 3 bytes follow
        assertThat(probe(HUSK.copyOf(HUSK.size - 1))).isEqualTo(PerfSampleVerdict.TRUNCATED)
    }

    @Test
    fun `M return a verdict W probe() {corrupt bytes}`() {
        // Given — mutating a valid trace drives corruption deeper than random bytes, which die on the
        // first tag check. Any input must produce a verdict rather than an exception.
        val random = Random(SEED)
        val pristine = HUSK + SAMPLE + HUSK + HUSK

        // When + Then
        repeat(FUZZ_ITERATIONS) {
            val mutated = pristine.copyOf()
            repeat(1 + random.nextInt(4)) {
                mutated[random.nextInt(mutated.size)] = random.nextInt().toByte()
            }
            assertThat(probe(mutated)).isIn(*PerfSampleVerdict.values())
        }
    }

    private fun probe(trace: ByteArray) = PerfSampleProbe.probe(trace)

    private companion object {
        const val SEED = 20260806L
        const val FUZZ_ITERATIONS = 2000

        fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

        /** Packet tag, length 4, then two wiretype-0 fields. No submessage, as in the empty capture. */
        val HUSK = bytes(0x0A, 0x04, 0x08, 0x01, 0x18, 0x02)

        /** As above plus field 66, wiretype 2, with a two-byte payload. */
        val SAMPLE = bytes(0x0A, 0x07, 0x08, 0x01, 0x92, 0x04, 0x02, 0xAA, 0xBB)
    }
}
