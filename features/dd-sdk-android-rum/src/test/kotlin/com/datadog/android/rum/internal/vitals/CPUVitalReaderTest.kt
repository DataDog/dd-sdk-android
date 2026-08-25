/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.vitals

import fr.xgouchet.elmyr.annotation.DoubleForgery
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
internal class CPUVitalReaderTest {

    lateinit var testedReader: VitalReader

    @Mock
    lateinit var mockCpuStatReader: CpuStatReader

    @BeforeEach
    fun `set up`() {
        testedReader = CPUVitalReader(mockCpuStatReader)
    }

    @Test
    fun `M return user time W readVitalData()`(@DoubleForgery fakeUserTime: Double) {
        // Given
        whenever(mockCpuStatReader.readUserTime()) doReturn fakeUserTime

        // When
        val result = testedReader.readVitalData()

        // Then
        assertThat(result).isEqualTo(fakeUserTime)
    }

    @Test
    fun `M return null W readVitalData() {no cpu data}`() {
        // Given
        whenever(mockCpuStatReader.readUserTime()) doReturn null

        // When
        val result = testedReader.readVitalData()

        // Then
        assertThat(result).isNull()
    }
}
