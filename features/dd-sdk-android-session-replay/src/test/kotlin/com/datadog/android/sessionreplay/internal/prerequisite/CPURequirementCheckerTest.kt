/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.prerequisite

import com.datadog.android.api.InternalLogger
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.io.File
import java.io.FilenameFilter

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(ForgeConfigurator::class)
class CPURequirementCheckerTest {

    private lateinit var testedCPURequirementChecker: CPURequirementChecker

    @Mock
    private lateinit var mockCpuDir: File

    @Mock
    private lateinit var mockInternalLogger: InternalLogger

    @IntForgery(min = 1, max = 10)
    private var fakeCpuCores: Int = 0

    private lateinit var fakeCpuFileList: List<File>

    @BeforeEach
    fun `set up`() {
        fakeCpuFileList = mockCpuFileList(fakeCpuCores)
        testedCPURequirementChecker = CPURequirementChecker(
            minCPUCores = fakeCpuCores,
            cpuDirFile = mockCpuDir,
            internalLogger = mockInternalLogger
        )
    }

    @Test
    fun `M have correct name W name()`() {
        assertThat(testedCPURequirementChecker.name()).isEqualTo("cpu")
    }

    @Test
    fun `M return true W checkMinimumRequirement  { cpu core number meets configuration }`() {
        // Given
        val fakeCpuFileList = mockCpuFileList(fakeCpuCores + 1)

        // When
        whenever(mockCpuDir.listFiles(any<FilenameFilter>())).doReturn(fakeCpuFileList.toTypedArray())
        val result = testedCPURequirementChecker.checkMinimumRequirement()

        // Then
        assertThat(result).isTrue()
    }

    @Test
    fun `M return false W checkMinimumRequirement  { cpu core number does not meet configuration }`() {
        // Given
        val fakeCpuFileList = mockCpuFileList(fakeCpuCores - 1)

        // When
        whenever(mockCpuDir.listFiles(any<FilenameFilter>())).doReturn(fakeCpuFileList.toTypedArray())
        val result = testedCPURequirementChecker.checkMinimumRequirement()

        // Then
        assertThat(result).isFalse()
    }

    @Test
    fun `M return correct checked value W checkMinimumRequirement`() {
        // Given
        val fakeCpuFileList = mockCpuFileList(fakeCpuCores)

        // When
        whenever(mockCpuDir.listFiles(any<FilenameFilter>())).doReturn(fakeCpuFileList.toTypedArray())
        testedCPURequirementChecker.checkMinimumRequirement()

        // Then
        assertThat(testedCPURequirementChecker.checkedValue()).isEqualTo(fakeCpuCores)
    }

    private fun mockCpuFileList(number: Int): List<File> {
        val fakeCpuFileList = mutableListOf<File>()
        repeat(number) { index ->
            fakeCpuFileList += mock<File> {
                whenever(it.name).doReturn("cpu$index")
            }
        }
        return fakeCpuFileList
    }
}
