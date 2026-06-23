/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.vitals

import com.datadog.android.rum.utils.forge.Configurator
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.junit.jupiter.api.io.TempDir
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.quality.Strictness
import java.io.File

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class CpuStatReaderTest {

    lateinit var testedReader: CpuStatReader

    @TempDir
    lateinit var tempDir: File

    lateinit var fakeFile: File

    // /proc/self/stat fields (indices 0–12 before utime at index 13)
    @IntForgery(1)
    var fakePid: Int = 0

    @StringForgery(regex = "\\(\\w+\\)")
    lateinit var fakeCommand: String

    @StringForgery(regex = "[RSDZTtWXxKWP]")
    lateinit var fakeState: String

    @IntForgery(1)
    var fakePpid: Int = 0

    @IntForgery(1)
    var fakePgrp: Int = 0

    @IntForgery(1)
    var fakeSession: Int = 0

    @IntForgery(1)
    var fakeTtyNr: Int = 0

    @IntForgery(1)
    var fakeTpgid: Int = 0

    @IntForgery(1)
    var fakeFlags: Int = 0

    @IntForgery(1)
    var fakeMinFlt: Int = 0

    @IntForgery(1)
    var fakeCMinFlt: Int = 0

    @IntForgery(1)
    var fakeMajFlt: Int = 0

    @IntForgery(1)
    var fakeCMajFlt: Int = 0

    @IntForgery(min = 1, max = 10_000)
    var fakeUtime: Int = 0

    @IntForgery(min = 1, max = 10_000)
    var fakeStime: Int = 0

    @BeforeEach
    fun `set up`() {
        fakeFile = File(tempDir, "stat")
        testedReader = CpuStatReader(fakeFile, internalLogger = mock())
    }

    @Test
    fun `M use default stat file W init()`() {
        // When
        val reader = CpuStatReader(internalLogger = mock())

        // Then
        assertThat(reader.statFile).isEqualTo(CpuStatReader.STAT_FILE)
    }

    @Test
    fun `M read utime W readUserTime()`() {
        // Given
        fakeFile.writeText(generateStatContent(fakeUtime))

        // When
        val result = testedReader.readUserTime()

        // Then
        assertThat(result).isEqualTo(fakeUtime.toDouble())
    }

    @Test
    fun `M read utime W readUserTime() {multiple times}`(@IntForgery(1) utimes: List<Int>) {
        // Given
        val results = mutableListOf<Double?>()

        // When
        utimes.forEach { utime ->
            fakeFile.writeText(generateStatContent(utime))
            results.add(testedReader.readUserTime())
        }

        // Then
        assertThat(results).isEqualTo(utimes.map { it.toDouble() })
    }

    @Test
    fun `M ignore stime W readUserTime() {non-numeric stime}`(
        @StringForgery(regex = "[a-z]{3}") fakeNonNumeric: String
    ) {
        // Given — utime is well-formed, only stime is garbage; utime must still be returned
        fakeFile.writeText(generateStatContent(utime = fakeUtime, stime = fakeNonNumeric))

        // When
        val result = testedReader.readUserTime()

        // Then
        assertThat(result).isEqualTo(fakeUtime.toDouble())
    }

    @Test
    fun `M return null W readUserTime() {file doesn't exist}`() {
        // When
        val result = testedReader.readUserTime()

        // Then
        assertThat(result).isNull()
    }

    @Test
    fun `M return null W readUserTime() {file isn't readable}`() {
        // Given
        val restrictedFile = mock<File> {
            on { exists() } doReturn true
            on { canRead() } doReturn false
        }
        val reader = CpuStatReader(restrictedFile, internalLogger = mock())

        // When
        val result = reader.readUserTime()

        // Then
        assertThat(result).isNull()
    }

    @Test
    fun `M return null W readUserTime() {invalid content}`(@StringForgery fakeContent: String) {
        // Given
        fakeFile.writeText(fakeContent)

        // When
        val result = testedReader.readUserTime()

        // Then
        assertThat(result).isNull()
    }

    @Test
    fun `M return null W readUserTime() {non-numeric utime}`(
        @StringForgery(regex = "[a-z]{3}") fakeNonNumeric: String
    ) {
        // Given
        fakeFile.writeText(generateStatContent(utime = fakeNonNumeric))

        // When
        val result = testedReader.readUserTime()

        // Then
        assertThat(result).isNull()
    }

    @Test
    fun `M read utime plus stime W readActiveTime()`() {
        // Given
        fakeFile.writeText(generateStatContent(fakeUtime))

        // When
        val result = testedReader.readActiveTime()

        // Then
        assertThat(result).isEqualTo((fakeUtime + fakeStime).toDouble())
    }

    @Test
    fun `M use only utime W readActiveTime() {stime field absent}`() {
        // Given — content with exactly 14 tokens (utime at [13], no stime at [14])
        fakeFile.writeText(generateStatContent(fakeUtime, includeStime = false))

        // When
        val result = testedReader.readActiveTime()

        // Then
        assertThat(result).isEqualTo(fakeUtime.toDouble())
    }

    @Test
    fun `M return null W readActiveTime() {non-numeric utime}`(
        @StringForgery(regex = "[a-z]{3}") fakeNonNumeric: String
    ) {
        // Given
        fakeFile.writeText(generateStatContent(utime = fakeNonNumeric))

        // When
        val result = testedReader.readActiveTime()

        // Then
        assertThat(result).isNull()
    }

    @Test
    fun `M return null W readActiveTime() {non-numeric stime}`(
        @StringForgery(regex = "[a-z]{3}") fakeNonNumeric: String
    ) {
        // Given — 15 tokens, numeric utime but non-numeric stime
        fakeFile.writeText(generateStatContent(utime = fakeUtime, stime = fakeNonNumeric))

        // When
        val result = testedReader.readActiveTime()

        // Then
        assertThat(result).isNull()
    }

    @Test
    fun `M return null W readActiveTime() {file doesn't exist}`() {
        // When
        val result = testedReader.readActiveTime()

        // Then
        assertThat(result).isNull()
    }

    @Test
    fun `M return null W readActiveTime() {file isn't readable}`() {
        // Given
        val restrictedFile = mock<File> {
            on { exists() } doReturn true
            on { canRead() } doReturn false
        }
        val reader = CpuStatReader(restrictedFile, internalLogger = mock())

        // When
        val result = reader.readActiveTime()

        // Then
        assertThat(result).isNull()
    }

    @Test
    fun `M return null W readActiveTime() {invalid content}`(@StringForgery fakeContent: String) {
        // Given
        fakeFile.writeText(fakeContent)

        // When
        val result = testedReader.readActiveTime()

        // Then
        assertThat(result).isNull()
    }

    private fun generateStatContent(utime: Any, stime: Any = fakeStime, includeStime: Boolean = true): String {
        val fields = mutableListOf(
            fakePid, fakeCommand, fakeState, fakePpid, fakePgrp,
            fakeSession, fakeTtyNr, fakeTpgid, fakeFlags,
            fakeMinFlt, fakeCMinFlt, fakeMajFlt, fakeCMajFlt,
            utime
        )
        if (includeStime) fields.add(stime)
        return fields.joinToString(" ")
    }
}
