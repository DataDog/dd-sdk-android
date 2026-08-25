/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.timeseries

import com.datadog.android.rum.utils.forge.Configurator
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions

@Extensions(
    ExtendWith(ForgeExtension::class)
)
@ForgeConfiguration(Configurator::class)
internal class BufferTest {

    @Test
    fun `M return false W isFull() { buffer is empty }`(@IntForgery(min = 1, max = 256) fakeSize: Int) {
        // Given
        val testedBuffer = Buffer<Double>(fakeSize)

        // When / Then
        assertThat(testedBuffer.isFull()).isFalse()
    }

    @Test
    fun `M return false W isFull() { items below capacity }`(
        @IntForgery(min = 2, max = 256) fakeSize: Int,
        forge: Forge
    ) {
        // Given
        val testedBuffer = Buffer<Double>(fakeSize)
        repeat(fakeSize - 1) { testedBuffer.add(forge.getForgery<DataPoint<Double>>()) }

        // When / Then
        assertThat(testedBuffer.isFull()).isFalse()
    }

    @Test
    fun `M return true W isFull() { items at capacity }`(@IntForgery(min = 1, max = 256) fakeSize: Int, forge: Forge) {
        // Given
        val testedBuffer = Buffer<Double>(fakeSize)
        repeat(fakeSize) { testedBuffer.add(forge.getForgery<DataPoint<Double>>()) }

        // When / Then
        assertThat(testedBuffer.isFull()).isTrue()
    }

    @Test
    fun `M return true W isFull() { items beyond capacity }`(
        @IntForgery(min = 1, max = 64) fakeSize: Int,
        @IntForgery(min = 1, max = 8) fakeOverflow: Int,
        forge: Forge
    ) {
        // Given
        val testedBuffer = Buffer<Double>(fakeSize)
        repeat(fakeSize + fakeOverflow) { testedBuffer.add(forge.getForgery<DataPoint<Double>>()) }

        // When / Then
        assertThat(testedBuffer.isFull()).isTrue()
    }

    @Test
    fun `M return empty list W drain() { buffer is empty }`(@IntForgery(min = 1, max = 256) fakeSize: Int) {
        // Given
        val testedBuffer = Buffer<Double>(fakeSize)

        // When
        val result = testedBuffer.drain()

        // Then
        assertThat(result).isEmpty()
    }

    @Test
    fun `M return all items and clear W drain() { buffer not empty }`(
        @IntForgery(min = 1, max = 16) fakeSize: Int,
        forge: Forge
    ) {
        // Given
        val testedBuffer = Buffer<Double>(fakeSize)
        val fakePoints = (0 until fakeSize).map { forge.getForgery<DataPoint<Double>>() }
        fakePoints.forEach { testedBuffer.add(it) }

        // When
        val drained = testedBuffer.drain()

        // Then
        assertThat(drained).containsExactlyElementsOf(fakePoints)
        assertThat(testedBuffer.isFull()).isFalse()
        assertThat(testedBuffer.drain()).isEmpty()
    }

    @Test
    fun `M return a detached copy W drain() { buffer is reused afterwards }`(
        @IntForgery(min = 2, max = 16) fakeSize: Int,
        forge: Forge
    ) {
        // Given
        val testedBuffer = Buffer<Double>(fakeSize)
        val fakePoints = (0 until fakeSize).map { forge.getForgery<DataPoint<Double>>() }
        fakePoints.forEach { testedBuffer.add(it) }
        val drained = testedBuffer.drain()

        // When
        testedBuffer.add(forge.getForgery<DataPoint<Double>>())

        // Then
        assertThat(drained).containsExactlyElementsOf(fakePoints)
    }
}
