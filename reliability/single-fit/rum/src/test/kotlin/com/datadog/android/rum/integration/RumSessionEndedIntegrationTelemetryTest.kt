/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.integration

import com.datadog.android.core.stub.StubSDKCore
import com.datadog.android.rum.GlobalRumMonitor
import com.datadog.android.rum.Rum
import com.datadog.android.rum.RumConfiguration
import com.datadog.android.rum.integration.tests.assertj.TelemetryMetricAssert.Companion.assertThat
import com.datadog.android.rum.integration.tests.elmyr.RumIntegrationForgeConfigurator
import com.datadog.android.rum.integration.tests.utils.MainLooperTestConfiguration
import com.datadog.tools.unit.annotations.TestConfigurationsProvider
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import com.datadog.tools.unit.extensions.config.TestConfiguration
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class),
    ExtendWith(TestConfigurationExtension::class)
)
@ForgeConfiguration(RumIntegrationForgeConfigurator::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RumSessionEndedIntegrationTelemetryTest {

    @StringForgery
    private lateinit var fakeApplicationId: String

    private lateinit var fakeRumConfiguration: RumConfiguration

    private lateinit var stubSdkCore: StubSDKCore

    @BeforeEach
    fun `set up`(forge: Forge) {
        stubSdkCore = StubSDKCore(forge)
        fakeRumConfiguration = RumConfiguration.Builder(fakeApplicationId)
            .trackNonFatalAnrs(false)
            .setTelemetrySampleRate(100f)
            .build()
    }

    @Test
    fun `M receive an event with 'was_stopped' is true W stopSession()`(
        @StringForgery viewKey: String,
        @StringForgery viewName: String
    ) {
        // Given
        Rum.enable(fakeRumConfiguration, stubSdkCore)
        val rumMonitor = GlobalRumMonitor.get(stubSdkCore)

        rumMonitor.startView(key = viewKey, name = viewName)

        // When
        rumMonitor.stopSession()

        // Then
        assertThat(stubSdkCore.lastMetric())
            .hasWasStopped(true)
    }

    @Test
    fun `M receive an event with correct view counts W track multiple views`(
        @IntForgery(min = 1, max = 5) repeatCount: Int,
        forge: Forge
    ) {
        // Given
        Rum.enable(fakeRumConfiguration, stubSdkCore)
        val rumMonitor = GlobalRumMonitor.get(stubSdkCore)

        repeat(repeatCount) {
            val viewKey = forge.anAlphaNumericalString()
            rumMonitor.startView(key = viewKey, name = forge.anAlphaNumericalString())
            rumMonitor.stopView(key = viewKey)
        }

        // When
        rumMonitor.stopSession()

        // Then
        assertThat(stubSdkCore.lastMetric())
            .hasViewCount(repeatCount)
    }

    companion object {
        private val mainLooper = MainLooperTestConfiguration()

        @TestConfigurationsProvider
        @JvmStatic
        fun getTestConfigurations(): List<TestConfiguration> {
            return listOf(mainLooper)
        }
    }
}
