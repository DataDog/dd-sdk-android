/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.profiling.internal.perfetto

import com.datadog.android.api.InternalLogger
import com.datadog.android.profiling.forge.Configurator
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.junit.jupiter.api.io.TempDir
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.isA
import org.mockito.kotlin.isNull
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.quality.Strictness
import java.io.File
import java.io.FileNotFoundException

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class PerfettoStackSampleDetectorTest {

    private lateinit var testedDetector: PerfettoStackSampleDetector

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @TempDir
    lateinit var tempDir: File

    @BeforeEach
    fun `set up`() {
        testedDetector = PerfettoStackSampleDetector()
    }

    @Test
    fun `M return false W hasNoStackSamples() {launch profile with stack samples}`() {
        // Given
        val traceFile = writeTrace(readFixture(LAUNCH_PROFILE_FIXTURE))

        // When
        val result = testedDetector.hasNoStackSamples(traceFile, mockInternalLogger)

        // Then
        assertThat(result).isFalse()
        verifyNoInteractions(mockInternalLogger)
    }

    @Test
    fun `M return true W hasNoStackSamples() {empty profile with samples without callstack}`() {
        // Given
        val traceFile = writeTrace(readFixture(EMPTY_PROFILE_FIXTURE))

        // When
        val result = testedDetector.hasNoStackSamples(traceFile, mockInternalLogger)

        // Then
        assertThat(result).isTrue()
        verifyNoInteractions(mockInternalLogger)
    }

    @Test
    fun `M return false W hasNoStackSamples() {stack sample after many packets without stack sample}`() {
        // Given
        val emptyProfile = readFixture(EMPTY_PROFILE_FIXTURE)
        val traceFile = writeTrace(emptyProfile + emptyProfile + emptyProfile + readFixture(LAUNCH_PROFILE_FIXTURE))

        // When
        val result = testedDetector.hasNoStackSamples(traceFile, mockInternalLogger)

        // Then
        assertThat(result).isFalse()
    }

    @Test
    fun `M return false W hasNoStackSamples() {negative packet tag}`() {
        // Given
        val traceFile = writeTrace(readFixture(EMPTY_PROFILE_FIXTURE) + NEGATIVE_VARINT)

        // When
        val result = testedDetector.hasNoStackSamples(traceFile, mockInternalLogger)

        // Then
        assertThat(result).isFalse()
    }

    @Test
    fun `M return false W hasNoStackSamples() {overflowing packet length}`() {
        // Given
        val traceFile = writeTrace(readFixture(EMPTY_PROFILE_FIXTURE) + OVERFLOWING_LENGTH_PACKET_HEADER)

        // When
        val result = testedDetector.hasNoStackSamples(traceFile, mockInternalLogger)

        // Then
        assertThat(result).isFalse()
    }

    @Test
    fun `M return false W hasNoStackSamples() {overflowing perf_sample length}`() {
        // Given
        val traceFile = writeTrace(readFixture(EMPTY_PROFILE_FIXTURE) + OVERFLOWING_PERF_SAMPLE_PACKET)

        // When
        val result = testedDetector.hasNoStackSamples(traceFile, mockInternalLogger)

        // Then
        assertThat(result).isFalse()
    }

    @Test
    fun `M return false W hasNoStackSamples() {invalid field number}`() {
        // Given
        val traceFile = writeTrace(readFixture(EMPTY_PROFILE_FIXTURE) + INVALID_FIELD_NUMBER_PACKET)

        // When
        val result = testedDetector.hasNoStackSamples(traceFile, mockInternalLogger)

        // Then
        assertThat(result).isFalse()
    }

    @Test
    fun `M return false W hasNoStackSamples() {packet larger than the maximum packet size}`() {
        // Given
        val traceFile = writeTrace(readFixture(EMPTY_PROFILE_FIXTURE) + OVERSIZED_PACKET_HEADER)

        // When
        val result = testedDetector.hasNoStackSamples(traceFile, mockInternalLogger)

        // Then
        assertThat(result).isFalse()
    }

    @Test
    fun `M return false W hasNoStackSamples() {truncated empty profile}`() {
        // Given
        val emptyProfile = readFixture(EMPTY_PROFILE_FIXTURE)
        val traceFile = writeTrace(emptyProfile.copyOf(emptyProfile.size - 1))

        // When
        val result = testedDetector.hasNoStackSamples(traceFile, mockInternalLogger)

        // Then
        assertThat(result).isFalse()
    }

    @Test
    fun `M return false W hasNoStackSamples() {empty profile with compressed packets}`() {
        // Given
        val traceFile = writeTrace(readFixture(EMPTY_PROFILE_FIXTURE) + COMPRESSED_PACKETS_PACKET)

        // When
        val result = testedDetector.hasNoStackSamples(traceFile, mockInternalLogger)

        // Then
        assertThat(result).isFalse()
    }

    @Test
    fun `M return false W hasNoStackSamples() {not a perfetto trace}`(
        @StringForgery fakeContent: String
    ) {
        // Given
        val traceFile = writeTrace(fakeContent.toByteArray())

        // When
        val result = testedDetector.hasNoStackSamples(traceFile, mockInternalLogger)

        // Then
        assertThat(result).isFalse()
    }

    @Test
    fun `M return false W hasNoStackSamples() {empty file}`() {
        // Given
        val traceFile = writeTrace(ByteArray(0))

        // When
        val result = testedDetector.hasNoStackSamples(traceFile, mockInternalLogger)

        // Then
        assertThat(result).isFalse()
    }

    @Test
    fun `M return false and log W hasNoStackSamples() {missing file}`(
        @StringForgery fakeFileName: String
    ) {
        // Given
        val traceFile = File(tempDir, fakeFileName)

        // When
        val result = testedDetector.hasNoStackSamples(traceFile, mockInternalLogger)

        // Then
        assertThat(result).isFalse()
        argumentCaptor<() -> String> {
            verify(mockInternalLogger).log(
                eq(InternalLogger.Level.WARN),
                eq(InternalLogger.Target.MAINTAINER),
                capture(),
                isA<FileNotFoundException>(),
                eq(false),
                isNull()
            )
            assertThat(firstValue()).isEqualTo("Unable to read the profiling trace to check its stack samples.")
        }
    }

    private fun readFixture(name: String): ByteArray {
        return checkNotNull(javaClass.classLoader?.getResourceAsStream(name)).use { it.readBytes() }
    }

    private fun writeTrace(content: ByteArray): File {
        return File(tempDir, TRACE_FILE_NAME).apply { writeBytes(content) }
    }

    companion object {
        // Stack sampling profile without any stack sample, recorded by an affected profiling system package.
        private const val EMPTY_PROFILE_FIXTURE = "perfetto/empty_stack_sampling.perfetto-trace"

        // Application launch profile of the sample app.
        private const val LAUNCH_PROFILE_FIXTURE = "perfetto/launch_stack_sampling.perfetto-trace"

        private const val TRACE_FILE_NAME = "trace.perfetto-trace"

        // `TracePacket { compressed_packets (field 50) = <empty> }`, framed as `Trace.packet` (field 1).
        private val COMPRESSED_PACKETS_PACKET = byteArrayOf(0x0A, 0x03, 0x92.toByte(), 0x03, 0x00)

        // Varint decoding to -1, which can only come from corrupted data.
        private val NEGATIVE_VARINT = ByteArray(9) { 0xFF.toByte() } + byteArrayOf(0x01)

        // Ten-byte varint whose last byte overflows 64 bits, which would wrap around to 0.
        private val OVERFLOWING_VARINT = ByteArray(9) { 0x80.toByte() } + byteArrayOf(0x02)

        // `Trace.packet` (field 1) header with an overflowing length.
        private val OVERFLOWING_LENGTH_PACKET_HEADER = byteArrayOf(0x0A) + OVERFLOWING_VARINT

        // `Trace.packet` (field 1) holding a `perf_sample` (field 66) with an overflowing length.
        private val OVERFLOWING_PERF_SAMPLE_PACKET = byteArrayOf(0x0A, 0x0C, 0x92.toByte(), 0x04) + OVERFLOWING_VARINT

        // `Trace.packet` (field 1) holding a varint field with the invalid field number 0.
        private val INVALID_FIELD_NUMBER_PACKET = byteArrayOf(0x0A, 0x02, 0x00, 0x00)

        // `Trace.packet` (field 1) header declaring a 2 MiB packet.
        private val OVERSIZED_PACKET_HEADER = byteArrayOf(0x0A, 0x80.toByte(), 0x80.toByte(), 0x80.toByte(), 0x01)
    }
}
