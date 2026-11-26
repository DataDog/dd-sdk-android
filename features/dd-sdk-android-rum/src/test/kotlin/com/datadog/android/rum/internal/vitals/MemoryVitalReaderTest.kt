/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.vitals

import com.datadog.android.rum.internal.vitals.ProcStatusReader.Field
import com.datadog.android.rum.utils.forge.Configurator
import fr.xgouchet.elmyr.annotation.LongForgery
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
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class MemoryVitalReaderTest {

    private lateinit var testedReader: MemoryVitalReader

    @Mock
    lateinit var mockProcStatusReader: ProcStatusReader

    @BeforeEach
    fun `set up`() {
        testedReader = MemoryVitalReader(mockProcStatusReader)
    }

    @Test
    fun `M return VmRSS times 1000 W readVitalData()`(
        @LongForgery(min = 0L, max = MAX_MEMORY_KIB) fakeVmRssKib: Long
    ) {
        // Given
        whenever(mockProcStatusReader.readFieldsKib(any())) doReturn mapOf(Field.VM_RSS to fakeVmRssKib)

        // When
        val result = testedReader.readVitalData()

        // Then
        assertThat(result).isEqualTo(fakeVmRssKib.toDouble() * BYTES_IN_KB)
        verify(mockProcStatusReader).readFieldsKib(listOf(Field.VM_RSS))
    }

    @Test
    fun `M return null W readVitalData() { VmRSS missing }`() {
        // Given
        whenever(mockProcStatusReader.readFieldsKib(any())) doReturn emptyMap()

        // When
        val result = testedReader.readVitalData()

        // Then
        assertThat(result).isNull()
    }

    private companion object {
        const val BYTES_IN_KB = 1000
        const val MAX_MEMORY_KIB = 16L * 1024L * 1024L
    }
}
