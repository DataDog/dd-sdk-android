/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.benchmark.sample.config

import com.datadog.android.api.SdkCore
import com.datadog.android.log.Logs
import com.datadog.android.rum.Rum
import com.datadog.android.sessionreplay.SessionReplay
import com.datadog.benchmark.sample.DatadogFeaturesInitializer
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.MockedStatic
import org.mockito.Mockito
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
class DatadogFeaturesInitializerTest {

    @Mock
    private lateinit var mockSdkCore: SdkCore

    @Test
    fun `M enable session replay and rum W run is instrumented and scenario is sr`() {
        // Given
        val config =
            BenchmarkConfig(run = SyntheticsRun.Instrumented, scenario = SyntheticsScenario.SessionReplay)

        mockAllAndCheck(config) {
            // Then
            sessionReplay.verifySessionReplayEnabled()
            rum.verifyRumEnabled()
            logs.verifyNoInteractions()
        }
    }

    @Test
    fun `M skip session replay and enable rum W run is baseline and scenario is sr`() {
        // Given
        val config = BenchmarkConfig(
            run = SyntheticsRun.Baseline,
            scenario = SyntheticsScenario.SessionReplay
        )

        mockAllAndCheck(config) {
            // Then
            sessionReplay.verifyNoInteractions()
            rum.verifyRumEnabled()
            logs.verifyNoInteractions()
        }
    }

    @Test
    fun `M enable nothing W scenario is logsCustom and run is baseline`() {
        // Given
        val config = BenchmarkConfig(
            run = SyntheticsRun.Baseline,
            scenario = SyntheticsScenario.LogsCustom
        )

        mockAllAndCheck(config) {
            // Then
            sessionReplay.verifyNoInteractions()
            rum.verifyNoInteractions()
            logs.verifyNoInteractions()
        }
    }

    @Test
    fun `M enable logs W scenario is logsCustom and run is instrumented`() {
        // Given
        val config = BenchmarkConfig(
            run = SyntheticsRun.Instrumented,
            scenario = SyntheticsScenario.LogsCustom
        )

        mockAllAndCheck(config) {
            // Then
            sessionReplay.verifyNoInteractions()
            rum.verifyNoInteractions()
            logs.verifyLogsEnabled()
        }
    }

    private fun mockAllAndCheck(config: BenchmarkConfig, thenBlock: MockedStatics.() -> Unit) {
        Mockito.mockStatic(SessionReplay::class.java).use { sessionReplayStatic ->
            Mockito.mockStatic(Rum::class.java).use { rumStatic ->
                Mockito.mockStatic(Logs::class.java).use { logsStatic ->
                    // When
                    DatadogFeaturesInitializer(
                        sdkCore = mockSdkCore
                    ).initialize(config)

                    // Then
                    thenBlock(MockedStatics(sessionReplayStatic, rumStatic, logsStatic))
                }
            }
        }
    }
}

private class MockedStatics(
    val sessionReplay: MockedStatic<SessionReplay>,
    val rum: MockedStatic<Rum>,
    val logs: MockedStatic<Logs>
)

private fun MockedStatic<SessionReplay>.verifySessionReplayEnabled() {
    verify {
        SessionReplay.enable(
            sessionReplayConfiguration = any(),
            sdkCore = any()
        )
    }
}

private fun MockedStatic<Rum>.verifyRumEnabled() {
    verify {
        Rum.enable(
            rumConfiguration = any(),
            sdkCore = any()
        )
    }
}

private fun MockedStatic<Logs>.verifyLogsEnabled() {
    verify {
        Logs.enable(
            logsConfiguration = any(),
            sdkCore = any()
        )
    }
}
