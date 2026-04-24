/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.internal.sampling

import com.datadog.android.internal.forge.Configurator
import fr.xgouchet.elmyr.annotation.FloatForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(ForgeExtension::class)
@ForgeConfiguration(Configurator::class)
internal class DeterministicSamplingTest {

    @Test
    fun `M return combined rate W combinedSampleRate() {valid rates}`() {
        assertThat(DeterministicSampling.combinedSampleRate(50f, 20f)).isEqualTo(10f)
    }

    @Test
    fun `M return 0 W combinedSampleRate() {featureRate is 0}`(
        @FloatForgery(min = 0f, max = 100f) sessionRate: Float
    ) {
        assertThat(DeterministicSampling.combinedSampleRate(sessionRate, 0f)).isEqualTo(0f)
    }

    @Test
    fun `M return 0 W combinedSampleRate() {sessionRate is 0}`(
        @FloatForgery(min = 0f, max = 100f) featureRate: Float
    ) {
        assertThat(DeterministicSampling.combinedSampleRate(0f, featureRate)).isEqualTo(0f)
    }

    @Test
    fun `M return featureRate W combinedSampleRate() {sessionRate is 100}`() {
        assertThat(DeterministicSampling.combinedSampleRate(100f, 75f)).isEqualTo(75f)
    }

    @Test
    fun `M return sessionRate W combinedSampleRate() {featureRate is 100}`() {
        assertThat(DeterministicSampling.combinedSampleRate(75f, 100f)).isEqualTo(75f)
    }

    @Test
    fun `M return 100 W combinedSampleRate() {both rates are 100}`() {
        assertThat(DeterministicSampling.combinedSampleRate(100f, 100f)).isEqualTo(100f)
    }

    @Test
    fun `M return product divided by 100 W combinedSampleRate() {random valid rates}`(
        @FloatForgery(min = 0f, max = 100f) sessionRate: Float,
        @FloatForgery(min = 0f, max = 100f) featureRate: Float
    ) {
        val expected = sessionRate * featureRate / 100f
        assertThat(DeterministicSampling.combinedSampleRate(sessionRate, featureRate)).isEqualTo(expected)
    }

    @Test
    fun `M clamp to 100 W combinedSampleRate() {rates exceed valid range}`() {
        assertThat(DeterministicSampling.combinedSampleRate(200f, 100f)).isEqualTo(100f)
    }

    @Test
    fun `M clamp to 0 W combinedSampleRate() {negative rate}`() {
        assertThat(DeterministicSampling.combinedSampleRate(-50f, 50f)).isEqualTo(0f)
    }
}
