/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.vitals

import android.view.Choreographer
import com.datadog.android.utils.forge.Configurator
import com.datadog.tools.unit.setStaticValue
import com.nhaarman.mockitokotlin2.verify
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
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class FrameRateVitalReaderTest {

    lateinit var testedReader: FrameRateVitalReader

    @Mock
    lateinit var mockChoreographer: Choreographer

    @BeforeEach
    fun `set up`() {
        testedReader = FrameRateVitalReader()

        Choreographer::class.java.setStaticValue(
            "sThreadInstance",
            object : ThreadLocal<Choreographer>() {
                override fun initialValue(): Choreographer {
                    return mockChoreographer
                }
            }
        )
    }

    @Test
    fun `𝕄 return null 𝕎 readVitalData() {no data}`() {
        // When
        val result = testedReader.readVitalData()

        // Then
        assertThat(result).isNull()
    }

    @Test
    fun `𝕄 return null 𝕎 readVitalData() {only one frame timestamp}`(
        @LongForgery timestampNs: Long
    ) {
        // Given
        testedReader.doFrame(timestampNs)

        // When
        val result = testedReader.readVitalData()

        // Then
        assertThat(result).isNull()
    }

    @Test
    fun `𝕄 return null 𝕎 readVitalData() {duration is null}`(
        @LongForgery timestampNs: Long
    ) {
        // Given
        testedReader.doFrame(timestampNs)
        testedReader.doFrame(timestampNs)

        // When
        val result = testedReader.readVitalData()

        // Then
        assertThat(result).isNull()
    }

    @Test
    fun `𝕄 return last frameRate 𝕎 readVitalData() {two frame timestamp}`(
        @LongForgery timestampNs: Long,
        @LongForgery(1, ONE_SECOND_NS) frameDurationNs: Long
    ) {
        // Given
        testedReader.doFrame(timestampNs)
        testedReader.doFrame(timestampNs + frameDurationNs)
        val expectedFrameRate = ONE_SECOND_NS.toDouble() / frameDurationNs.toDouble()

        // When
        val result = testedReader.readVitalData()

        // Then
        assertThat(result).isEqualTo(expectedFrameRate)
    }

    @Test
    fun `𝕄 return last frameRate 𝕎 readVitalData() {many frame timestamp}`(
        @LongForgery initialTimestampNs: Long,
        @LongForgery(1, ONE_SECOND_NS) frameDurationsNs: List<Long>
    ) {
        // Given
        var timestampNs = initialTimestampNs
        testedReader.doFrame(timestampNs)
        frameDurationsNs.forEach {
            timestampNs += it
            testedReader.doFrame(timestampNs)
        }
        val expectedFrameRate = ONE_SECOND_NS.toDouble() / frameDurationsNs.last().toDouble()

        // When
        val result = testedReader.readVitalData()

        // Then
        assertThat(result).isEqualTo(expectedFrameRate)
    }

    @Test
    fun `𝕄 schedule callback 𝕎 doFrame() {only one frame timestamp`(
        @LongForgery timestampNs: Long
    ) {
        // Given

        // When
        testedReader.doFrame(timestampNs)

        // Then
        verify(mockChoreographer).postFrameCallback(testedReader)
    }

    companion object {
        const val ONE_SECOND_NS: Long = 1000L * 1000L * 1000L
    }
}
