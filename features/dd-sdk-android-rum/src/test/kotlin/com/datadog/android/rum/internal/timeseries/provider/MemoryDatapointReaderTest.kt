/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.timeseries.provider

import com.datadog.android.internal.time.TimeProvider
import com.datadog.android.rum.internal.timeseries.MemorySnapshot
import com.datadog.android.rum.internal.vitals.ProcStatusReader
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
import java.util.concurrent.TimeUnit

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class MemoryDatapointReaderTest {

    private lateinit var testedReader: MemoryDatapointReader

    @Mock
    lateinit var mockProcStatusReader: ProcStatusReader

    @Mock
    lateinit var mockTimeProvider: TimeProvider

    @LongForgery(min = 1L)
    var fakeTimestampMs: Long = 0L

    @LongForgery(min = 1L)
    var fakeIntervalMs: Long = 0L

    @LongForgery(min = 1L, max = MAX_MEMORY_KIB)
    var fakeRssAnonKib: Long = 0L

    @LongForgery(min = 1L, max = MAX_MEMORY_KIB)
    var fakeVmRssKib: Long = 0L

    @LongForgery(min = 0L, max = MAX_MEMORY_KIB)
    var fakeVmSwapKib: Long = 0L

    @BeforeEach
    fun `set up`() {
        whenever(mockTimeProvider.getServerTimestampMillis()) doReturn fakeTimestampMs
        testedReader = MemoryDatapointReader(
            procStatusReader = mockProcStatusReader,
            timeProvider = mockTimeProvider,
            intervalMs = fakeIntervalMs
        )
    }

    @Test
    fun `M return RssAnon plus VmSwap W read() { all fields present }`() {
        // Given
        stubFields(Field.RSS_ANON to fakeRssAnonKib, Field.VM_RSS to fakeVmRssKib, Field.VM_SWAP to fakeVmSwapKib)

        // When
        val result = testedReader.read()

        // Then
        checkNotNull(result)
        assertThat(result.timestampNs).isEqualTo(TimeUnit.MILLISECONDS.toNanos(fakeTimestampMs))
        assertThat(result.value).isEqualTo(
            MemorySnapshot(footprintKib = fakeRssAnonKib + fakeVmSwapKib, nonResidentKib = fakeVmSwapKib)
        )
        verify(mockProcStatusReader).readFieldsKib(listOf(Field.RSS_ANON, Field.VM_RSS, Field.VM_SWAP))
    }

    @Test
    fun `M fall back to VmRSS W read() { RssAnon missing }`() {
        // Given
        stubFields(Field.VM_RSS to fakeVmRssKib, Field.VM_SWAP to fakeVmSwapKib)

        // When
        val result = testedReader.read()

        // Then
        checkNotNull(result)
        assertThat(result.value).isEqualTo(
            MemorySnapshot(footprintKib = fakeVmRssKib + fakeVmSwapKib, nonResidentKib = fakeVmSwapKib)
        )
    }

    @Test
    fun `M report no non-resident memory W read() { VmSwap missing }`() {
        // Given
        stubFields(Field.RSS_ANON to fakeRssAnonKib, Field.VM_RSS to fakeVmRssKib)

        // When
        val result = testedReader.read()

        // Then
        checkNotNull(result)
        assertThat(result.value).isEqualTo(MemorySnapshot(footprintKib = fakeRssAnonKib, nonResidentKib = 0L))
    }

    @Test
    fun `M return null W read() { RssAnon and VmRSS missing }`() {
        // Given
        stubFields(Field.VM_SWAP to fakeVmSwapKib)

        // When
        val result = testedReader.read()

        // Then
        assertThat(result).isNull()
    }

    @Test
    fun `M return null W read() { no field read }`() {
        // Given
        stubFields()

        // When
        val result = testedReader.read()

        // Then
        assertThat(result).isNull()
    }

    private fun stubFields(vararg fieldsKib: Pair<Field, Long>) {
        whenever(mockProcStatusReader.readFieldsKib(any())) doReturn fieldsKib.toMap()
    }

    private companion object {
        const val MAX_MEMORY_KIB = 16L * 1024L * 1024L
    }
}
