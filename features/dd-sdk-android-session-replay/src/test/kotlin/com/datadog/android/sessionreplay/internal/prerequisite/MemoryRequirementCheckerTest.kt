/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.prerequisite

import com.datadog.android.api.InternalLogger
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.annotation.LongForgery
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
import org.mockito.quality.Strictness
import java.io.File

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(ForgeConfigurator::class)
class MemoryRequirementCheckerTest {

    private lateinit var testedMemoryRequirementChecker: MemoryRequirementChecker

    @TempDir
    lateinit var tempDir: File

    private lateinit var fakeMemoryFile: File

    @Mock
    private lateinit var mockInternalLogger: InternalLogger

    @IntForgery(min = 0)
    private var fakeMemorySizeInMb: Int = 0

    @LongForgery(min = 0)
    private var fakeMemoryFree: Long = 0L

    @LongForgery(min = 0)
    private var fakeMemoryAvailable: Long = 0L

    @BeforeEach
    fun `set up`() {
        fakeMemoryFile = File(tempDir, "meminfo")
        testedMemoryRequirementChecker = MemoryRequirementChecker(
            minRamSizeMb = fakeMemorySizeInMb,
            memInfoFile = fakeMemoryFile,
            internalLogger = mockInternalLogger
        )
    }

    @Test
    fun `M have correct name W call name()`() {
        assertThat(testedMemoryRequirementChecker.name()).isEqualTo("ram")
    }

    @Test
    fun `M return true W check memory size meet configuration`() {
        // Given
        val fakeMemoryFileContent = generateFakeMemoryFileContent(fakeMemorySizeInMb + 1)
        testedMemoryRequirementChecker = MemoryRequirementChecker(
            minRamSizeMb = fakeMemorySizeInMb,
            memInfoFile = fakeMemoryFile,
            internalLogger = mockInternalLogger
        )
        // When
        fakeMemoryFile.writeText(fakeMemoryFileContent)
        val result = testedMemoryRequirementChecker.checkMinimumRequirement()

        // Then
        assertThat(result).isTrue()
    }

    @Test
    fun `M return false W check cpu core number not meet configuration`() {
        // Given
        val fakeMemoryFileContent = generateFakeMemoryFileContent(fakeMemorySizeInMb - 1)
        testedMemoryRequirementChecker = MemoryRequirementChecker(
            minRamSizeMb = fakeMemorySizeInMb,
            memInfoFile = fakeMemoryFile,
            internalLogger = mockInternalLogger
        )

        // When
        fakeMemoryFile.writeText(fakeMemoryFileContent)
        val result = testedMemoryRequirementChecker.checkMinimumRequirement()

        // Then
        assertThat(result).isFalse()
    }

    @Test
    fun `M return correct checked value W check check`() {
        // Given
        val fakeMemoryFileContent = generateFakeMemoryFileContent(fakeMemorySizeInMb)
        testedMemoryRequirementChecker = MemoryRequirementChecker(
            minRamSizeMb = fakeMemorySizeInMb,
            memInfoFile = fakeMemoryFile,
            internalLogger = mockInternalLogger
        )

        // When
        fakeMemoryFile.writeText(fakeMemoryFileContent)
        testedMemoryRequirementChecker.checkMinimumRequirement()

        // Then
        assertThat(testedMemoryRequirementChecker.checkedValue()).isEqualTo((fakeMemorySizeInMb).toLong())
    }

    private fun generateFakeMemoryFileContent(memorySizeInMb: Int): String {
        return mapOf<String, Any>(
            "MemTotal" to (memorySizeInMb.toLong() * 1000),
            "MemFree" to fakeMemoryFree,
            "MemAvailable" to fakeMemoryAvailable
        ).map { (key, value) -> "$key:\t$value" }
            .joinToString("\n")
    }
}
