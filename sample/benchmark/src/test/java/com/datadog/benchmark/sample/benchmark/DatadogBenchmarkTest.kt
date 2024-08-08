/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.benchmark.sample.benchmark

import android.os.Bundle
import com.datadog.android.sessionreplay.SessionReplay
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
class DatadogBenchmarkTest {

    @Mock
    private lateinit var mockedBundle: Bundle

    @Test
    fun `M build correct config W resolve bundle {scenario = 'sr', run = 'baseline'}`() {
        whenever(mockedBundle.getString("synthetics.benchmark.scenario")).thenReturn("sr")
        whenever(mockedBundle.getString("synthetics.benchmark.run")).thenReturn("baseline")

        val config = DatadogBenchmark.Config.resolveSyntheticsBundle(mockedBundle)
        Assertions.assertThat(config.scenario).isEqualTo(SyntheticsScenario.SessionReplay)
        Assertions.assertThat(config.run).isEqualTo(SyntheticsRun.Baseline)
    }

    @Test
    fun `M build correct config W resolve bundle {scenario = 'sr', run = 'instrumented'}`() {
        whenever(mockedBundle.getString("synthetics.benchmark.scenario")).thenReturn("sr")
        whenever(mockedBundle.getString("synthetics.benchmark.run")).thenReturn("instrumented")

        val config = DatadogBenchmark.Config.resolveSyntheticsBundle(mockedBundle)
        Assertions.assertThat(config.scenario).isEqualTo(SyntheticsScenario.SessionReplay)
        Assertions.assertThat(config.run).isEqualTo(SyntheticsRun.Instrumented)
    }

    @Test
    fun `M build empty config W resolve invalid bundle`() {
        whenever(mockedBundle.getString("synthetics.benchmark.scenario")).thenReturn("")
        whenever(mockedBundle.getString("synthetics.benchmark.run")).thenReturn("")

        val config = DatadogBenchmark.Config.resolveSyntheticsBundle(mockedBundle)
        Assertions.assertThat(config.scenario).isEqualTo(null)
        Assertions.assertThat(config.run).isEqualTo(null)
    }

    @Test
    fun `M enable session replay W config run is instrumented`() {
        // Given
        val config =
            DatadogBenchmark.Config(run = SyntheticsRun.Instrumented, scenario = SyntheticsScenario.SessionReplay)
        Mockito.mockStatic(SessionReplay::class.java).use {
            // When
            DatadogBenchmark(config)

            // Then
            it.verify {
                SessionReplay.enable(
                    sessionReplayConfiguration = any(),
                    sdkCore = any()
                )
            }
        }
    }

    @Test
    fun `M skip session replay W config run is baseline`() {
        // Given
        val config = DatadogBenchmark.Config(
            run = SyntheticsRun.Baseline,
            scenario = SyntheticsScenario.SessionReplay
        )
        Mockito.mockStatic(SessionReplay::class.java).use {
            // When
            DatadogBenchmark(config)

            // Then
            it.verifyNoInteractions()
        }
    }

    @Test
    fun `M skip session replay W config scenario is not sr`() {
        // Given
        val config = DatadogBenchmark.Config(
            run = SyntheticsRun.Baseline,
            scenario = SyntheticsScenario.Logs
        )
        Mockito.mockStatic(SessionReplay::class.java).use {
            // When
            DatadogBenchmark(config)

            // Then
            it.verifyNoInteractions()
        }
    }
}
