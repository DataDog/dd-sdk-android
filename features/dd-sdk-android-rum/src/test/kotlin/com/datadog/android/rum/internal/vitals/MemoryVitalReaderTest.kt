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
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.io.File

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class MemoryVitalReaderTest {

    lateinit var testedReader: VitalReader

    @TempDir
    lateinit var tempDir: File

    lateinit var fakeFile: File

    @StringForgery(regex = "(\\.[a-z]+)+")
    lateinit var fakeName: String

    @StringForgery(regex = "[RSDZTtWXxKWP]")
    lateinit var fakeState: String

    @IntForgery(1)
    var fakePid: Int = 0

    @IntForgery(1, 0x7F)
    var fakeVmRss: Int = 0

    @IntForgery(1, 256)
    var fakeThreads: Int = 0

    lateinit var fakeStatusContent: String

    @BeforeEach
    fun `set up`() {
        fakeFile = File(tempDir, "stat")
        fakeStatusContent = generateStatusContent(fakeVmRss)
        testedReader = MemoryVitalReader(fakeFile, internalLogger = mock())
    }

    @Test
    fun `M read unix stats file W init()`() {
        // When
        val testedReader = MemoryVitalReader(internalLogger = mock())

        // Then
        assertThat(testedReader.statusFile).isEqualTo(MemoryVitalReader.STATUS_FILE)
    }

    @Test
    fun `M read correct data W readVitalData()`() {
        // Given
        fakeFile.writeText(fakeStatusContent)

        // When
        val result = testedReader.readVitalData()

        // Then
        assertThat(result).isEqualTo(fakeVmRss.toDouble() * 1000)
    }

    @Test
    fun `M read correct data W readVitalData() {multiple times}`(
        @IntForgery(1) vmRssValuesKb: List<Int>
    ) {
        // Given
        val results = mutableListOf<Double>()
        // When
        vmRssValuesKb.forEach { vmRss ->
            fakeFile.writeText(generateStatusContent(vmRss))
            val result = testedReader.readVitalData()
            results.add(result!!)
        }

        // Then
        assertThat(results).isEqualTo(vmRssValuesKb.map { vmRssKb -> vmRssKb.toDouble() * 1000 })
    }

    @Test
    fun `M return null W readVitalData() {file doesn't exist}`() {
        // When
        val result = testedReader.readVitalData()

        // Then
        assertThat(result).isNull()
    }

    @Test
    fun `M return null W readVitalData() {file isn't readable}`() {
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
    fun `M return null W readVitalData() {file has invalid data}`(
        @StringForgery content: String
    ) {
        // Given
        fakeFile.writeText(content)

        // When
        val result = testedReader.readVitalData()

        // Then
        assertThat(result).isNull()
    }

    private fun generateStatusContent(vmRss: Int): String {
        return mapOf<String, Any>(
            "Name" to fakeName,
            "State" to fakeState,
            "Pid" to fakePid,
            "VmRSS" to "$vmRss kB".padStart(11, ' '),
            "Threads" to fakeThreads
        )
            .map { (key, value) -> "$key:\t$value" }
            .joinToString("\n")
    }
}
