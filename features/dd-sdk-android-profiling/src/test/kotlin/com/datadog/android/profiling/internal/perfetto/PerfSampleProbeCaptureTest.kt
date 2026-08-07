/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.profiling.internal.perfetto

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.zip.ZipInputStream

/**
 * Runs the probe over two complete, unmodified captures from a real device, checked in as the zip
 * archives the profiler produced.
 *
 * The empty one is the case this exists for: 45882 packets over 57s at full cadence, no perf_sample.
 * The populated one is a healthy 51319-packet capture with 51304 samples. Both counts come from
 * Perfetto's own `trace_processor -q "select count(*) from perf_sample"`, so these are ground truth
 * rather than a recording of current behaviour.
 */
internal class PerfSampleProbeCaptureTest {

    @Test
    fun `M report NO_SAMPLES W probe() {real empty capture}`() {
        assertThat(probe(EMPTY_CAPTURE)).isEqualTo(PerfSampleVerdict.NO_SAMPLES)
    }

    @Test
    fun `M report HAS_SAMPLES W probe() {real populated capture}`() {
        assertThat(probe(POPULATED_CAPTURE)).isEqualTo(PerfSampleVerdict.HAS_SAMPLES)
    }

    private fun probe(name: String) = PerfSampleProbe.probe(captureBytes(name))

    private fun captureBytes(name: String): ByteArray = openCapture(name).use { it.readBytes() }

    /** Opens the archive positioned at the start of the `perfetto.proto` entry. */
    private fun openCapture(name: String): ZipInputStream {
        val classLoader = checkNotNull(javaClass.classLoader)
        val resource = checkNotNull(classLoader.getResourceAsStream("$CAPTURE_DIR/$name")) {
            "missing capture archive $CAPTURE_DIR/$name"
        }
        val zip = ZipInputStream(resource)
        var entry = zip.nextEntry
        // Each archive wraps the trace under a per-session directory, so match on the leaf name.
        while (entry != null && !entry.name.endsWith("perfetto.proto")) {
            entry = zip.nextEntry
        }
        checkNotNull(entry) { "no perfetto.proto inside $name" }
        return zip
    }

    private companion object {
        // Not "captures/", which the root .gitignore excludes for Android Studio.
        const val CAPTURE_DIR = "perfetto-captures"
        const val EMPTY_CAPTURE = "empty-profile-capture.zip"
        const val POPULATED_CAPTURE = "populated-profile-capture.zip"
    }
}
