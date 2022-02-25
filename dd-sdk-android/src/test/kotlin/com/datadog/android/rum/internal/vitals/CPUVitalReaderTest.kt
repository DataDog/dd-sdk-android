/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.vitals

import com.datadog.android.utils.forge.Configurator
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import java.io.File
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.junit.jupiter.api.io.TempDir
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class CPUVitalReaderTest {

    lateinit var testedReader: VitalReader

    @TempDir
    lateinit var tempDir: File

    lateinit var fakeFile: File

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

    @IntForgery(1)
    var fakeUtime: Int = 0

    @IntForgery(1)
    var fakeStime: Int = 0

    @IntForgery(1)
    var fakeCUtime: Int = 0

    @IntForgery(1)
    var fakeCStime: Int = 0

    @IntForgery(-100, -2)
    var fakePriority: Int = 0

    lateinit var fakeStatContent: String

    @BeforeEach
    fun `set up`() {
        fakeFile = File(tempDir, "stat")
        fakeStatContent = generateFakeContent()
        testedReader = CPUVitalReader(fakeFile)
    }

    @Test
    fun `𝕄 read unix stats file 𝕎 init()`() {
        // When
        val testedReader = CPUVitalReader()

        // Then
        assertThat(testedReader.statFile).isEqualTo(CPUVitalReader.STAT_FILE)
    }

    @Test
    fun `𝕄 read correct data 𝕎 readVitalData()`() {
        // Given
        fakeFile.writeText(fakeStatContent)

        // When
        val result = testedReader.readVitalData()

        // Then
        assertThat(result).isEqualTo(fakeUtime.toDouble())
    }

    @Test
    fun `𝕄 read correct data 𝕎 readVitalData() {multiple times}`(
        @IntForgery(1) utimes: List<Int>
    ) {
        // Given
        val results = mutableListOf<Double>()

        // When
        utimes.forEach { utime ->
            fakeUtime = utime
            fakeFile.writeText(generateFakeContent())
            val result = testedReader.readVitalData()
            results.add(result!!)
        }

        // Then
        assertThat(results).isEqualTo(utimes.map { utime -> utime.toDouble() })
    }

    @Test
    fun `𝕄 return null 𝕎 readVitalData() {file doesn't exist}`() {
        // When
        val result = testedReader.readVitalData()

        // Then
        assertThat(result).isNull()
    }

    @Test
    fun `𝕄 return null 𝕎 readVitalData() {file isn't readable}`() {
        // Given
        val restrictedFile = mock<File>()
        whenever(restrictedFile.exists()) doReturn true
        whenever(restrictedFile.canRead()) doReturn false

        // When
        val result = testedReader.readVitalData()

        // Then
        assertThat(result).isNull()
    }

    @Test
    fun `𝕄 return null 𝕎 readVitalData() {file has invalid data}`(
        @StringForgery content: String
    ) {
        // Given
        fakeFile.writeText(content)

        // When
        val result = testedReader.readVitalData()

        // Then
        assertThat(result).isNull()
    }

    private fun generateFakeContent(): String {
        return listOf<Any>(
            fakePid, fakeCommand, fakeState, fakePpid, fakePgrp,
            fakeSession, fakeTtyNr, fakeTpgid, fakeFlags,
            fakeMinFlt, fakeCMinFlt, fakeMajFlt, fakeCMajFlt,
            fakeUtime, fakeStime, fakeCUtime, fakeCStime,
            fakePriority
        ).joinToString(" ")
    }
}
