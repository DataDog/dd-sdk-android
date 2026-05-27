/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.benchmark.sample.config

import com.datadog.android.api.SdkCore
import com.datadog.android.log.Logs
import com.datadog.android.profiling.ExperimentalProfilingApi
import com.datadog.android.profiling.Profiling
import com.datadog.android.rum.Rum
import com.datadog.android.rum.RumMonitor
import com.datadog.android.rum._RumInternalProxy
import com.datadog.android.sessionreplay.SessionReplay
import com.datadog.benchmark.sample.DatadogFeaturesInitializer
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.MockedStatic
import org.mockito.Mockito
import org.mockito.Mockito.mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.quality.Strictness

@OptIn(ExperimentalProfilingApi::class)
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
    fun `M enable session replay and rum W run is instrumented and scenario is srCompose`() {
        // Given
        val config = BenchmarkConfig(
            run = SyntheticsRun.Instrumented,
            scenario = SyntheticsScenario.SessionReplayCompose
        )

        mockAllAndCheck(config) {
            // Then
            sessionReplay.verifySessionReplayEnabled()
            rum.verifyRumEnabled()
            logs.verifyNoInteractions()
        }
    }

    @Test
    fun `M enable rum only W run is baseline and scenario is sr`() {
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
    fun `M enable rum only W run is baseline and scenario is srCompose`() {
        // Given
        val config = BenchmarkConfig(
            run = SyntheticsRun.Baseline,
            scenario = SyntheticsScenario.SessionReplayCompose
        )

        mockAllAndCheck(config) {
            // Then
            sessionReplay.verifyNoInteractions()
            rum.verifyRumEnabled()
            logs.verifyNoInteractions()
        }
    }

    @Test
    fun `M enable rum only W run is baseline and scenario is upload`() {
        // Given
        val config = BenchmarkConfig(
            run = SyntheticsRun.Baseline,
            scenario = SyntheticsScenario.Upload
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
            profiling.verifyNoInteractions()
        }
    }

    @Test
    fun `M enable rum W run is instrumented and scenario is profiling`() {
        // Given
        val config = BenchmarkConfig(
            run = SyntheticsRun.Instrumented,
            scenario = SyntheticsScenario.Profiling
        )

        mockAllAndCheck(config) {
            // Then
            sessionReplay.verifyNoInteractions()
            rum.verifyRumEnabled()
            logs.verifyNoInteractions()
            // Profiling.enable is gated on Build.VERSION.SDK_INT >= VANILLA_ICE_CREAM (API 35).
            // In JVM unit tests SDK_INT is 0, so the call is skipped here; the API-level gate
            // is exercised by instrumented tests.
            profiling.verifyNoInteractions()
        }
    }

    @Test
    fun `M enable nothing W run is baseline and scenario is profiling`() {
        // Given
        val config = BenchmarkConfig(
            run = SyntheticsRun.Baseline,
            scenario = SyntheticsScenario.Profiling
        )

        mockAllAndCheck(config) {
            // Then
            sessionReplay.verifyNoInteractions()
            rum.verifyNoInteractions()
            logs.verifyNoInteractions()
            profiling.verifyNoInteractions()
        }
    }

    private fun mockAllAndCheck(config: BenchmarkConfig, thenBlock: MockedStatics.() -> Unit) {
        val sessionReplayStatic = Mockito.mockStatic(SessionReplay::class.java)
        val rumStatic = Mockito.mockStatic(Rum::class.java)
        val logsStatic = Mockito.mockStatic(Logs::class.java)
        val profilingStatic = Mockito.mockStatic(Profiling::class.java)
        try {
            // When
            val mockMonitor = mock<RumMonitor>()
            class StubRumMonitor : RumMonitor by mockMonitor {
                override fun _getInternal(): _RumInternalProxy? {
                    return null
                }
            }

            DatadogFeaturesInitializer(
                sdkCore = { mockSdkCore },
                rumMonitor = { StubRumMonitor() }
            ).initialize(config, mock())

            // Then
            thenBlock(MockedStatics(sessionReplayStatic, rumStatic, logsStatic, profilingStatic))
        } finally {
            profilingStatic.close()
            logsStatic.close()
            rumStatic.close()
            sessionReplayStatic.close()
        }
    }
}

@OptIn(ExperimentalProfilingApi::class)
private class MockedStatics(
    val sessionReplay: MockedStatic<SessionReplay>,
    val rum: MockedStatic<Rum>,
    val logs: MockedStatic<Logs>,
    val profiling: MockedStatic<Profiling>
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
