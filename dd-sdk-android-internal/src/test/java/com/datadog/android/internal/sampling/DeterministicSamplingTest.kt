/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.internal.sampling

import fr.xgouchet.elmyr.annotation.FloatForgery
import fr.xgouchet.elmyr.annotation.LongForgery
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
internal class DeterministicSamplingTest {

    @RepeatedTest(32)
    fun `M always return true W computeSamplingDecision() {sampleRate is 100}`(
        @LongForgery fakeId: Long
    ) {
        assertThat(computeSamplingDecision(100f, fakeId.toULong())).isTrue()
    }

    @RepeatedTest(32)
    fun `M always return true W computeSamplingDecision() {sampleRate above 100}`(
        @FloatForgery(min = 100.01f, max = 200f) fakeSampleRate: Float,
        @LongForgery fakeId: Long
    ) {
        assertThat(computeSamplingDecision(fakeSampleRate, fakeId.toULong())).isTrue()
    }

    @RepeatedTest(32)
    fun `M always return false W computeSamplingDecision() {sampleRate is 0}`(
        @LongForgery fakeId: Long
    ) {
        assertThat(computeSamplingDecision(0f, fakeId.toULong())).isFalse()
    }

    @RepeatedTest(32)
    fun `M always return false W computeSamplingDecision() {sampleRate below 0}`(
        @FloatForgery(min = -100f, max = -0.01f) fakeSampleRate: Float,
        @LongForgery fakeId: Long
    ) {
        assertThat(computeSamplingDecision(fakeSampleRate, fakeId.toULong())).isFalse()
    }

    @Test
    fun `M return deterministic result W computeSamplingDecision() {same id same rate}`(
        @FloatForgery(min = 1f, max = 99f) fakeSampleRate: Float,
        @LongForgery fakeId: Long
    ) {
        val first = computeSamplingDecision(fakeSampleRate, fakeId.toULong())
        val second = computeSamplingDecision(fakeSampleRate, fakeId.toULong())
        assertThat(first).isEqualTo(second)
    }
}
